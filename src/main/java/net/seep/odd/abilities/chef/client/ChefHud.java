package net.seep.odd.abilities.chef.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.seep.odd.block.supercooker.SuperCookerBlockEntity;

public final class ChefHud {
    private ChefHud() {}

    public static void init() {
        HudRenderCallback.EVENT.register(ChefHud::render);
    }

    private static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        HitResult hr = mc.crosshairTarget;
        if (!(hr instanceof BlockHitResult bhr) || hr.getType() != HitResult.Type.BLOCK) return;

        if (!(mc.world.getBlockEntity(bhr.getBlockPos()) instanceof SuperCookerBlockEntity be)) return;
        if (!be.isCooking()) return;

        long now = mc.world.getTime();
        long target = be.getNextStirAt();
        int window = be.getStirWindow();

        long diff = target - now; // positive => time until
        boolean stirNow = Math.abs(diff) <= window;

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        int x = w / 2 - 60;
        int y = h - 70;

        String line1 = stirNow ? "STIR NOW!" : ("Stir in: " + String.format("%.1fs", diff / 20.0));
        String line2 = "Good: " + be.getGoodStirs() + "/" + be.getMinGoodStirsRequired() + "  Miss: " + be.getMisses();

        ctx.drawText(mc.textRenderer, Text.literal(line1), x, y, 0xFFFFFF, true);
        ctx.drawText(mc.textRenderer, Text.literal(line2), x, y + 10, 0xCCCCCC, true);

        // simple timing bar (no textures)
        int barW = 120;
        int barH = 6;
        int bx = w / 2 - barW / 2;
        int by = y + 24;

        ctx.fill(bx, by, bx + barW, by + barH, 0x80000000);

        // show “window” region around center
        // scale: show +/- 3 seconds around target
        float range = 60f; // ticks
        float center = 0.5f;
        float winHalf = Math.min(1f, window / range);

        int wx0 = (int)(bx + barW * (center - winHalf));
        int wx1 = (int)(bx + barW * (center + winHalf));
        ctx.fill(wx0, by, wx1, by + barH, 0x60FFFFFF);

        // marker showing current offset
        float t = 0.5f + (diff / range) * 0.5f;
        t = Math.max(0f, Math.min(1f, t));
        int mx = (int)(bx + barW * (1f - t));
        ctx.fill(mx, by - 1, mx + 1, by + barH + 1, 0xFFFFFFFF);
    }
}
