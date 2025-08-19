package net.seep.odd.abilities.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.client.UmbraClientState;
import net.seep.odd.abilities.client.ClientPowerHolder;

public final class UmbraHudOverlay {
    private UmbraHudOverlay(){}

    public static void register() {
        HudRenderCallback.EVENT.register(UmbraHudOverlay::render);
    }

    private static void render(DrawContext ctx, float delta) {
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.isPaused()) return;

        // Only render for Umbra Soul
        String pid = ClientPowerHolder.get();
        if (!"umbra_soul".equals(pid)) return;

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        // Bar geometry (center bottom, above hotbar)
        int barW = 140;
        int barH = 8;
        int x = (w - barW) / 2;
        int y = h - 64 - 16;  // just above your ability icons

        // Backdrop
        ctx.fill(x - 2, y - 2, x + barW + 2, y + barH + 2, 0x66000000);
        ctx.fill(x, y, x + barW, y + barH, 0xAA111111);

        float pct = Math.max(0f, Math.min(1f, UmbraClientState.energy() / UmbraClientState.max()));
        int fill = (int)(barW * pct);

        // Dark red accent
        int color = 0xCC8A1010; // dark red with some alpha
        ctx.fill(x, y, x + fill, y + barH, color);

        // Edge outline
        ctx.drawBorder(x - 2, y - 2, barW + 4, barH + 4, 0x55FFFFFF);

        // Optional active marker
        if (UmbraClientState.active()) {
            ctx.drawTextWithShadow(mc.textRenderer, "SHADOW", x + barW + 8, y - 2, 0xFF8A1010);
        }
    }
}
