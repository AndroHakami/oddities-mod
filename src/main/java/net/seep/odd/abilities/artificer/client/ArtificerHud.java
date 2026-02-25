// FILE: src/main/java/net/seep/odd/abilities/artificer/client/ArtificerHud.java
package net.seep.odd.abilities.artificer.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.abilities.artificer.EssenceColors;
import net.seep.odd.abilities.artificer.EssenceStorage;
import net.seep.odd.abilities.artificer.EssenceType;
import org.joml.Vector3f;

public final class ArtificerHud implements HudRenderCallback {

    public static void register() { HudRenderCallback.EVENT.register(new ArtificerHud()); }

    private static final Identifier HUD_FRAME =
            new Identifier("odd", "textures/gui/vacuum_storage_gui.png");
    private static final Identifier HUD_BG =
            new Identifier("odd", "textures/gui/vacuum_storage_bg.png");

    private static final int TEX_W = 100;
    private static final int TEX_H = 50;

    private static final int SLOT_Y_TOP = 11;
    private static final int SLOT_Y_BOT = 44;

    private static final int[] SLOT_X0 = {12, 26, 41, 54, 69, 83};
    private static final int[] SLOT_X1 = {16, 30, 45, 58, 73, 87};

    private static final EssenceType[] ORDER = new EssenceType[] {
            EssenceType.GAIA,
            EssenceType.COLD,
            EssenceType.HOT,
            EssenceType.LIGHT,
            EssenceType.LIFE,
            EssenceType.DEATH
    };

    // ✅ percent text scale (smaller)
    private static final float TEXT_SCALE = 0.65f;

    @Override
    public void onHudRender(DrawContext ctx, float tickDelta) {
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        ItemStack held = mc.player.getMainHandStack().isEmpty()
                ? mc.player.getOffHandStack()
                : mc.player.getMainHandStack();
        if (held.isEmpty()) return;

        if (!(held.getItem() instanceof net.seep.odd.abilities.artificer.item.ArtificerVacuumItem)) return;

        int cap = EssenceStorage.getCapacity(held);
        if (cap <= 0) cap = 1;

        int x = 10;
        int y = mc.getWindow().getScaledHeight() - TEX_H - 10;

        // 1) background
        ctx.drawTexture(HUD_BG, x, y, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);

        // 2) bars + % labels
        int slotH = (SLOT_Y_BOT - SLOT_Y_TOP + 1);

        for (int i = 0; i < ORDER.length && i < SLOT_X0.length; i++) {
            EssenceType e = ORDER[i];
            int have = EssenceStorage.get(held, e);

            float f = MathHelper.clamp(have / (float) cap, 0f, 1f);
            int pct = MathHelper.clamp(Math.round(f * 100f), 0, 100);

            int sx0 = x + SLOT_X0[i];
            int sx1 = x + SLOT_X1[i] + 1; // exclusive end

            int fillH = Math.max(0, Math.round(slotH * f));
            if (fillH > 0) {
                int sy1 = y + SLOT_Y_BOT + 1;
                int sy0 = sy1 - fillH;

                Vector3f c = EssenceColors.start(e);
                int base = argbFrom(c, 0.88f);
                int hi   = argbFrom(tintTowardWhite(c, 0.28f), 0.92f);

                ctx.fill(sx0, sy0, sx1, sy1, base);

                int hiH = Math.min(2, fillH);
                ctx.fill(sx0, sy0, sx1, sy0 + hiH, hi);
            }

            // ✅ smaller % text using matrix scaling
            String txt = pct + "%";

            int cx = (sx0 + (sx1 - 1)) / 2;
            int ty = y + (SLOT_Y_TOP - 9); // baseline position before scaling

            // compute scaled width
            int tw = mc.textRenderer.getWidth(txt);
            float scaledW = tw * TEXT_SCALE;

            // center it
            float tx = cx - (scaledW / 2f);

            ctx.getMatrices().push();
            ctx.getMatrices().translate(tx, ty, 0);
            ctx.getMatrices().scale(TEXT_SCALE, TEXT_SCALE, 1.0f);
            ctx.drawText(mc.textRenderer, txt, 0, 0, 0xFFFFFFFF, true);
            ctx.getMatrices().pop();
        }

        // 3) frame
        ctx.drawTexture(HUD_FRAME, x, y, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);
    }

    private static int argbFrom(Vector3f rgb01, float alpha01) {
        float a = MathHelper.clamp(alpha01, 0f, 1f);
        float r = MathHelper.clamp(rgb01.x, 0f, 1f);
        float g = MathHelper.clamp(rgb01.y, 0f, 1f);
        float b = MathHelper.clamp(rgb01.z, 0f, 1f);

        int A = (int)(a * 255f) & 0xFF;
        int R = (int)(r * 255f) & 0xFF;
        int G = (int)(g * 255f) & 0xFF;
        int B = (int)(b * 255f) & 0xFF;

        return (A << 24) | (R << 16) | (G << 8) | B;
    }

    private static Vector3f tintTowardWhite(Vector3f c, float amt) {
        amt = MathHelper.clamp(amt, 0f, 1f);
        return new Vector3f(
                c.x + (1f - c.x) * amt,
                c.y + (1f - c.y) * amt,
                c.z + (1f - c.z) * amt
        );
    }
}