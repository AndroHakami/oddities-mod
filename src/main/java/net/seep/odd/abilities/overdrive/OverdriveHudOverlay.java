package net.seep.odd.abilities.overdrive;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class OverdriveHudOverlay implements HudRenderCallback {
    private static float energy   = 0f;
    private static int   mode     = 0; // 0=Normal,1=Energized,2=Overdrive
    private static int   odTicks  = 0;
    public static float getEnergy() { return energy; }       // 0..100 (or 0..1 — see note below)
    public static int   getMode()   { return mode; }         // 0=Normal,1=Energized,2=Overdrive
    public static int   getOdTicks(){ return odTicks; }

    private OverdriveHudOverlay() {}

    public static void register() {
        HudRenderCallback.EVENT.register(new OverdriveHudOverlay());
    }

    public static void clientHudUpdate(float e, int m, int odLeft) {
        energy = e;
        mode = m;
        odTicks = odLeft;
    }

    @Override
    public void onHudRender(DrawContext ctx, float tickDelta) {
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        if (!"overdrive".equals(net.seep.odd.abilities.client.ClientPowerHolder.get())) return;

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        int barW = 140, barH = 10;
        int x = w - barW - 12;
        int y = 12;

        // frame
        ctx.fill(x - 2, y - 2, x + barW + 2, y + barH + 2, 0x80000000);
        ctx.fill(x, y, x + barW, y + barH, 0xFF101010);

        // fill
        int fillW = (int) (barW * (energy / 100f));
        int color = (mode == 2) ? 0xFFFF8800 : 0xFFEE6A00; // brighter in overdrive
        ctx.fill(x, y, x + fillW, y + barH, color);

        // text
        String mStr = (mode == 0) ? "Normal" : (mode == 1) ? "Energized" : "OVERDRIVE";
        String right = (mode == 2) ? ("OD " + odTicks/20 + "s") : (int)energy + "%";
        ctx.drawText(mc.textRenderer, "Overdrive [" + mStr + "]", x, y + barH + 4, 0xFFE0E0E0, true);
        int tw = mc.textRenderer.getWidth(right);
        ctx.drawText(mc.textRenderer, right, x + barW - tw, y + barH + 4, 0xFFFFC080, true);
    }
}
