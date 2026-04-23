// src/main/java/net/seep/odd/block/combiner/enchant/SentryPulseHandler.java
package net.seep.odd.block.combiner.enchant;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.particle.DustColorTransitionParticleEffect;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SentryPulseHandler {
    private SentryPulseHandler(){}

    private static boolean installed = false;

    // ticks since last pulse while blocking
    private static final Map<UUID, Integer> timer = new HashMap<>();

    // ~2 blocks radius
    private static final double RADIUS = 5.05;

    // cyan dust transition (cyan -> slightly bluish)
    private static final DustColorTransitionParticleEffect CYAN_PULSE =
            new DustColorTransitionParticleEffect(
                    new Vector3f(0.10f, 0.95f, 0.95f),
                    new Vector3f(0.10f, 0.55f, 1.00f),
                    0.95f
            );

    public static void init() {
        if (installed) return;
        installed = true;

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                tickPlayer(p);
            }
        });
    }

    private static void tickPlayer(ServerPlayerEntity p) {
        if (!p.isUsingItem()) {
            timer.remove(p.getUuid());
            return;
        }

        var active = p.getActiveItem();
        if (active == null || active.isEmpty()) {
            timer.remove(p.getUuid());
            return;
        }

        // Must be SENTRY enchant on the ACTIVE item (shield)
        int lvl = EnchantmentHelper.getLevel(CombinerEnchantments.SENTRY, active);
        if (lvl <= 0) {
            timer.remove(p.getUuid());
            return;
        }

        int t = timer.getOrDefault(p.getUuid(), 0) + 1;
        if (t < 20) {
            timer.put(p.getUuid(), t);
            return;
        }
        timer.put(p.getUuid(), 0);

        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        // sound each pulse
        sw.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.55f, 1.65f);

        // visual ring pulse (subtle)
        spawnPulse(sw, p);

        // knockback aura
        applyKnockback(sw, p, lvl);
    }

    private static void spawnPulse(ServerWorld sw, ServerPlayerEntity p) {
        Vec3d center = p.getPos().add(0, 0.9, 0);

        // 3 rings to imply “outward” without spam
        spawnRing(sw, center, 0.65, 10);
        spawnRing(sw, center, 1.25, 12);
        spawnRing(sw, center, 1.90, 14);
    }

    private static void spawnRing(ServerWorld sw, Vec3d center, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double ang = (i / (double) points) * Math.PI * 2.0;
            double x = center.x + Math.cos(ang) * radius;
            double z = center.z + Math.sin(ang) * radius;

            // subtle outward drift
            double vx = Math.cos(ang) * 0.03;
            double vz = Math.sin(ang) * 0.03;

            sw.spawnParticles(CYAN_PULSE, x, center.y, z, 1, vx, 0.01, vz, 0.0);
        }
    }

    private static void applyKnockback(ServerWorld sw, ServerPlayerEntity p, int lvl) {
        Vec3d origin = p.getPos().add(0, 0.4, 0);

        Box box = p.getBoundingBox().expand(RADIUS, 1.2, RADIUS);
        var targets = sw.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive()
                        && e != p
                        && !e.isSpectator()
                        && !e.isTeammate(p) // ✅ team-safe
        );

        if (targets.isEmpty()) return;

        // semi-strong, scaled by level + distance
        double maxKb = 1.85 + (lvl - 1) * 0.25; // lvl1=0.85, lvl2=1.10

        for (LivingEntity e : targets) {
            Vec3d to = e.getPos().subtract(origin);
            double dist = to.length();
            if (dist <= 0.0001 || dist > RADIUS) continue;

            double falloff = 1.0 - (dist / RADIUS); // closer => stronger
            double strength = maxKb * MathHelper.clamp(falloff, 0.0, 1.0);

            Vec3d dir = to.normalize();
            double yBoost = 0.06 + 0.10 * falloff;

            e.addVelocity(dir.x * strength, yBoost, dir.z * strength);
            e.velocityModified = true;
        }
    }
}