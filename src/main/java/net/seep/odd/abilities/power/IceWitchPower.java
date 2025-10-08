// FILE: net/seep.odd/abilities/power/IceWitchPower.java
package net.seep.odd.abilities.power;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.seep.odd.abilities.icewitch.IceProjectileEntity;
import net.seep.odd.abilities.icewitch.IceSpellAreaEntity;
import net.seep.odd.abilities.icewitch.IceWitchPackets;
import net.seep.odd.abilities.icewitch.client.CpmIceWitchHooks;
import net.seep.odd.abilities.overdrive.client.CpmHooks;
import net.seep.odd.abilities.tamer.projectile.EmeraldShurikenEntity;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.particles.OddParticles;
import net.seep.odd.sound.ModSounds;

import java.util.*;

/** Ice Witch power: Elytra-style soar (no elytra) + ice projectile that plants an AoE sigil on impact. */
public final class IceWitchPower implements Power {
    public static final float MAX_MANA = 100f;

    @Override public String id() { return "ice_witch"; }
    @Override public boolean hasSlot(String slot) { return "primary".equals(slot) || "secondary".equals(slot); }
    @Override public long cooldownTicks() { return 0; }
    @Override public long secondaryCooldownTicks() { return 2 * 20; }
    @Override public Identifier iconTexture(String slot) {
        return new Identifier("odd", "textures/gui/abilities/ice_witch_" + slot + ".png");
    }

    @Override public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary"   -> "Hold Jump in mid-air for true Elytra flight (no elytra). Propelled where you look. Consumes mana and auto-stops at 0.";
            case "secondary" -> "Fire an ice bolt; on impact it plants a chilling sigil that slows and freezes foes.";
            default          -> "Ice Witch";
        };
    }
    @Override public String longDescription() {
        return "Attacks freeze; you have a mana bar. Soar with Elytra physics without an elytra, and fire an ice bolt that plants a damaging frost circle on impact.";
    }

    /* ===== flight tuning (augment vanilla elytra while active) ===== */
    private static final float  SOAR_MANA_DRAIN = 0.80f;  // per tick while flying
    private static final double PROP_ACCEL      = 0.11;   // push toward look dir per tick (kept from your file)
    private static final double HORIZ_MAX_SPD   = 1.05;   // cap horizontal speed
    private static final double LIFT_PER_TICK   = 0.010;  // soft upward bias each tick
    private static final double SINK_FLOOR      = -0.10;  // don’t sink faster than this while boosting

    /* ===== projectile -> sigil tracking ===== */
    private static final class Shot {
        final UUID projId;
        final UUID ownerId;
        Vec3d lastPos;
        int ttl;
        Shot(UUID projId, UUID ownerId, Vec3d start, int ttl) {
            this.projId = projId; this.ownerId = ownerId; this.lastPos = start; this.ttl = ttl;
        }
    }
    private static final Map<UUID, Shot> SHOTS = new HashMap<>();
    private static int LAST_PROJECTILE_TICK = -1;

    /* ===== per-player state ===== */
    private static final class State {
        float mana = MAX_MANA;
        boolean soarEnabled = true;  // primary toggles this
        // input
        float ix = 0, iz = 0;
        boolean jumpHeld = false;
        // notifications / hud sync
        boolean outOfManaNotified = false;
        int syncTimer = 0;
    }
    private static final Map<UUID, State> S = new HashMap<>();
    private static State st(ServerPlayerEntity p){ return S.computeIfAbsent(p.getUuid(), u->new State()); }

    /* ===== packets -> state ===== */
    public static void onClientInput(ServerPlayerEntity p, int mask, float strafe, float forward) {
        State s = st(p);
        s.jumpHeld = (mask & 1) != 0;
        float len = (float)Math.sqrt(strafe*strafe + forward*forward);
        if (len > 1f) { strafe /= len; forward /= len; }
        s.ix = strafe;
        s.iz = forward;
    }

    /* ===== melee freeze helper ===== */
    public static void onMeleeFreeze(ServerPlayerEntity attacker, LivingEntity victim) {
        if (!(attacker.getWorld() instanceof ServerWorld sw)) return;
        victim.setFrozenTicks(Math.min(500, victim.getFrozenTicks() + 40));
        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1, true, true, true), attacker);
        victim.extinguish();
        sw.spawnParticles(OddParticles.ICE_FLAKE, victim.getX(), victim.getY() + victim.getStandingEyeHeight()*0.6, victim.getZ(), 6, 0.2,0.2,0.2, 0.01);
    }

    /* ===== inputs ===== */
    @Override
    public void activate(ServerPlayerEntity player) {
        State s = st(player);
        s.soarEnabled = !s.soarEnabled;
        s.outOfManaNotified = false; // reset notifier when user toggles
        player.sendMessage(Text.literal(s.soarEnabled ? "Soar: ON" : "Soar: OFF"), true);
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        State s = st(player);
        if (s.mana < 25f) return;

        // pay cost + CPM gesture
        s.mana -= 25f;
        CpmHooks.play("cast_spell");

        // **make it look like a throw**
        player.swingHand(Hand.MAIN_HAND, true);
        ((ServerWorld)player.getWorld()).playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                ModSounds.ICE_SPELL,
                SoundCategory.PLAYERS,
                0.9f,
                0.9f + player.getWorld().random.nextFloat() * 0.2f
        );

        // spawn a visible ice bolt (snowball) and track it for impact -> sigil
        ServerWorld sw = (ServerWorld) player.getWorld();
        IceProjectileEntity proj = new IceProjectileEntity(sw, player);
        Vec3d look = player.getRotationVec(1.0f).normalize();
        proj.setVelocity(look.x, look.y, look.z, 1.9f, 0.0f);
        proj.setNoGravity(false);
        sw.spawnEntity(proj);

        SHOTS.put(proj.getUuid(), new Shot(proj.getUuid(), player.getUuid(), proj.getPos(), 80)); // ~4s TTL
    }

    /* ===== per-tick ===== */
    public static void serverTick(ServerPlayerEntity p) {
        State s = st(p);
        ServerWorld sw = (ServerWorld)p.getWorld();

        // process all tracked projectiles ONCE per tick for the world
        int now = (int) sw.getTime();
        if (LAST_PROJECTILE_TICK != now) {
            processTrackedProjectiles(sw);
            LAST_PROJECTILE_TICK = now;
        }

        // passive mana regen
        s.mana = Math.min(MAX_MANA, s.mana + 0.30f);
        if (s.mana > 5f) s.outOfManaNotified = false; // rearm notice once you substantially regen

        // === Real Elytra flight, augmented ===
        boolean canSoar =
                s.soarEnabled && s.jumpHeld && s.mana > 0f &&
                        !p.isOnGround() && !p.isClimbing() && !p.isTouchingWater() && !p.isInLava() &&
                        !p.hasVehicle();

        if (canSoar) {
            if (!p.isFallFlying()) p.startFallFlying(); else p.startFallFlying();

            Vec3d look = p.getRotationVec(1.0f).normalize();
            Vec3d v = p.getVelocity();

            // propulsion toward look
            Vec3d v2 = v.add(look.multiply(PROP_ACCEL));

            // clamp horizontal
            Vec3d horiz = new Vec3d(v2.x, 0, v2.z);
            double hlen = horiz.length();
            if (hlen > HORIZ_MAX_SPD) {
                horiz = horiz.normalize().multiply(HORIZ_MAX_SPD);
                v2 = new Vec3d(horiz.x, v2.y, horiz.z);
            }

            // softer gravity + slight lift bias (more if looking up)
            double vy = v2.y + LIFT_PER_TICK * (1.0 + Math.max(0.0, look.y));
            if (vy < SINK_FLOOR) vy = SINK_FLOOR;
            v2 = new Vec3d(v2.x, vy, v2.z);

            p.setVelocity(v2);
            p.velocityModified = true;
            p.fallDistance = 0f;

            // trail + drain
            if ((p.age % 2) == 0) sw.spawnParticles(OddParticles.ICE_FLAKE, p.getX(), p.getY(), p.getZ(), 2, 0.1,0.1,0.1, 0.0);
            s.mana = Math.max(0f, s.mana - SOAR_MANA_DRAIN);

            // out-of-mana cutoff: stop flight + auto toggle OFF (once)
            if (s.mana <= 0f) {
                try { p.stopFallFlying(); } catch (Throwable ignore) {}
                if (s.soarEnabled) s.soarEnabled = false;
                if (!s.outOfManaNotified) {
                    s.outOfManaNotified = true;
                    p.sendMessage(Text.literal("Soar: OFF (out of mana)"), true);
                }
            }
        } else {
            if (p.isFallFlying()) {
                try { p.stopFallFlying(); } catch (Throwable ignore) {}
            }
        }

        // HUD sync (throttle)
        if (++s.syncTimer >= 5) {
            s.syncTimer = 0;
            IceWitchPackets.syncManaToClient(p, s.mana, MAX_MANA);
        }
    }

    /* ===== projectile processing ===== */
    private static void processTrackedProjectiles(ServerWorld sw) {
        Iterator<Shot> it = SHOTS.values().iterator();
        while (it.hasNext()) {
            Shot sh = it.next();
            var e = sw.getEntity(sh.projId);

            if (e != null && e.isAlive()) {
                Vec3d cur = e.getPos();

                // raycast between last and current to detect block hit on this tick
                if (sh.lastPos != null && !sh.lastPos.equals(cur)) {
                    RaycastContext ctx = new RaycastContext(
                            sh.lastPos, cur,
                            RaycastContext.ShapeType.COLLIDER,
                            RaycastContext.FluidHandling.NONE,
                            e
                    );
                    var hit = sw.raycast(ctx);
                    if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                        placeSigil(sw, sh.ownerId, (BlockHitResult) hit);
                        e.discard();
                        it.remove();
                        continue;
                    }
                }

                // keep tracking + TTL fallback
                sh.lastPos = cur;
                if (--sh.ttl <= 0) {
                    placeSigilAt(sw, sh.ownerId, cur);
                    e.discard();
                    it.remove();
                }
            } else {
                // projectile died this tick (likely impact we missed): drop at last known spot
                if (sh.lastPos != null) placeSigilAt(sw, sh.ownerId, sh.lastPos);
                it.remove();
            }
        }
    }

    private static void placeSigil(ServerWorld sw, UUID owner, BlockHitResult bhr) {
        BlockPos bp = bhr.getBlockPos();
        Direction face = bhr.getSide();
        double x = bp.getX() + 0.5;
        double z = bp.getZ() + 0.5;
        double y = (face == Direction.DOWN) ? bp.getY() - 0.01 : bp.getY() + 1.01;
        spawnSigil(sw, owner, x, y, z);
    }

    private static void placeSigilAt(ServerWorld sw, UUID owner, Vec3d pos) {
        // try to snap to ground below the last position
        Vec3d start = pos.add(0, 1.5, 0);
        Vec3d end   = pos.add(0, -6.0, 0);
        var hit = sw.raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                null
        ));
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            placeSigil(sw, owner, (BlockHitResult) hit);
        } else {
            spawnSigil(sw, owner, pos.x, pos.y, pos.z);
        }
    }

    private static void spawnSigil(ServerWorld sw, UUID owner, double x, double y, double z) {
        if (ModEntities.ICE_SPELL_AREA == null) return;
        IceSpellAreaEntity spell = ModEntities.ICE_SPELL_AREA.create(sw);
        if (spell == null) return;
        spell.setRadius(3.25f);
        spell.setLifetimeTicks(200);
        spell.setOwner(owner);
        spell.refreshPositionAndAngles(x, y, z, 0, 0);
        sw.spawnEntity(spell);
    }
}
