// src/main/java/net/seep/odd/client/RottenRootsClient.java
package net.seep.odd.sky.client;

import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import net.seep.odd.Oddities;
import net.seep.odd.sky.client.RottenRootsDimensionEffects;
import net.seep.odd.sky.client.RottenRootsSkyClear;

public final class RottenRootsClient {
    private RottenRootsClient() {}

    private static final RegistryKey<World> ROTTEN_ROOTS =
            RegistryKey.of(RegistryKeys.WORLD, new Identifier(Oddities.MOD_ID, "rotten_roots"));

    private static final Identifier EFFECTS_ID =
            new Identifier(Oddities.MOD_ID, "rotten_roots_effects");

    private static boolean inited = false;

    public static void init() {
        if (inited) return;
        inited = true;

        // DimensionEffects (fog + no sun/moon/stars)
        DimensionRenderingRegistry.registerDimensionEffects(EFFECTS_ID, new RottenRootsDimensionEffects());

        // Make the sky a flat murky color (no geometry)
        DimensionRenderingRegistry.registerSkyRenderer(ROTTEN_ROOTS, RottenRootsSkyClear::render);

        // Optional: disable clouds + weather visuals
        DimensionRenderingRegistry.registerCloudRenderer(ROTTEN_ROOTS, ctx -> {});
        DimensionRenderingRegistry.registerWeatherRenderer(ROTTEN_ROOTS, ctx -> {});
    }
}
