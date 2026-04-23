package net.seep.odd.abilities.core.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public final class CorePassiveHudClient {
    private CorePassiveHudClient() {}

    private static boolean inited = false;
    private static int remainingTicks = 0;

    public static void init() {
        if (inited) return;
        inited = true;

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> remainingTicks = 0);

        HudRenderCallback.EVENT.register((DrawContext context, float tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null || client.options.hudHidden) return;
            if (remainingTicks <= 0) return;

            TextRenderer tr = client.textRenderer;
            String line = String.format("CORE RECHARGING %.1fs", remainingTicks / 20.0F);
            int sw = context.getScaledWindowWidth();
            int sh = context.getScaledWindowHeight();
            int x = (sw - tr.getWidth(line)) / 2;
            int y = sh - 72;

            context.drawTextWithShadow(tr, Text.literal(line), x, y, 0xFF6A6A);
        });
    }

    public static void setRemainingTicks(int ticks) {
        remainingTicks = Math.max(0, ticks);
    }
}
