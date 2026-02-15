// FILE: src/main/java/net/seep/odd/abilities/wizard/client/WizardManaHudClient.java
package net.seep.odd.abilities.wizard.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public final class WizardManaHudClient {
    private WizardManaHudClient() {}

    public static void register() {
        HudRenderCallback.EVENT.register(WizardManaHudClient::render);
    }

    private static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (!WizardClientState.hasWizard()) return;

        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();

        int w = 110;
        int h = 8;
        int x = sw / 2 - w / 2;
        int y = sh - 48;

        float mana = WizardClientState.mana();
        float max = Math.max(1f, WizardClientState.manaMax());
        float t = MathHelper.clamp(mana / max, 0f, 1f);

        // background
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xAA000000);
        // fill
        ctx.fill(x, y, x + (int)(w * t), y + h, 0xAA4E9BFF);

        // optional: tiny text
        // ctx.drawTextWithShadow(mc.textRenderer, "Mana " + (int)mana, x, y - 10, 0xFFFFFFFF);
    }
}
