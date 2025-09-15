package net.seep.odd.abilities.artificer.mixer.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

import net.seep.odd.abilities.artificer.EssenceType;
import net.seep.odd.abilities.artificer.mixer.PotionMixerBlockEntity;

public final class PotionMixerHud implements HudRenderCallback {
    private static final int BAR_W = 80;
    private static final int BAR_H = 6;
    private static final int PAD   = 3;

    @Override
    public void onHudRender(DrawContext ctx, float tickDelta) {
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;

        // Only show when looking at a Potion Mixer within reach
        if (!(mc.crosshairTarget instanceof BlockHitResult bhr)) return;
        BlockPos pos = bhr.getBlockPos();
        if (!(mc.world.getBlockEntity(pos) instanceof PotionMixerBlockEntity be)) return;

        int x = 12;
        int y = mc.getWindow().getScaledHeight() - (PAD + (BAR_H + PAD) * (EssenceType.values().length + 2)) - 12;

        // Box header
        var tf = mc.textRenderer;
        ctx.fill(x-6, y-8, x + BAR_W + 120, y + (BAR_H + PAD) * (EssenceType.values().length + 1) + 10,
                0xAA000000);

        // Title + speed/ready
        int speed = Math.round(Math.abs(be.getSpeed()));
        boolean ready = Math.abs(be.getSpeed()) >= PotionMixerBlockEntity.MIN_SPEED;
        ctx.drawText(tf, Text.literal("Potion Mixer"), x, y - 6, 0xFFFFFF, false);
        ctx.drawText(tf, Text.literal("Speed: " + speed + (ready ? " (READY)" : " (idle)")),
                x + BAR_W + 20, y - 6, ready ? 0x7CFC00 : 0xAAAAAA, false);

        y += 6;

        // One bar per essence
        long capFabric = be.getWorld() != null ? be.getWorld().getTickOrder() : 0; // unused, just to avoid null warnings

        long cap = 1000; // mB per essence (display number)
        for (EssenceType t : EssenceType.values()) {
            long amt = be.getAmountDisplayMb(t); // we’ll add this helper below
            float pct = Math.min(1f, amt / 1000f);

            // Bar frame
            ctx.fill(x, y, x + BAR_W, y + BAR_H, 0xFF202020);
            // Inner
            int col = t.argb | 0xFF000000;
            int fillW = Math.round(BAR_W * pct);
            ctx.fill(x + 1, y + 1, x + 1 + fillW, y + BAR_H - 1, col);

            // Label
            ctx.drawText(tf, Text.literal(t.key + ": " + amt + " mB"), x + BAR_W + 10, y - 1, 0xFFFFFF, false);
            y += BAR_H + PAD;
        }
    }
}
