package net.seep.odd.abilities.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class ClientShadowHud {
    private static int energy = 100; // 0..100
    private ClientShadowHud(){}

    public static void set(int v){ energy = Math.max(0, Math.min(100, v)); }

    public static void register() {
        HudRenderCallback.EVENT.register(ClientShadowHud::render);
    }

    private static void render(DrawContext ctx, float delta) {
        var mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        // Draw only if player has Umbra Soul power
        String id = ClientPowerHolder.get();
        if (!"umbra_soul".equals(id)) return;

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        int barW = 120, barH = 6;
        int x = (w - barW)/2;
        int y = h - 80; // above your ability icons

        // back
        ctx.fill(x-1, y-1, x+barW+1, y+barH+1, 0x66000000);
        // fill
        int fill = (int)(barW * (energy/100f));
        ctx.fill(x, y, x+fill, y+barH, 0xAA2F2F2F); // smoky gray
        // label
        String t = "Shadow " + energy + "%";
        int tw = mc.textRenderer.getWidth(t);
        ctx.drawTextWithShadow(mc.textRenderer, t, x + (barW-tw)/2, y - 10, 0xE0E0E0);
    }
}