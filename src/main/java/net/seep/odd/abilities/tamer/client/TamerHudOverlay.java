// net/seep/odd/abilities/tamer/client/TamerHudOverlay.java
package net.seep.odd.abilities.tamer.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.Window;
import net.minecraft.util.Identifier;

public final class TamerHudOverlay {
    private TamerHudOverlay() {}

    /** Call once from your client init. */
    public static void register() {
        HudRenderCallback.EVENT.register(TamerHudOverlay::render);
    }

    private static void render(DrawContext ctx, float tickDelta) {
        if (MinecraftClient.getInstance().player == null) return;

        // hide if stale
        long now = System.currentTimeMillis();
        if (now - TamerHudState.lastUpdate > 2500L) return;

        Window w = MinecraftClient.getInstance().getWindow();
        int right = w.getScaledWidth() - 10;
        int y = 10;

        // --- icon + title (right-aligned) ---
        final int iconSize = 16;
        int titleXRight = right;

        Identifier icon = TamerHudState.icon; // may be null
        if (icon != null) {
            int ix = right - iconSize;
            // u,v = 0,0 because the entire 16x16 is used
            ctx.drawTexture(icon, ix, y - 1, 0, 0, iconSize, iconSize, iconSize, iconSize);
            titleXRight -= (iconSize + 4);
        }

        var tr = MinecraftClient.getInstance().textRenderer;
        String title = TamerHudState.name + "  Lv." + TamerHudState.level;
        int tw = tr.getWidth(title);
        ctx.drawTextWithShadow(tr, title, titleXRight - tw, y, 0xFFFFFF);
        y += 12;

        // --- HP bar ---
        float hp = Math.max(0f, TamerHudState.hp);
        float mh = Math.max(1f, TamerHudState.maxHp);
        int bw = 110, bh = 6;
        int x = right - bw;
        int fillHp = (int) (bw * (hp / mh));

        ctx.fill(x - 1, y - 1, right + 1, y + bh + 1, 0xAA000000); // bg frame
        ctx.fill(x, y, x + fillHp, y + bh, 0xFF22CC22);            // hp fill
        ctx.drawTextWithShadow(tr, String.format("%.0f/%.0f", hp, mh), x + 2, y - 1, 0xFFFFFFFF);
        y += 10;

        // --- XP bar ---
        int need = Math.max(1, TamerHudState.next);   // XP needed to next level
        int have = Math.max(0, TamerHudState.exp);    // current total XP toward next
        int expProg = Math.min(have, need);
        int fillXp = (int) (bw * (expProg / (double) need));

        ctx.fill(x - 1, y - 1, right + 1, y + bh + 1, 0xAA000000); // bg frame
        ctx.fill(x, y, x + fillXp, y + bh, 0xFF22A9FF);            // xp fill
        ctx.drawTextWithShadow(tr, String.format("%d / %d XP", expProg, need), x + 2, y - 1, 0xFFFFFFFF);
    }
}
