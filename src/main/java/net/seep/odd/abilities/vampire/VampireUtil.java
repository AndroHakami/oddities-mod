// src/main/java/net/seep/odd/abilities/vampire/VampireUtil.java
package net.seep.odd.abilities.vampire;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.LightType;
import net.seep.odd.abilities.PowerAPI;

import org.joml.Vector3f;

public final class VampireUtil {
    private VampireUtil() {}

    // A bit brighter + bigger so you can actually SEE it
    private static final DustParticleEffect SENSE =
            new DustParticleEffect(new Vector3f(0.85f, 0.08f, 0.12f), 1.10f);

    public static boolean isVampire(PlayerEntity p) {
        if (p == null) return false;

        // IMPORTANT: this avoids your previous client->server cast crash
        if (p.getWorld().isClient) {
            return VampireClientState.isClientVampire();
        }

        if (p instanceof ServerPlayerEntity sp) {
            return "vampire".equals(PowerAPI.get(sp));
        }
        return false;
    }

    /** “Direct sunlight” check (Overworld day + skylight at head). */
    public static boolean isInDirectSunlight(PlayerEntity p) {
        World w = p.getWorld();
        if (w == null) return false;
        if (!w.getDimension().hasSkyLight()) return false;
        if (w.isClient) return false;

        if (!w.isDay()) return false;
        if (w.getRegistryKey() != World.OVERWORLD) return false;

        BlockPos head = BlockPos.ofFloored(p.getX(), p.getEyeY(), p.getZ());
        if (!w.isSkyVisible(head)) return false;

        int sky = w.getLightLevel(LightType.SKY, head);
        return sky >= 14;
    }

    /**
     * Spawn “sense trails” pointing toward nearby entities; ONLY the viewer sees them.
     * This version is deliberately more visible + starts close to the camera so it never feels “broken”.
     */
    public static void spawnSenseTrails(ServerWorld sw, ServerPlayerEntity viewer, double range, int maxTargets) {
        if (viewer == null || maxTargets <= 0) return;

        double r = Math.max(6.0, Math.min(96.0, range)); // clamp to sane values
        Vec3d eye = viewer.getEyePos();
        Vec3d look = viewer.getRotationVector().normalize();

        var box = viewer.getBoundingBox().expand(r, r * 0.6, r);

        LivingEntity[] best = new LivingEntity[maxTargets];
        double[] bestD2 = new double[maxTargets];
        for (int i = 0; i < maxTargets; i++) bestD2[i] = Double.POSITIVE_INFINITY;

        // Prefer targets roughly in FRONT of the player so the trails are actually visible.
        final double minDot = 0.15; // ~81 degrees cone

        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, box, le ->
                le.isAlive() && le != viewer && !le.isSpectator())) {

            Vec3d to = e.getPos().add(0, e.getHeight() * 0.6, 0).subtract(eye);
            double d2 = to.lengthSquared();
            if (d2 > r * r) continue;

            double len = Math.sqrt(d2);
            if (len < 2.0) continue;

            Vec3d n = to.multiply(1.0 / len);
            double dot = n.dotProduct(look);
            if (dot < minDot) continue;

            int worst = 0;
            for (int i = 1; i < maxTargets; i++) {
                if (bestD2[i] > bestD2[worst]) worst = i;
            }
            if (d2 < bestD2[worst]) {
                bestD2[worst] = d2;
                best[worst] = e;
            }
        }

        boolean any = false;

        for (int i = 0; i < maxTargets; i++) {
            LivingEntity e = best[i];
            if (e == null) continue;

            any = true;

            Vec3d tgt = e.getPos().add(0, e.getHeight() * 0.6, 0);
            Vec3d dir = tgt.subtract(eye);
            double len = dir.length();
            if (len < 2.0) continue;

            Vec3d n = dir.multiply(1.0 / len);

            // Show only the first part of the ray so it doesn’t clutter
            double showLen = Math.min(12.0, len);

            // Slight corkscrew so it reads better in motion
            Vec3d up = new Vec3d(0, 1, 0);
            Vec3d side = n.crossProduct(up);
            if (side.lengthSquared() < 1.0E-4) side = new Vec3d(1, 0, 0);
            side = side.normalize();

            int steps = 8;
            double startDist = 1.0; // ✅ start close to camera so it’s obvious
            double step = (showLen - startDist) / (steps - 1);

            double time = (viewer.age + sw.getTime()) * 0.25;

            for (int k = 0; k < steps; k++) {
                double t = startDist + step * k;

                double wave = Math.sin(time + k * 0.9) * 0.10;     // sideways wiggle
                double bob  = Math.cos(time * 1.15 + k * 0.7) * 0.05; // small vertical bob

                Vec3d pt = eye.add(n.multiply(t))
                        .add(side.multiply(wave))
                        .add(0.0, bob, 0.0);

                // 2 particles per point = noticeably thicker but still light
                sw.spawnParticles(
                        viewer, SENSE, true,
                        pt.x, pt.y, pt.z,
                        2,
                        0.015, 0.015, 0.015,
                        0.0
                );
            }
        }

        // ✅ If no targets, still show a tiny “ping” around you so it never feels dead.
        if (!any) {
            for (int k = 0; k < 6; k++) {
                double a = (Math.PI * 2.0) * (k / 6.0);
                Vec3d pt = eye.add(Math.cos(a) * 0.6, -0.15, Math.sin(a) * 0.6);
                sw.spawnParticles(viewer, SENSE, true, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0.0);
            }
        }
    }
}
