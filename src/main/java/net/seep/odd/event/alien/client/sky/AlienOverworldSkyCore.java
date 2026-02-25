package net.seep.odd.event.alien.client.sky;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

@Environment(EnvType.CLIENT)
public final class AlienOverworldSkyCore {
    private AlienOverworldSkyCore() {}

    public static ShaderProgram ALIEN_SKY = null;

    public static void init() {
        CoreShaderRegistrationCallback.EVENT.register(ctx -> {
            ctx.register(
                    new Identifier(Oddities.MOD_ID, "alien_overworld_sky"),
                    VertexFormats.POSITION,
                    program -> ALIEN_SKY = program
            );
        });
    }
}