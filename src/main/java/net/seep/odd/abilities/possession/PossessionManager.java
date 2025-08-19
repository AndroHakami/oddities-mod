package net.seep.odd.abilities.possession;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PossessionManager {
    public static final PossessionManager INSTANCE = new PossessionManager();
    private PossessionManager(){}

    public static class Session {
        public final UUID playerId;
        public final LivingEntity target;
        public int remaining; // ticks
        public net.seep.odd.abilities.net.PossessionControlPacket.State last =
                new net.seep.odd.abilities.net.PossessionControlPacket.State(false,false,false,false,false,false,0,0,false);
        public final Vec3d anchorPos;
        public final RegistryKey<World> anchorDim;
        public final float anchorYaw, anchorPitch;
        public net.minecraft.world.GameMode prevMode; // reserved

        public Session(UUID id, LivingEntity t, int ticks, net.minecraft.world.GameMode prev,
                       Vec3d anchorPos, RegistryKey<World> anchorDim, float anchorYaw, float anchorPitch) {
            playerId = id; target = t; remaining = ticks; prevMode = prev;
            this.anchorPos = anchorPos; this.anchorDim = anchorDim;
            this.anchorYaw = anchorYaw; this.anchorPitch = anchorPitch;
        }
    }

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public void start(ServerPlayerEntity player, LivingEntity target, int durationTicks) {
        end(player, false);
        var prev = player.interactionManager.getGameMode();

        // anchor the real body
        Vec3d anchor = player.getPos();
        RegistryKey<World> dim = player.getWorld().getRegistryKey();
        float ay = player.getYaw();
        float ap = player.getPitch();

        // DO NOT change gamemode or server camera here (client will set camera)

        // freeze & hide the body
        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.INVISIBILITY, durationTicks + 40, 0, false, false));
        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.SLOWNESS, durationTicks + 40, 255, false, false));
        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.MINING_FATIGUE, durationTicks + 40, 255, false, false));
        player.setInvulnerable(true);

        if (target instanceof MobEntity m) m.setAiDisabled(true);

        sessions.put(player.getUuid(), new Session(player.getUuid(), target, durationTicks, prev, anchor, dim, ay, ap));

        // tell the client to start + which entity to camera-follow
        var buf = PacketByteBufs.create();
        buf.writeVarInt(target.getId());
        ServerPlayNetworking.send(player, new net.minecraft.util.Identifier("odd","possess_start"), buf);
    }

    public void end(ServerPlayerEntity player, boolean restoreCamera) {
        var s = sessions.remove(player.getUuid());
        if (s != null && s.target instanceof MobEntity m && s.target.isAlive()) {
            m.setAiDisabled(false);
        }
        // DO NOT set camera here; the client restores it

        // unfreeze/unhide body
        player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS);
        player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.MINING_FATIGUE);
        player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.INVISIBILITY);
        player.setInvulnerable(false);

        ServerPlayNetworking.send(player, new net.minecraft.util.Identifier("odd","possess_stop"),
                PacketByteBufs.empty());
    }

    public boolean isPossessing(ServerPlayerEntity p){ return sessions.containsKey(p.getUuid()); }

    public void registerTicker() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var it = sessions.entrySet().iterator(); it.hasNext();) {
                var entry = it.next();
                var s = entry.getValue();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(s.playerId);
                if (player == null) { it.remove(); continue; }
                var world = (ServerWorld) player.getWorld();

                // end conditions
                if (s.target == null || !s.target.isAlive() || s.target.isRemoved() || s.remaining <= 0) {
                    end(player, true);
                    it.remove();
                    continue;
                }

                // keep body anchored
                if (player.getWorld().getRegistryKey() == s.anchorDim) {
                    if (player.squaredDistanceTo(s.anchorPos) > 0.0004) {
                        player.teleport((ServerWorld) player.getWorld(),
                                s.anchorPos.x, s.anchorPos.y, s.anchorPos.z, s.anchorYaw, s.anchorPitch);
                    } else {
                        player.setYaw(s.anchorYaw);
                        player.setPitch(s.anchorPitch);
                    }
                    player.setVelocity(Vec3d.ZERO);
                    player.velocityModified = true;
                }

                // drive the possessed entity
                applyControl(s, world);
                s.remaining--;
            }
        });
    }

    private void applyControl(Session s, ServerWorld world) {
        LivingEntity e = s.target;
        var in = s.last;

        e.setYaw(in.yaw());
        e.setPitch(in.pitch());

        Vec3d forward = yawToVec(in.yaw(), 0);
        Vec3d strafe  = new Vec3d(-forward.z, 0, forward.x);

        double base = e.isOnGround() ? 0.28 : 0.18;
        if (in.sprint()) base *= 1.5;

        Vec3d move = Vec3d.ZERO;
        if (in.f()) move = move.add(forward.multiply(base));
        if (in.b()) move = move.subtract(forward.multiply(base));
        if (in.l()) move = move.add(strafe.multiply(base));
        if (in.r()) move = move.subtract(strafe.multiply(base));

        double vy = (in.jump() && e.isOnGround()) ? 0.42 : 0.0;
        e.move(MovementType.SELF, new Vec3d(move.x, vy, move.z));
        e.velocityModified = true;

        if (in.actionPressed()) triggerAction(e, world, s);
    }

    private void triggerAction(LivingEntity e, ServerWorld world, Session s) {
        EntityType<?> t = e.getType();

        if (t == EntityType.GHAST) {
            var look = e.getRotationVec(1f);
            var fb = new FireballEntity(world, e, look.x, look.y, look.z, 1);
            fb.setPos(e.getX() + look.x * 2.0, e.getEyeY(), e.getZ() + look.z * 2.0);
            world.spawnEntity(fb);
            world.playSound(null, e.getBlockPos(), SoundEvents.ENTITY_GHAST_SHOOT, SoundCategory.PLAYERS, 1f, 1f);

        } else if (t == EntityType.SKELETON) {
            var look = e.getRotationVec(1f);
            var arrow = new ArrowEntity(world, e);
            arrow.setPos(e.getX(), e.getEyeY() - 0.1, e.getZ());
            arrow.setVelocity(look.x, look.y, look.z, 2.2f, 1.0f);
            world.spawnEntity(arrow);
            world.playSound(null, e.getBlockPos(), SoundEvents.ENTITY_SKELETON_SHOOT, SoundCategory.PLAYERS, 1f, 1f);

        } else if (t == EntityType.CREEPER) {
            // block-safe "fake" explosion
            double radius = 3.5;
            world.playSound(null, e.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1f, 1f);
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, e.getX(), e.getBodyY(0.5), e.getZ(), 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.EXPLOSION, e.getX(), e.getBodyY(0.5), e.getZ(), 20, 0.6, 0.4, 0.6, 0.1);

            Box box = new Box(e.getBlockPos()).expand(radius);
            for (LivingEntity le : world.getEntitiesByClass(LivingEntity.class, box, ent -> ent != e)) {
                double dist = le.squaredDistanceTo(e);
                double falloff = Math.max(0, 1.0 - Math.sqrt(dist) / radius);
                if (falloff <= 0) continue;

                le.damage(world.getDamageSources().mobAttack(e), (float)(8.0 * falloff));
                Vec3d kb = le.getPos().subtract(e.getPos()).normalize()
                        .multiply(0.9 * falloff).add(0, 0.35 * falloff, 0);
                le.addVelocity(kb.x, kb.y, kb.z);
                le.velocityModified = true;
            }
            s.remaining = 0; // end possession

        } else {
            // default: short-range swipe
            double reach = 2.5;
            Vec3d from = e.getCameraPosVec(1f);
            Vec3d to = from.add(e.getRotationVec(1f).multiply(reach));
            EntityHitResult hit = ProjectileUtil.raycast(
                    e, from, to, new Box(from, to).expand(1),
                    ent -> ent instanceof LivingEntity && ent != e,
                    reach * reach);
            if (hit != null && hit.getType() == HitResult.Type.ENTITY) {
                var target = (LivingEntity) hit.getEntity();
                target.damage(world.getDamageSources().mobAttack(e), 4.0f);
                world.playSound(null, target.getBlockPos(),
                        SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8f, 1.2f);
            }
        }
    }

    private static Vec3d yawToVec(float yawDeg, float pitchDeg) {
        float yaw = (float) Math.toRadians(-yawDeg) - (float) Math.PI;
        float x = (float) Math.sin(yaw);
        float z = (float) Math.cos(yaw);
        return new Vec3d(x, 0, z);
    }

    public static int durationFor(LivingEntity e) {
        var type = e.getType();
        if (type == EntityType.GHAST)    return 20 * 12;
        if (type == EntityType.CREEPER)  return 20 * 6;
        if (type == EntityType.SKELETON) return 20 * 10;
        if (e.isUndead())                return 20 * 9;
        return 20 * 8;
    }

    public Session getSession(ServerPlayerEntity player) {
        return sessions.get(player.getUuid());
    }
}
