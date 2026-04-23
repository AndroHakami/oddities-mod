package net.seep.odd.sky.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.event.alien.client.AlienInvasionClientState;
import net.seep.odd.sky.CelestialEventClient;
import net.seep.odd.sky.day.BiomeDayProfileClientStore;

public final class OverworldDreamClouds {
    private OverworldDreamClouds() {}

    public static Vec3d apply(ClientWorld world, Vec3d original, float tickDelta) {
        if (world == null || original == null) return original;
        if (!world.getRegistryKey().equals(World.OVERWORLD)) return original;
        if (CelestialEventClient.hideClouds()) return new Vec3d(0.0, 0.0, 0.0);

        Vec3d out = original;

        float dayAmount = BiomeDayProfileClientStore.getDayAmount(world, tickDelta);
        if (!AlienInvasionClientState.active() && dayAmount > 0.001f) {
            Vec3d cameraPos = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
            BiomeDayProfileClientStore.DayBiomeBlend blend = BiomeDayProfileClientStore.sample(world, cameraPos);

            if (blend.weight() > 0.001f) {
                double w = blend.weight() * dayAmount;

                Vec3d target = blend.cloud();
                Vec3d lifted = new Vec3d(
                        out.x + (1.0 - out.x) * 0.08 * w,
                        out.y + (1.0 - out.y) * 0.08 * w,
                        out.z + (1.0 - out.z) * 0.08 * w
                );

                out = mix(lifted, target, 0.42 * w);
            }
        }

        float nightAmount = OverworldDreamSkyRenderer.getNightAmount(world, tickDelta);
        if (nightAmount > 0.001f) {
            double pulse = 0.5 + 0.5 * Math.sin((world.getTime() + tickDelta) * 0.0024);
            Vec3d blue = new Vec3d(0.47, 0.54, 0.84);
            Vec3d purple = new Vec3d(0.58, 0.55, 0.87);
            Vec3d target = mix(blue, purple, 0.35 + 0.20 * pulse);

            double rain = world.getRainGradient(tickDelta);
            double thunder = world.getThunderGradient(tickDelta);
            double weather = clamp01(1.0 - rain * 0.45 - thunder * 0.35);

            double lift = 0.10 * nightAmount * weather;
            Vec3d lifted = new Vec3d(
                    out.x + (1.0 - out.x) * lift,
                    out.y + (1.0 - out.y) * lift,
                    out.z + (1.0 - out.z) * lift
            );

            double tintStrength = 0.16 * nightAmount * weather;
            out = mix(lifted, target, tintStrength);
            out = out.add(target.multiply(0.04 * nightAmount * weather));
        }

        if (AlienInvasionClientState.active()) {
            double prog = clamp01(AlienInvasionClientState.skyProgress01(tickDelta));
            Vec3d alien = new Vec3d(0.20, 0.95, 0.45);
            out = mix(out, alien, 0.35 * prog);
            out = out.add(alien.multiply(0.05 * prog));
        }

        out = CelestialEventClient.applySkyHue(out);
        return clampVec(out);
    }

    private static Vec3d mix(Vec3d a, Vec3d b, double t) {
        t = clamp01(t);
        return new Vec3d(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t
        );
    }

    private static Vec3d clampVec(Vec3d v) {
        return new Vec3d(
                clamp01(v.x),
                clamp01(v.y),
                clamp01(v.z)
        );
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
