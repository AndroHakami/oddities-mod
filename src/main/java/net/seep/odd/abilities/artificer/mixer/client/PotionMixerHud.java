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
    private static final int BAR_W = 80;
    private static final int BAR_H = 6;
    private static final int PAD   = 3;

    // show within ~6 blocks (squared = 36)
    private static final double MAX_DIST_SQ = 36.0;

    @Override
    public void onHudRender(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;

        // Only show when looking at a Potion Mixer within reach
        if (!(mc.crosshairTarget instanceof BlockHitResult bhr)) return;
        BlockPos pos = bhr.getBlockPos();
        if (!(mc.world.getBlockEntity(pos) instanceof PotionMixerBlockEntity be)) return;

        // distance gate (from player to block center)
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        double dx = mc.player.getX() - cx;
        double dy = mc.player.getY() - cy;
        double dz = mc.player.getZ() - cz;
        if ((dx*dx + dy*dy + dz*dz) > MAX_DIST_SQ) return;

        int x = 12;
        int y = mc.getWindow().getScaledHeight()
                - (PAD + (BAR_H + PAD) * (EssenceType.values().length + 2)) - 12;

        // Box header
        var tf = mc.textRenderer;
        ctx.fill(
                x - 6,
                y - 8,
                x + BAR_W + 120,
                y + (BAR_H + PAD) * (EssenceType.values().length + 1) + 10,
                0xAA000000
        );

        // Title + speed/ready
        int speed = Math.round(Math.abs(be.getSpeed()));
        boolean ready = Math.abs(be.getSpeed()) >= PotionMixerBlockEntity.MIN_SPEED;
        ctx.drawText(tf, Text.literal("Potion Mixer"), x, y - 6, 0xFFFFFF, false);
        ctx.drawText(
                tf,
                Text.literal("Speed: " + speed + (ready ? " (READY)" : " (idle)")),
                x + BAR_W + 20,
                y - 6,
                ready ? 0x7CFC00 : 0xAAAAAA,
                false
        );

        y += 6;

        // One bar per essence
        final long capMb = 1000; // display cap per essence (mB)
        for (EssenceType t : EssenceType.values()) {
            long amt = be.getAmountDisplayMb(t); // your helper
            float pct = Math.min(1f, amt / (float) capMb);

            // Bar frame
            ctx.fill(x, y, x + BAR_W, y + BAR_H, 0xFF202020);

            // Inner
            int col = (t.argb | 0xFF000000); // force full alpha
            int fillW = Math.round(BAR_W * pct);
            ctx.fill(x + 1, y + 1, x + 1 + fillW, y + BAR_H - 1, col);

            // Label
            ctx.drawText(tf, Text.literal(t.key + ": " + amt + " mB"),
                    x + BAR_W + 10, y - 1, 0xFFFFFF, false);

            y += BAR_H + PAD;
        }
    }
}
