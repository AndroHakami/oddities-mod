package net.seep.odd.abilities.artificer.mixer.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.abilities.artificer.EssenceType;
import net.seep.odd.abilities.artificer.mixer.PotionMixerBlockEntity;

public final class PotionMixerHud implements HudRenderCallback {
    private static final int BAR_W = 80, BAR_H = 6, PAD = 3;
    private static final double MAX_DIST_SQ = 36.0;

    @Override
    public void onHudRender(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult bhr)) return;

        BlockPos pos = bhr.getBlockPos();
        if (!(mc.world.getBlockEntity(pos) instanceof PotionMixerBlockEntity be)) return;

        double dx = mc.player.getX() - (pos.getX() + 0.5);
        double dy = mc.player.getY() - (pos.getY() + 0.5);
        double dz = mc.player.getZ() - (pos.getZ() + 0.5);
        if ((dx*dx + dy*dy + dz*dz) > MAX_DIST_SQ) return;

        int x = 12;
        int y = mc.getWindow().getScaledHeight()
                - (PAD + (BAR_H + PAD) * (EssenceType.values().length + 2)) - 12;

        var tf = mc.textRenderer;
        ctx.fill(x-6, y-8, x + BAR_W + 120, y + (BAR_H + PAD) * (EssenceType.values().length + 1) + 10, 0xAA000000);

        int speed = Math.round(Math.abs(be.getSpeedAbs()));
        boolean ready = be.isPoweredAndReady();
        ctx.drawText(tf, Text.literal("Potion Mixer"), x, y - 6, 0xFFFFFF, false);
        ctx.drawText(tf, Text.literal("Speed: " + speed + (ready ? " (READY)" : " (idle)")),
                x + BAR_W + 20, y - 6, ready ? 0x7CFC00 : 0xAAAAAA, false);

        y += 6;
        final long capMb = 1000;
        for (EssenceType t : EssenceType.values()) {
            long amt = be.getAmountDisplayMb(t);
            float pct = Math.min(1f, amt / (float) capMb);
            ctx.fill(x, y, x + BAR_W, y + BAR_H, 0xFF202020);
            int col = (t.argb | 0xFF000000);
            int fillW = Math.round(BAR_W * pct);
            ctx.fill(x + 1, y + 1, x + 1 + fillW, y + BAR_H - 1, col);
            ctx.drawText(tf, Text.literal(t.key + ": " + amt + " mB"),
                    x + BAR_W + 10, y - 1, 0xFFFFFF, false);
            y += BAR_H + PAD;
        }
    }
}
