package net.seep.odd.abilities.looker;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;

import net.seep.odd.abilities.looker.client.LookerInvisFx;

@Environment(EnvType.CLIENT)
public final class LookerClient {
    private LookerClient() {}

    private static boolean INIT = false;

    // state mirrored from server when invis toggles
    private static boolean overlayOn = false;
    private static int clientMeter = 0;
    private static int clientMax   = 1;

    /** Wire everything on client init. */
    public static void init() {
        if (INIT) return;
        INIT = true;

        LookerNet.registerClient();
        LookerInvisFx.init();

        // draw ONLY meter while invisible (overlay is now Satin)
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            if (!overlayOn) return;
            drawMeter(ctx);
        });

        // local smooth countdown so meter looks fluid during invis
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (overlayOn && clientMeter > 0) clientMeter--;
            if (client.world == null) overlayOn = false;
        });
    }

    /** Called by LookerNet when server toggles invis overlay. */
    static void handleOverlay(boolean on, int meter, int max) {
        overlayOn   = on;
        clientMeter = Math.max(0, meter);
        clientMax   = Math.max(1, max);

        // drive Satin effect
        LookerInvisFx.setActive(on, clientMeter, clientMax);
    }

    /**
     * Ability-specific invis helper for render mixins.
     * Uses the synced tracked flag for every player, with a local overlay fallback for the owner.
     */
    public static boolean isLookerInvisible(LivingEntity e) {
        if (e instanceof OddLookerInvisibility looker && looker.oddities$isLookerInvisible()) {
            return true;
        }

        var mc = MinecraftClient.getInstance();
        return mc != null && mc.player == e && LookerInvisFx.isActive();
    }

    /* ---------- meter rendering ---------- */

    private static void drawMeter(DrawContext ctx) {
        var mc = MinecraftClient.getInstance();
        if (mc == null) return;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        float frac = clientMax > 0 ? Math.max(0f, Math.min(1f, clientMeter / (float) clientMax)) : 0f;

        int barW = Math.min(160, (int) (sw * 0.35));
        int barH = 8;
        int x = (sw - barW) / 2;
        int y = sh - 52;

        int bg   = 0x66000000;
        int edge = 0x88FFFFFF;
        int fill = 0xFFB6D7FF;

        ctx.fill(x - 2, y - 2, x + barW + 2, y + barH + 2, bg);
        ctx.drawBorder(x - 2, y - 2, barW + 4, barH + 4, edge);

        int fillW = (int) (barW * frac);
        ctx.fill(x, y, x + fillW, y + barH, fill);

        var tr = mc.textRenderer;
        int secs = Math.max(0, (int) Math.ceil(clientMeter / 20.0));
        String s = secs + "s";
        int tw = tr.getWidth(s);
        ctx.drawText(tr, s, x + barW - tw, y - 10, 0xFFEDEDED, true);
    }
}
