package net.seep.odd.abilities.supercharge;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public final class SuperHud {
    private SuperHud() {}

    private static boolean show;
    private static int cur;
    private static int max;

    public static void onHud(boolean s, int c, int m) {
        show = s; cur = c; max = m;
    }

    public static void init() {
        HudRenderCallback.EVENT.register((DrawContext ctx, float tickDelta) -> {
            if (!show || max <= 0) return;
            var mc = MinecraftClient.getInstance();
            int sw = mc.getWindow().getScaledWidth();
            int sh = mc.getWindow().getScaledHeight();
            float pct = MathHelper.clamp(cur / (float)max, 0f, 1f);

            int orange = 0xFFFF8C00;
            int bg     = 0x66FF8C00;

            int cx = sw / 2, cy = sh / 2;
            drawRing(ctx, cx, cy, 14, 22, pct, 64, orange, bg);

            String label = (int)(pct * 100) + "%";
            int w = mc.textRenderer.getWidth(label);
            ctx.drawText(mc.textRenderer, label, cx - w/2, cy - 4, 0xFFFFFFFF, true);
        });
    }

    private static void drawRing(DrawContext ctx, int cx, int cy, int rIn, int rOut, float pct, int steps, int fill, int bg) {
        drawArc(ctx, cx, cy, rIn, rOut, 0f, 1f, steps, bg);
        drawArc(ctx, cx, cy, rIn, rOut, 0f, pct, steps, fill);
    }
    private static void drawArc(DrawContext ctx, int cx, int cy, int rIn, int rOut, float from, float to, int steps, int color) {
        to = Math.max(to, from);
        int segs = Math.max(1, Math.round(steps * (to - from)));
        for (int i = 0; i < segs; i++) {
            float a0 = (from + (i    /(float)segs)) * (float)(Math.PI * 2);
            float a1 = (from + ((i+1)/(float)segs)) * (float)(Math.PI * 2);
            int x0o = cx + (int)(Math.cos(a0) * rOut), y0o = cy + (int)(Math.sin(a0) * rOut);
            int x1o = cx + (int)(Math.cos(a1) * rOut), y1o = cy + (int)(Math.sin(a1) * rOut);
            int x0i = cx + (int)(Math.cos(a0) * rIn ), y0i = cy + (int)(Math.sin(a0) * rIn );
            int x1i = cx + (int)(Math.cos(a1) * rIn ), y1i = cy + (int)(Math.sin(a1) * rIn );
            ctx.fill(Math.min(x0i,x1o), Math.min(y0i,y1o), Math.max(x0i,x1o), Math.max(y0i,y1o), color);
            ctx.fill(Math.min(x1i,x1o), Math.min(y1i,y1o), Math.max(x1i,x1o), Math.max(y1i,y1o), color);
        }
    }
}
