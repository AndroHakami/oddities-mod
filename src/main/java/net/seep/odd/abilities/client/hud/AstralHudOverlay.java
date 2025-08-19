package net.seep.odd.abilities.client.hud;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public final class AstralHudOverlay implements HudRenderCallback {

    public static void register() {
        HudRenderCallback.EVENT.register(new AstralHudOverlay());
    }

    @Override
    public void onHudRender(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;
        if (!AstralClientState.isActive()) return;

        long ticksLeft = AstralClientState.ticksLeft(mc);
        double dist = AstralClientState.distanceToAnchor(mc);

        // Format time mm:ss
        long secs = Math.max(0, ticksLeft / 20);
        long mm = secs / 60;
        long ss = secs % 60;
        String timeStr = String.format("%d:%02d", mm, ss);

        String distStr = Double.isNaN(dist) ? "—" : String.format("%.1f m", dist);
        String line = "Astral • " + timeStr + " • " + distStr;

        TextRenderer tr = mc.textRenderer;
        int w = mc.getWindow().getScaledWidth();

        // Top-center placement
        int tw = tr.getWidth(line);
        int x = (w - tw) / 2;
        int y = 8; // a little padding from the very top

        // backdrop
        int padX = 6;
        int padY = 4;
        ctx.fill(x - padX, y - padY, x + tw + padX, y + tr.fontHeight + padY, 0x66000000);

        // dark red text
        int darkRed = 0xFF8A0D0D; // ARGB
        ctx.drawTextWithShadow(tr, Text.literal(line), x, y, darkRed);
    }
}
