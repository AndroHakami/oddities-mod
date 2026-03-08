package net.seep.odd.abilities.wizard.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public final class WizardManaHudClient {
    private WizardManaHudClient() {}

    // ✅ Put your overlay texture here (you will add the PNG)
    // Suggested file: resources/assets/odd/textures/gui/wizard/mana_frame.png
    private static final Identifier MANA_FRAME = new Identifier("odd", "textures/gui/wizard/mana_frame.png");

    // frame size (matches your art)
    private static final int FRAME_W = 128;
    private static final int FRAME_H = 18;

    // bar size
    private static final int BAR_W = 110;
    private static final int BAR_H = 8;

    // bar position inside frame
    private static final int BAR_INSET_X = 9;
    private static final int BAR_INSET_Y = 5;

    public static void register() {
        HudRenderCallback.EVENT.register(WizardManaHudClient::render);
    }

    private static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (!WizardClientState.hasWizard()) return;

        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();

        // ✅ moved a bit higher than before
        int xFrame = sw / 2 - FRAME_W / 2;
        int yFrame = sh - 70; // was ~48 area; now higher

        int xBar = xFrame + BAR_INSET_X;
        int yBar = yFrame + BAR_INSET_Y;

        float mana = WizardClientState.mana();
        float max  = Math.max(1f, WizardClientState.manaMax());
        float t    = MathHelper.clamp(mana / max, 0f, 1f);

        // background behind bar
        ctx.fill(xBar - 1, yBar - 1, xBar + BAR_W + 1, yBar + BAR_H + 1, 0xAA000000);

        // fill
        ctx.fill(xBar, yBar, xBar + (int)(BAR_W * t), yBar + BAR_H, 0xAA4E9BFF);

        // ✅ overlay frame (draw last so it sits on top)
        ctx.drawTexture(MANA_FRAME, xFrame, yFrame, 0, 0, FRAME_W, FRAME_H, FRAME_W, FRAME_H);
    }
}