package net.seep.odd.abilities.looker;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

import net.seep.odd.Oddities;

@Environment(EnvType.CLIENT)
public final class LookerClient {
    private LookerClient() {}

    private static boolean INIT = false;

    // state mirrored from server when invis toggles
    private static boolean overlayOn = false;
    private static int clientMeter = 0;
    private static int clientMax   = 1;

    // your texture goes here (PNG with transparency!)
    private static final Identifier OVERLAY_TEX =
            new Identifier(Oddities.MOD_ID, "textures/misc/looker_phase_overlay.png");

    /** Wire everything on client init. */
    public static void init() {
        if (INIT) return;
        INIT = true;

        LookerNet.registerClient();

        // draw overlay + meter while invisible
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            if (!overlayOn) return;
            drawOverlay(ctx);
            drawMeter(ctx);
        });

        // local smooth countdown so meter looks fluid during invis
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (overlayOn && clientMeter > 0) clientMeter--;
            if (client.world == null) overlayOn = false; // world change safety
        });
    }

    /** Called by LookerNet when server toggles invis overlay. */
    static void handleOverlay(boolean on, int meter, int max) {
        overlayOn  = on;
        clientMeter = Math.max(0, meter);
        clientMax   = Math.max(1, max);
    }

    /** Armor mixin helper: hide armor when local player is looker-invisible. */
    public static boolean isLookerInvisible(LivingEntity e) {
        var mc = MinecraftClient.getInstance();
        return overlayOn && mc != null && mc.player == e;
    }

    /* ---------- rendering ---------- */

    /** Fullscreen overlay that respects the texture's alpha and multiplies a fade factor. */
    private static void drawOverlay(DrawContext ctx) {
        var mc = MinecraftClient.getInstance();
        if (mc == null) return;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        // fraction remaining; use it to pulse alpha when low
        float frac  = clientMax > 0 ? Math.max(0f, Math.min(1f, clientMeter / (float) clientMax)) : 0f;

        // baseline alpha multiplier — texture’s own transparency still applies
        float alpha = 0.92f;
        // pulse when almost empty
        if (frac < 0.15f) {
            float t = (System.currentTimeMillis() % 400L) / 400f;
            alpha = 0.55f + 0.45f * (float) Math.sin(t * (float) Math.PI * 2f);
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.setShaderColor(1f, 1f, 1f, alpha);   // <- this makes sure we **respect texture alpha** and apply ours
        ctx.drawTexture(OVERLAY_TEX, 0, 0, 0, 0, sw, sh, sw, sh);
        ctx.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    /** Simple yellow meter bar above the hotbar; only visible while invisible. */
    private static void drawMeter(DrawContext ctx) {
        var mc = MinecraftClient.getInstance();
        if (mc == null) return;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        float frac = clientMax > 0 ? Math.max(0f, Math.min(1f, clientMeter / (float) clientMax)) : 0f;

        int barW = Math.min(160, (int) (sw * 0.35));
        int barH = 8;
        int x = (sw - barW) / 2;
        int y = sh - 52; // just above the hotbar

        int bg   = 0x66000000; // translucent black
        int edge = 0x88FFFFFF; // soft white border
        int fill = 0xFFFFD447; // yellow

        // bg + border
        ctx.fill(x - 2, y - 2, x + barW + 2, y + barH + 2, bg);
        ctx.drawBorder(x - 2, y - 2, barW + 4, barH + 4, edge);

        // fill
        int fillW = (int) (barW * frac);
        ctx.fill(x, y, x + fillW, y + barH, fill);

        // text (seconds left) on the right
        var tr = mc.textRenderer;
        int secs = Math.max(0, (int) Math.ceil(clientMeter / 20.0));
        String s = secs + "s";
        int tw = tr.getWidth(s);
        ctx.drawText(tr, s, x + barW - tw, y - 10, 0xFFEDEDED, true);
    }
}
