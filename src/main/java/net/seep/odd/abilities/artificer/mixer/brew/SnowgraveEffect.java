// FILE: src/main/java/net/seep/odd/abilities/artificer/mixer/brew/SnowgraveEffect.java
package net.seep.odd.abilities.artificer.mixer.brew;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class SnowgraveEffect {
    private SnowgraveEffect() {}

    private static boolean inited = false;

    public static final int CHARGE_TICKS = 20 * 2;       // 2s charge
    public static final int FREEZE_TICKS = 20 * 5;       // 5s frozen
    public static final float RADIUS = 5.0f;             // similar to your healing aurora
    public static final float SPREAD_RADIUS = 3.0f;      // on kill
    public static final int ICE_SHELL_COUNT = 8;         // a bit denser = “full model” look

    private record Zone(ServerWorld world, Vec3d center, long endTick) {}
    private static final Object2ObjectOpenHashMap<Long, Zone> ZONES = new Object2ObjectOpenHashMap<>();

    // ✅ store the spawned ice display entity IDs so we can ALWAYS discard them
    private record Frozen(ServerWorld world, UUID uuid, long endTick, Vec3d lastPos, int[] iceIds) {}
    private static final Object2ObjectOpenHashMap<UUID, Frozen> FROZEN = new Object2ObjectOpenHashMap<>();

    private static void initCommon() {
        if (inited) return;
        inited = true;
        ServerTickEvents.START_SERVER_TICK.register(SnowgraveEffect::tick);
    }

    public static void apply(World world, BlockPos pos, @Nullable LivingEntity thrower, ItemStack stack) {
        if (!(world instanceof ServerWorld sw)) return;
        initCommon();

        Vec3d center = Vec3d.ofCenter(pos);

        long id = sw.getRandom().nextLong();
        long end = sw.getServer().getTicks() + CHARGE_TICKS;

        ZONES.put(id, new Zone(sw, center, end));

        // charge aurora on clients
        net.seep.odd.abilities.artificer.mixer.SnowgraveNet.sendZone(sw, id, center, RADIUS, CHARGE_TICKS);

        sw.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.9f, 0.55f);
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTicks();

        // finish charged zones -> freeze
        var zit = ZONES.object2ObjectEntrySet().fastIterator();
        while (zit.hasNext()) {
            var e = zit.next();
            Zone z = e.getValue();
            if (now < z.endTick) continue;

            Box aabb = new Box(z.center, z.center).expand(RADIUS, 2.5, RADIUS);
            for (LivingEntity t : z.world.getEntitiesByClass(LivingEntity.class, aabb, ent -> ent.isAlive())) {
                freezeTarget(z.world, t, now);
            }

            z.world.playSound(null, BlockPos.ofFloored(z.center), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1.0f, 0.65f);
            z.world.spawnParticles(net.minecraft.particle.ParticleTypes.SNOWFLAKE, z.center.x, z.center.y + 0.6, z.center.z,
                    90, 1.2, 1.0, 1.2, 0.02);

            zit.remove();
        }

        // maintain frozen targets: immobilize, detect death -> explode + spread
        var fit = FROZEN.object2ObjectEntrySet().fastIterator();
        while (fit.hasNext()) {
            var e = fit.next();
            Frozen f = e.getValue();

            Entity raw = f.world.getEntity(f.uuid);
            LivingEntity ent = (raw instanceof LivingEntity le) ? le : null;

            // expired
            if (now >= f.endTick) {
                cleanupIce(f.world, f.iceIds);
                fit.remove();
                continue;
            }

            // missing or dead = treat as death while frozen
            if (ent == null || !ent.isAlive()) {
                Vec3d p = f.lastPos;

                cleanupIce(f.world, f.iceIds);

                // explosion + spread
                f.world.spawnParticles(net.minecraft.particle.ParticleTypes.ITEM_SNOWBALL, p.x, p.y + 0.8, p.z,
                        140, 0.9, 0.7, 0.9, 0.08);
                f.world.spawnParticles(net.minecraft.particle.ParticleTypes.SNOWFLAKE, p.x, p.y + 0.8, p.z,
                        160, 1.1, 0.9, 1.1, 0.03);

                f.world.playSound(null, BlockPos.ofFloored(p), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1.1f, 0.9f);

                Box box = new Box(p, p).expand(SPREAD_RADIUS, 2.5, SPREAD_RADIUS);
                for (LivingEntity t : f.world.getEntitiesByClass(LivingEntity.class, box, x -> x.isAlive())) {
                    freezeTarget(f.world, t, now);
                }

                fit.remove();
                continue;
            }

            // still alive: clamp movement hard
            ent.setFrozenTicks(Math.max(ent.getFrozenTicks(), 160));
            ent.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 10, false, true, true));
            ent.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 10, 2, false, true, true));
            ent.setVelocity(0, Math.min(0, ent.getVelocity().y), 0);
            ent.velocityModified = true;
            ent.fallDistance = 0.0f;

            // keep last pos updated for death explosion
            FROZEN.put(ent.getUuid(), new Frozen(f.world, ent.getUuid(), f.endTick, ent.getPos(), f.iceIds));
        }
    }

    private static void freezeTarget(ServerWorld sw, LivingEntity t, long now) {
        // refresh existing: CLEANUP old ice first
        Frozen prev = FROZEN.get(t.getUuid());
        if (prev != null) cleanupIce(sw, prev.iceIds);

        long end = now + FREEZE_TICKS;

        int[] iceIds = new int[ICE_SHELL_COUNT];
        for (int i = 0; i < ICE_SHELL_COUNT; i++) iceIds[i] = -1;

        // “ice casing” using block displays
        for (int i = 0; i < ICE_SHELL_COUNT; i++) {
            DisplayEntity.BlockDisplayEntity disp = EntityType.BLOCK_DISPLAY.create(sw);
            if (disp == null) continue;

            ((net.seep.odd.mixin.BlockDisplayEntityAccessor)(Object) disp).odd$setBlockState(Blocks.ICE.getDefaultState());
            disp.setNoGravity(true);
            disp.setInvulnerable(true);

            // spread around model (covers “entire model” much better)
            double ox = (sw.getRandom().nextDouble() - 0.5) * 1.2;
            double oz = (sw.getRandom().nextDouble() - 0.5) * 1.2;
            double oy = 0.05 + sw.getRandom().nextDouble() * Math.max(0.2, t.getHeight());

            disp.refreshPositionAndAngles(t.getX() + ox, t.getY() + oy, t.getZ() + oz,
                    sw.getRandom().nextFloat() * 360f, 0f);

            disp.addCommandTag("odd_snowgrave_ice");
            sw.spawnEntity(disp);

            iceIds[i] = disp.getId();
        }

        FROZEN.put(t.getUuid(), new Frozen(sw, t.getUuid(), end, t.getPos(), iceIds));
    }

    private static void cleanupIce(ServerWorld sw, int[] ids) {
        if (ids == null) return;
        for (int id : ids) {
            if (id < 0) continue;
            Entity e = sw.getEntityById(id);
            if (e != null) e.discard();
        }
    }
}
