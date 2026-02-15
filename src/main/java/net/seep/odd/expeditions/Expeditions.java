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

import net.seep.odd.expeditions.atheneum.AtheneumChunkGenerator;
import net.seep.odd.expeditions.atheneum.AtheneumCommands;

public final class Expeditions {
    private Expeditions() {}

    public static final Identifier ROTTEN_ROOTS_ID = new Identifier(Oddities.MOD_ID, "rotten_roots");
    public static final RegistryKey<World> ROTTEN_ROOTS_WORLD = RegistryKey.of(RegistryKeys.WORLD, ROTTEN_ROOTS_ID);
    public static final RegistryKey<Biome> ROTTEN_ROOTS_BIOME = RegistryKey.of(RegistryKeys.BIOME, ROTTEN_ROOTS_ID);

    // ✅ Atheneum
    public static final Identifier ATHENEUM_ID = new Identifier(Oddities.MOD_ID, "atheneum");
    public static final RegistryKey<World> ATHENEUM_WORLD = RegistryKey.of(RegistryKeys.WORLD, ATHENEUM_ID);
    public static final RegistryKey<Biome> ATHENEUM_BIOME = RegistryKey.of(RegistryKeys.BIOME, ATHENEUM_ID);

    public static void register() {
        // Rotten Roots
        Registry.register(Registries.CHUNK_GENERATOR, ROTTEN_ROOTS_ID, RottenRootsChunkGenerator.CODEC);
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
                RottenRootsCommands.register(dispatcher));

        // Atheneum
        Registry.register(Registries.CHUNK_GENERATOR, ATHENEUM_ID, AtheneumChunkGenerator.CODEC);
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
                AtheneumCommands.register(dispatcher));
    }
}
