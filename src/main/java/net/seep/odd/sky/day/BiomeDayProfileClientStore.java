package net.seep.odd.sky.day;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class BiomeDayProfileClientStore {
    private BiomeDayProfileClientStore() {}

    private static final Map<Identifier, BiomeDayProfile> PROFILES = new HashMap<>();

    public static void replaceAll(Map<Identifier, BiomeDayProfile> incoming) {
        PROFILES.clear();
        PROFILES.putAll(incoming);
    }

    public static float getDayAmount(ClientWorld world, float tickDelta) {
        float sunHeight = MathHelper.cos(world.getSkyAngle(tickDelta) * ((float) Math.PI * 2.0f)) * 2.0f + 0.5f;
        sunHeight = MathHelper.clamp(sunHeight, 0.0f, 1.0f);
        return MathHelper.clamp((sunHeight - 0.02f) / 0.98f, 0.0f, 1.0f);
    }

    public static DayBiomeBlend sample(ClientWorld world, Vec3d cameraPos) {
        if (PROFILES.isEmpty()) {
            return DayBiomeBlend.NONE;
        }

        BlockPos center = BlockPos.ofFloored(cameraPos);
        int radius = 2;
        int step = 2;

        double skyR = 0.0, skyG = 0.0, skyB = 0.0;
        double fogR = 0.0, fogG = 0.0, fogB = 0.0;
        double horizonR = 0.0, horizonG = 0.0, horizonB = 0.0;
        double cloudR = 0.0, cloudG = 0.0, cloudB = 0.0;

        int totalSamples = 0;
        int matchedSamples = 0;

        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                totalSamples++;

                BlockPos pos = center.add(dx * step, 0, dz * step);
                RegistryEntry<Biome> entry = world.getBiome(pos);
                Optional<RegistryKey<Biome>> key = entry.getKey();
                if (key.isEmpty()) continue;

                Identifier biomeId = key.get().getValue();
                BiomeDayProfile profile = PROFILES.get(biomeId);
                if (profile == null) continue;

                Vec3d sky = profile.skyVec();
                Vec3d fog = profile.fogVec();
                Vec3d horizon = profile.horizonVec();
                Vec3d cloud = profile.cloudVec();

                skyR += sky.x; skyG += sky.y; skyB += sky.z;
                fogR += fog.x; fogG += fog.y; fogB += fog.z;
                horizonR += horizon.x; horizonG += horizon.y; horizonB += horizon.z;
                cloudR += cloud.x; cloudG += cloud.y; cloudB += cloud.z;

                matchedSamples++;
            }
        }

        if (matchedSamples == 0) {
            return DayBiomeBlend.NONE;
        }

        float coverage = (float) matchedSamples / (float) totalSamples;

        return new DayBiomeBlend(
                new Vec3d(skyR / matchedSamples, skyG / matchedSamples, skyB / matchedSamples),
                new Vec3d(fogR / matchedSamples, fogG / matchedSamples, fogB / matchedSamples),
                new Vec3d(horizonR / matchedSamples, horizonG / matchedSamples, horizonB / matchedSamples),
                new Vec3d(cloudR / matchedSamples, cloudG / matchedSamples, cloudB / matchedSamples),
                coverage
        );
    }

    public record DayBiomeBlend(Vec3d sky, Vec3d fog, Vec3d horizon, Vec3d cloud, float weight) {
        public static final DayBiomeBlend NONE =
                new DayBiomeBlend(Vec3d.ZERO, Vec3d.ZERO, Vec3d.ZERO, Vec3d.ZERO, 0.0f);
    }
}