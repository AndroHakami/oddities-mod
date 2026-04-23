package net.seep.odd.sky.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.seep.odd.Oddities;
import net.seep.odd.event.alien.client.sky.AlienOverworldSkyCore;
import net.seep.odd.sky.CelestialEventClient;
import net.seep.odd.sky.day.BiomeDayGradeFx;
import net.seep.odd.sky.day.BiomeDayProfileNetworking;

public final class OverworldDreamSkyClient {
    private OverworldDreamSkyClient() {}

    public static ShaderProgram OVERWORLD_DREAM_SKY;
    public static ShaderProgram OVERWORLD_BIOME_DAY_SKY;

    private static boolean inited = false;

    public static void init() {
        if (inited) return;
        inited = true;

        AlienOverworldSkyCore.init();

        CoreShaderRegistrationCallback.EVENT.register(ctx -> {
            ctx.register(
                    new Identifier(Oddities.MOD_ID, "overworld_dream_sky"),
                    VertexFormats.POSITION,
                    program -> OVERWORLD_DREAM_SKY = program
            );

            ctx.register(
                    new Identifier(Oddities.MOD_ID, "overworld_biome_day_sky"),
                    VertexFormats.POSITION,
                    program -> OVERWORLD_BIOME_DAY_SKY = program
            );
        });

        DimensionRenderingRegistry.registerSkyRenderer(World.OVERWORLD, OverworldDreamSkyRenderer::render);

        ClientTickEvents.END_CLIENT_TICK.register(client -> CelestialEventClient.clientTick());

        BiomeDayProfileNetworking.initClient();
        BiomeDayGradeFx.init();
    }
}
