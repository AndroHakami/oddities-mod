package net.seep.odd.item.custom;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.seep.odd.particles.OddParticles;

import org.jetbrains.annotations.Nullable;

public final class StarSwordController {
    private static final int MAX_STARS = 5;
    private static final int CHARGE_TICKS = 80;   // 4 seconds
    private static final int DESCEND_TICKS = 14;  // quick sure-hit projectile fall
    private static final float DAMAGE_PER_STAR = 0.5f;

    private static final Map<UUID, MarkData> ACTIVE = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(StarSwordController::tickWorld);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE.clear());
    }

    public static boolean applyMark(ServerWorld world, LivingEntity target, @Nullable LivingEntity attacker) {
        if (!target.isAlive()) return false;

        MarkData data = ACTIVE.get(target.getUuid());
        if (data != null && !data.dimension.equals(world.getRegistryKey())) {
            ACTIVE.remove(target.getUuid());
            data = null;
        }

        if (data != null) {
            if (data.descending) {
                return false; // cannot add more stars once the big star is already coming down
            }

            data.stars = Math.min(MAX_STARS, data.stars + 1);
            data.ownerUuid = attacker == null ? null : attacker.getUuid();
            spawnApplyBurst(world, target, data.stars);
            return true;
        }

        ACTIVE.put(target.getUuid(), new MarkData(
                target.getUuid(),
                attacker == null ? null : attacker.getUuid(),
                world.getRegistryKey(),
                world.getTime(),
                1
        ));
        spawnApplyBurst(world, target, 1);
        return true;
    }

    private static void tickWorld(ServerWorld world) {
        if (ACTIVE.isEmpty()) return;

        Iterator<Map.Entry<UUID, MarkData>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            MarkData data = it.next().getValue();
            if (!data.dimension.equals(world.getRegistryKey())) {
                continue;
            }

            Entity entity = world.getEntity(data.targetUuid);
            if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
                it.remove();
                continue;
            }

            if (!data.descending) {
                spawnOrbitingStars(world, target, data.stars, world.getTime());

                if (world.getTime() - data.startTick >= CHARGE_TICKS) {
                    data.descending = true;
                    data.descendTick = 0;
                    world.playSound(
                            null,
                            target.getX(), target.getY(), target.getZ(),
                            SoundEvents.ENTITY_ENDER_EYE_LAUNCH,
                            SoundCategory.PLAYERS,
                            0.75f,
                            1.3f
                    );
                }
            } else {
                data.descendTick++;
                spawnFallingStar(world, target, data.stars, data.descendTick / (float) DESCEND_TICKS);

                if (data.descendTick >= DESCEND_TICKS) {
                    strike(world, target, data);
                    it.remove();
                }
            }
        }
    }

    private static void spawnApplyBurst(ServerWorld world, LivingEntity target, int stars) {
        double y = target.getY() + target.getHeight() + 0.15;
        int count = 6 + (stars * 2);

        world.spawnParticles(OddParticles.STAR_SIGIL, target.getX(), y, target.getZ(), count, 0.18, 0.10, 0.18, 0.015);
        world.playSound(
                null,
                target.getX(), target.getY(), target.getZ(),
                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                SoundCategory.PLAYERS,
                0.55f,
                1.55f - (stars * 0.08f)
        );
    }

    private static void spawnOrbitingStars(ServerWorld world, LivingEntity target, int stars, long time) {
        double baseY = target.getY() + target.getHeight() + 0.25;
        double radius = 0.34 + (stars * 0.02);
        double step = (Math.PI * 2.0) / stars;

        for (int i = 0; i < stars; i++) {
            double angle = (time * 0.12) + (i * step);
            double bob = Math.sin((time * 0.18) + (i * 1.35)) * 0.03;

            double x = target.getX() + (Math.cos(angle) * radius);
            double z = target.getZ() + (Math.sin(angle) * radius);
            double y = baseY + bob;

            // exactly one visible star per stack; the particle itself is now very short-lived
            // so it reads as a single orbiting star instead of a chained ribbon.
            world.spawnParticles(OddParticles.STAR_SIGIL, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void spawnFallingStar(ServerWorld world, LivingEntity target, int stars, float progress) {
        double impactY = target.getY() + target.getHeight() + 0.15;
        double startY = impactY + 4.75 + (stars * 0.35);
        double currentY = startY + ((impactY - startY) * progress);

        double cx = target.getX();
        double cz = target.getZ();
        double scale = 0.22 + (stars * 0.08);

        spawnStarCluster(world, cx, currentY, cz, scale);
        world.spawnParticles(OddParticles.STAR_SIGIL, cx, currentY, cz, 2, 0.03, 0.03, 0.03, 0.01);

        int trailCount = 2 + stars;
        for (int i = 0; i < trailCount; i++) {
            double t = i / (double) trailCount;
            double y = (currentY + 0.35) + ((startY - (currentY + 0.35)) * t);
            world.spawnParticles(OddParticles.STAR_SIGIL, cx, y, cz, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private static void spawnStarCluster(ServerWorld world, double cx, double cy, double cz, double scale) {
        world.spawnParticles(OddParticles.STAR_SIGIL, cx, cy, cz, 1, 0.0, 0.0, 0.0, 0.0);

        double[][] points = new double[][]{
                {0.0, scale},
                {scale, 0.0},
                {0.0, -scale},
                {-scale, 0.0},
                {scale * 0.65, scale * 0.65},
                {-scale * 0.65, scale * 0.65},
                {scale * 0.65, -scale * 0.65},
                {-scale * 0.65, -scale * 0.65}
        };

        for (double[] point : points) {
            world.spawnParticles(OddParticles.STAR_SIGIL, cx + point[0], cy, cz + point[1], 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void strike(ServerWorld world, LivingEntity target, MarkData data) {
        int stars = MathHelper.clamp(data.stars, 1, MAX_STARS);
        float damage = stars * DAMAGE_PER_STAR;
        double impactY = target.getY() + (target.getHeight() * 0.5);

        for (int i = 0; i < 4; i++) {
            double scale = 0.24 + (stars * 0.08) + (i * 0.08);
            spawnStarCluster(world, target.getX(), impactY + (i * 0.05), target.getZ(), scale);
        }

        world.spawnParticles(
                OddParticles.STAR_SIGIL,
                target.getX(), impactY, target.getZ(),
                12 + (stars * 6),
                0.25 + (stars * 0.05), 0.20, 0.25 + (stars * 0.05),
                0.03
        );

        target.damage(target.getDamageSources().magic(), damage);

        world.playSound(
                null,
                target.getX(), target.getY(), target.getZ(),
                SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST,
                SoundCategory.PLAYERS,
                0.95f,
                0.85f
        );
    }

    private StarSwordController() {}

    private static final class MarkData {
        private final UUID targetUuid;
        private @Nullable UUID ownerUuid;
        private final RegistryKey<World> dimension;
        private final long startTick;

        private int stars;
        private boolean descending;
        private int descendTick;

        private MarkData(UUID targetUuid, @Nullable UUID ownerUuid, RegistryKey<World> dimension, long startTick, int stars) {
            this.targetUuid = targetUuid;
            this.ownerUuid = ownerUuid;
            this.dimension = dimension;
            this.startTick = startTick;
            this.stars = stars;
            this.descending = false;
            this.descendTick = 0;
        }
    }
}
