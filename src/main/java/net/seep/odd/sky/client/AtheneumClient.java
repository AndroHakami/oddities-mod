// src/main/java/net/seep/odd/sky/client/AtheneumClient.java
package net.seep.odd.sky.client;

import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.seep.odd.Oddities;

public final class AtheneumClient {
    private AtheneumClient() {}

    private static final RegistryKey<World> ATHENEUM =
            RegistryKey.of(RegistryKeys.WORLD, new Identifier(Oddities.MOD_ID, "atheneum"));

    public static ShaderProgram ATHENEUM_SKY; // set on shader reload

    private static boolean inited = false;

    public static void init() {
        if (inited) return;
        inited = true;

        // Load assets/<modid>/shaders/core/atheneum_sky.json
        CoreShaderRegistrationCallback.EVENT.register(ctx -> {
            ctx.register(new Identifier(Oddities.MOD_ID, "atheneum_sky"), VertexFormats.POSITION, program -> {
                ATHENEUM_SKY = program;
            });
        });


        // Replace the sky rendering for Atheneum
        DimensionRenderingRegistry.registerSkyRenderer(ATHENEUM, AtheneumSkyRenderer::render);

        // Optional: don’t draw vanilla clouds/weather in this dimension
        DimensionRenderingRegistry.registerCloudRenderer(ATHENEUM, ctx -> {});
        DimensionRenderingRegistry.registerWeatherRenderer(ATHENEUM, ctx -> {});
    }
}
