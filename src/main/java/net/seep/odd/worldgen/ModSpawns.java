package net.seep.odd.worldgen;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.entity.ModEntities;

public final class ModSpawns {
    private ModSpawns() {}

    // Tag your Rotten Roots biomes with this.
    public static final TagKey<net.minecraft.world.biome.Biome> ROTTEN_ROOTS_BIOMES =
            TagKey.of(RegistryKeys.BIOME, new Identifier(Oddities.MOD_ID, "rotten_roots"));

    public static void registerFalseFrogSpawns() {
        BiomeModifications.addSpawn(
                BiomeSelectors.tag(ROTTEN_ROOTS_BIOMES),
                SpawnGroup.MONSTER,
                ModEntities.FALSE_FROG,
                12, // weight
                1,  // min
                2   // max
        );
    }
}
