package net.seep.odd.abilities.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.power.Powers;

import java.util.ArrayList;
import java.util.List;

public final class AbilityHudOverlay {
    private AbilityHudOverlay() {}

    public static void register() {
        HudRenderCallback.EVENT.register(AbilityHudOverlay::render);
    }

    private static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.isPaused()) return;

        String powerId = ClientPowerHolder.get();
        var p = Powers.get(powerId);
        if (p == null) return;

        // Build visible slots based on the power's capability.
        String[] all = {"primary", "secondary", "third", "fourth"};
        List<String> slots = new ArrayList<>(4);
        for (String s : all) if (p.hasSlot(s)) slots.add(s);
        if (slots.isEmpty()) return;

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        // Layout
        final int size = 20;        // ~30% smaller than 32px
        final int pad = 20;         // gap between icons
        final int labelGap = 8;     // space below icon for key text

        // Hotbar metrics (vanilla)
        final int HOTBAR_WIDTH  = 182;
        final int HOTBAR_HEIGHT = 22;

        // Anchor just to the right of the hotbar
        int hotbarRight = (w / 2) + (HOTBAR_WIDTH / 2);
        int gapX = 8; // distance from hotbar
        int totalWidth = slots.size() * size + (slots.size() - 1) * pad;

        int x0 = hotbarRight + gapX;
        // If we'd go off-screen (ultrawide/small UI), clamp to screen-right with a small margin.
        int screenRightMargin = 8;
        if (x0 + totalWidth > w - screenRightMargin) {
            x0 = Math.max(screenRightMargin, w - screenRightMargin - totalWidth);
        }

        // Sit slightly above the hotbar instead of on top of it
        int y0 = h - HOTBAR_HEIGHT - 4 - size - labelGap;

        for (int i = 0; i < slots.size(); i++) {
            int x = x0 + i * (size + pad);
            drawSlot(ctx, x, y0, slots.get(i), size);
        }
    }

    private static void drawSlot(DrawContext ctx, int x, int y, String slot, int size) {
        var mc = MinecraftClient.getInstance();

        String powerId = ClientPowerHolder.get();
        var p = Powers.get(powerId);
        if (p == null) return;

        final int ICON_SRC = 28; // your PNGs are 32x32
        Identifier icon = p.iconTexture(slot);

        int totalCd = switch (slot) {
            case "primary"   -> (int) p.cooldownTicks();
            case "secondary" -> (int) p.secondaryCooldownTicks();
            case "third"     -> (int) p.thirdCooldownTicks();
            case "fourth"    -> (p instanceof AbilityHudOverlay.HasFourthCooldown hf) ? (int) hf.fourthCooldownTicks() : 0;
            default -> 0;
        };
        int remain = ClientCooldowns.get(slot);

        // Backplate
        ctx.fill(x - 2, y - 2, x + size + 2, y + size + 2, 0x66000000);

        // --- Proper scaling: draw the full 32x32 icon, scaled down to 'size' ---
        var m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0);
        float s = (float) size / (float) ICON_SRC; // e.g., 22/32
        m.scale(s, s, 1f);
        // draw at (0,0) at native 32x32; matrix handles scaling
        ctx.drawTexture(icon, 0, 0, 0, 0, ICON_SRC, ICON_SRC, ICON_SRC, ICON_SRC);
        m.pop();

        // Cooldown wipe (drawn over the scaled icon in screen-space)
        if (remain > 0 && totalCd > 0) {
            float ratio = Math.min(1f, remain / (float) totalCd);
            int cover = (int) (size * ratio);
            ctx.fill(x, y + (size - cover), x + size, y + size, 0x99000000);
        }

        // Cooldown number
        if (remain > 0) {
            String txt = (remain >= 20)
                    ? String.valueOf((int) Math.ceil(remain / 20.0))
                    : String.format("%.1f", remain / 20.0);
            int tw = mc.textRenderer.getWidth(txt);
            ctx.drawTextWithShadow(mc.textRenderer, txt, x + (size - tw) / 2, y + (size / 2) - 3, 0xEBC034);
        }

        // Key label under icon
        String key = AbilityKeybinds.boundKeyName(slot);
        int kw = mc.textRenderer.getWidth(key);
        int labelY = y + size + 2;
        int color = (remain > 0) ? 0x80FFFFFF : 0xE0FFFFFF;
        ctx.drawTextWithShadow(mc.textRenderer, key, x + (size - kw) / 2, labelY, color);
    }

    // Optional hooks if you later add third/fourth cooldowns
    public interface HasThirdCooldown { long thirdCooldownTicks(); }
    public interface HasFourthCooldown { long fourthCooldownTicks(); }
}
