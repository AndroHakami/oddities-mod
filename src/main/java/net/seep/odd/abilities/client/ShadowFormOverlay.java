package net.seep.odd.abilities.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.net.UmbraNet;

/** Black dim + soft pulsing vignette while in Umbra shadow form. */
public final class ShadowFormOverlay {
    private ShadowFormOverlay() {}

    private static final Identifier VIGNETTE_TEX = new Identifier("odd", "textures/misc/umbra_vignette.png");
    private static float fade = 0f; // 0..1
    private static long lastGameTime = -1L;

    private static final float FADE_IN_TICKS  = 6f;   // ~0.3s
    private static final float FADE_OUT_TICKS = 10f;  // ~0.5s

    public static void register() {
        HudRenderCallback.EVENT.register(ShadowFormOverlay::render);
    }

    private static void render(DrawContext ctx, float tickDelta) {
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.isPaused()) return;

        // Only show when Umbra Soul is the current power AND it's active
        boolean targetActive = "umbra_soul".equals(ClientPowerHolder.get()) && UmbraNet.isClientActive();

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

        // base black dim intensity scales with missing energy
        int energy = UmbraNet.getClientEnergy();
        int max    = Math.max(1, UmbraNet.getClientMax());
        float missing = 1f - Math.min(1f, energy / (float) max);
        float dimAlphaF = (0.25f + 0.35f * missing) * fade;
        int dimA = ((int)(dimAlphaF * 255f)) & 0xFF;
        int dimARGB = (dimA << 24);
        ctx.fill(0, 0, w, h, dimARGB);

        // vignette (512×512), non-tiling, soft pulse
        double t = (now + tickDelta) / 20.0;
        float pulse = 0.90f + 0.10f * (float)Math.sin(t * Math.PI);
        float vignetteAlpha = 0.80f * fade * pulse;

        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1f, 1f, 1f, vignetteAlpha);
        ctx.drawTexture(
                VIGNETTE_TEX,
                0, 0,
                w, h,
                0, 0,
                512, 512,
                512, 512
        );
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableDepthTest();
    }
}
