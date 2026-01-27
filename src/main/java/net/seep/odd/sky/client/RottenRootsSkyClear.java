// src/main/java/net/seep/odd/client/RottenRootsSkyClear.java
package net.seep.odd.sky.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;

public final class RottenRootsSkyClear {
    private RottenRootsSkyClear() {}

    // match the DimensionEffects color
    private static final float R = 0.46f;
    private static final float G = 0.52f;
    private static final float B = 0.49f;

    public static void render(WorldRenderContext ctx) {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        // important: don't inherit vanilla tint
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // just set the background
        RenderSystem.clearColor(R, G, B, 1f);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
