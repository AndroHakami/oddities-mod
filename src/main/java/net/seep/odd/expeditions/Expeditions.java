package net.seep.odd.expeditions;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import net.seep.odd.Oddities;
import net.seep.odd.expeditions.rottenroots.RottenRootsChunkGenerator;
import net.seep.odd.expeditions.rottenroots.RottenRootsCommands;

public final class Expeditions {
    private Expeditions() {}

    public static final Identifier ROTTEN_ROOTS_ID = new Identifier(Oddities.MOD_ID, "rotten_roots");

    public static final RegistryKey<World> ROTTEN_ROOTS_WORLD = RegistryKey.of(RegistryKeys.WORLD, ROTTEN_ROOTS_ID);
    public static final RegistryKey<Biome> ROTTEN_ROOTS_BIOME = RegistryKey.of(RegistryKeys.BIOME, ROTTEN_ROOTS_ID);

    public static void register() {
        // ✅ 1.20.1 wants a Codec here (Registry<Codec<? extends ChunkGenerator>>)
        // ✅ also: use the stable CODEC constant (do NOT call .codec() here)
        Registry.register(Registries.CHUNK_GENERATOR, ROTTEN_ROOTS_ID, RottenRootsChunkGenerator.CODEC);

        // Commands
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
                RottenRootsCommands.register(dispatcher));
    }
}
