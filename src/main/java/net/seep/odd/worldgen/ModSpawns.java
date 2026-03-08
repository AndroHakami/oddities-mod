package net.seep.odd.worldgen;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registry;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import net.minecraft.world.Heightmap;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.biome.Biome;

import net.minecraft.world.gen.structure.Structure;
import net.minecraft.structure.StructureStart;

import net.seep.odd.Oddities;
import net.seep.odd.entity.ModEntities;

public final class ModSpawns {
    private ModSpawns(){}

    public static final RegistryKey<Biome> ROTTEN_ROOTS_KEY =
            RegistryKey.of(RegistryKeys.BIOME, new Identifier(Oddities.MOD_ID, "rotten_roots"));

    // Your safe structure id
    private static final Identifier SHROOM_VILLAGE_ID = new Identifier(Oddities.MOD_ID, "shroom_village");

    public static void register() {

        // --- SKITTER (common hostile) ---
        if (ModEntities.SKITTER != null) {
            BiomeModifications.addSpawn(
                    BiomeSelectors.includeByKey(ROTTEN_ROOTS_KEY),
                    SpawnGroup.MONSTER,
                    ModEntities.SKITTER,
                    80,   // ✅ common
                    1,
                    3
            );
            SpawnRestriction.register(
                    ModEntities.SKITTER,
                    SpawnRestriction.Location.ON_GROUND,
                    Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                    ModSpawns::allowSkitter
            );
        }

        // --- FALSE FROG (rarer hostile) ---
        if (ModEntities.FALSE_FROG != null) {
            BiomeModifications.addSpawn(
                    BiomeSelectors.includeByKey(ROTTEN_ROOTS_KEY),
                    SpawnGroup.MONSTER,
                    ModEntities.FALSE_FROG,
                    15,   // ✅ rarer now
                    1,
                    1
            );
            SpawnRestriction.register(
                    ModEntities.FALSE_FROG,
                    SpawnRestriction.Location.ON_GROUND,
                    Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                    ModSpawns::allowFalseFrog
            );
        }

        // --- FIREFLY (ambient, allowed everywhere incl. village) ---
        if (ModEntities.FIREFLY != null) {
            BiomeModifications.addSpawn(
                    BiomeSelectors.includeByKey(ROTTEN_ROOTS_KEY),
                    SpawnGroup.AMBIENT,
                    ModEntities.FIREFLY,
                    25,
                    2,
                    5
            );
            SpawnRestriction.register(
                    ModEntities.FIREFLY,
                    SpawnRestriction.Location.NO_RESTRICTIONS,
                    Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                    ModSpawns::allowFirefly
            );
        }
    }

    /* ---------------- Predicates ---------------- */

    private static boolean allowSkitter(EntityType<?> type,
                                        ServerWorldAccess world,
                                        SpawnReason reason,
                                        BlockPos pos,
                                        Random rand) {
        if (!world.getBiome(pos).matchesKey(ROTTEN_ROOTS_KEY)) return false;

        // Safe zone: no skitters inside shroom village
        if (isInsideShroomVillage(world, pos)) return false;

        if (!world.getBlockState(pos.down()).isOpaqueFullCube(world, pos.down())) return false;

        int y = pos.getY();
        return y > -16 && y < 340;
    }

    private static boolean allowFalseFrog(EntityType<?> type,
                                          ServerWorldAccess world,
                                          SpawnReason reason,
                                          BlockPos pos,
                                          Random rand) {
        if (!world.getBiome(pos).matchesKey(ROTTEN_ROOTS_KEY)) return false;

        // Safe zone: no frogs inside shroom village
        if (isInsideShroomVillage(world, pos)) return false;

        if (!world.getBlockState(pos.down()).isOpaqueFullCube(world, pos.down())) return false;

        int y = pos.getY();
        return y > -16 && y < 320;
    }

    private static boolean allowFirefly(EntityType<?> type,
                                        ServerWorldAccess world,
                                        SpawnReason reason,
                                        BlockPos pos,
                                        Random rand) {
        if (!world.getBiome(pos).matchesKey(ROTTEN_ROOTS_KEY)) return false;
        int y = pos.getY();
        return y > -16 && y < 340;
    }

    /* ---------------- Structure safe-zone check ---------------- */

    private static boolean isInsideShroomVillage(ServerWorldAccess world, BlockPos pos) {
        if (!(world instanceof ServerWorld sw)) return false;

        Registry<Structure> reg = sw.getRegistryManager().get(RegistryKeys.STRUCTURE);
        Structure shroomVillage = reg.get(SHROOM_VILLAGE_ID);
        if (shroomVillage == null) return false;

        StructureStart start = sw.getChunk(pos).getStructureStart(shroomVillage);
        if (start == null || !start.hasChildren()) return false;

        return start.getBoundingBox().contains(pos);
    }
}