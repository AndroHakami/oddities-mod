package net.seep.odd.abilities.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.client.ClientPowerHolder;
import net.seep.odd.abilities.net.UmbraNet;

/** Draws the user-provided 256×256 edge overlay texture while in shadow form. */
public final class UmbraOverlay {
    private UmbraOverlay() {}

    private static final Identifier OVERLAY_TEX = new Identifier("odd", "textures/misc/umbra_overlay.png");
    private static float fade = 0f; // 0..1
    private static long lastGameTime = -1L;

    private static final float FADE_IN_TICKS  = 6f;
    private static final float FADE_OUT_TICKS = 10f;

    public static void register() {
        HudRenderCallback.EVENT.register(UmbraOverlay::render);
    }

    private static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.isPaused()) return;

        boolean hasUmbraSelected = "umbra_soul".equals(ClientPowerHolder.get());
        boolean targetActive = hasUmbraSelected && UmbraNet.isClientActive();

        long now = (mc.world != null) ? mc.world.getTime() : 0L;
        long dtTicks = (lastGameTime < 0) ? 0 : Math.max(0, now - lastGameTime);
        lastGameTime = now;

        if (targetActive) {
            fade = (FADE_IN_TICKS > 0f) ? Math.min(1f, fade + (dtTicks / FADE_IN_TICKS)) : 1f;
        } else {
            fade = (FADE_OUT_TICKS > 0f) ? Math.max(0f, fade - (dtTicks / FADE_OUT_TICKS)) : 0f;
        }
        if (fade <= 0f) return;

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        // soft breathing
        double t = (now + tickDelta) / 20.0;
        float pulse = 0.90f + 0.10f * (float)Math.sin(t * Math.PI);
        float alpha = 0.85f * fade * pulse;

        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        // Draw as single quad (no tiling): region 256×256, texture size 256×256
        ctx.drawTexture(
                OVERLAY_TEX,
                0, 0,
                w, h,
                0, 0,
                256, 256,
                256, 256
        );
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableDepthTest();
    }
}
