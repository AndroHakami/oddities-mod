package net.seep.odd.worldgen;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.biome.Biome;
import net.seep.odd.Oddities;
import net.seep.odd.entity.ModEntities;

public final class ModSpawns {
    private ModSpawns(){}

    public static final RegistryKey<Biome> ROTTEN_ROOTS_KEY =
            RegistryKey.of(RegistryKeys.BIOME, new Identifier(Oddities.MOD_ID, "rotten_roots"));

    public static void register() {
        // --- FALSE FROG (hostile) ---
        if (ModEntities.FALSE_FROG != null) {
            BiomeModifications.addSpawn(
                    BiomeSelectors.includeByKey(ROTTEN_ROOTS_KEY),
                    SpawnGroup.MONSTER,
                    ModEntities.FALSE_FROG,
                    30,   // weight
                    1,    // min group
                    2     // max group
            );
            SpawnRestriction.register(
                    ModEntities.FALSE_FROG,
                    SpawnRestriction.Location.ON_GROUND,
                    Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                    ModSpawns::allowFalseFrog
            );
        }

        // --- FIREFLY (ambient) ---
        if (ModEntities.FIREFLY != null) {
            BiomeModifications.addSpawn(
                    BiomeSelectors.includeByKey(ROTTEN_ROOTS_KEY),
                    SpawnGroup.AMBIENT,
                    ModEntities.FIREFLY,
                    25,   // weight
                    2,    // min group
                    5     // max group
            );
            SpawnRestriction.register(
                    ModEntities.FIREFLY,
                    SpawnRestriction.Location.NO_RESTRICTIONS,
                    Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                    ModSpawns::allowFirefly
            );
        }
    }

    /* ---------------- Predicates used by SpawnRestriction.register ---------------- */

    private static boolean allowFalseFrog(EntityType<?> type,
                                          ServerWorldAccess world,
                                          SpawnReason reason,
                                          BlockPos pos,
                                          Random rand) {
        // Only in Rotten Roots biome
        if (!world.getBiome(pos).matchesKey(ROTTEN_ROOTS_KEY)) return false;

        // Must have solid ground directly below
        if (!world.getBlockState(pos.down()).isOpaqueFullCube(world, pos.down())) return false;

        // Keep spawns in a sane vertical band for your dimension
        int y = pos.getY();
        if (y < -16 || y > 320) return false;

        return true;
    }

    private static boolean allowFirefly(EntityType<?> type,
                                        ServerWorldAccess world,
                                        SpawnReason reason,
                                        BlockPos pos,
                                        Random rand) {
        // Only in Rotten Roots biome
        if (!world.getBiome(pos).matchesKey(ROTTEN_ROOTS_KEY)) return false;

        // Let them float basically anywhere reasonable in the space
        int y = pos.getY();
        return y > -16 && y < 340;
    }
}
