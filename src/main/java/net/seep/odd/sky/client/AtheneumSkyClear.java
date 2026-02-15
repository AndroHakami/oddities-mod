package net.seep.odd.sky.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;

public final class AtheneumSkyClear {
    private AtheneumSkyClear() {}

    public static void render(WorldRenderContext ctx) {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // black base (shader will paint the sky)
        RenderSystem.clearColor(0f, 0f, 0f, 1f);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
