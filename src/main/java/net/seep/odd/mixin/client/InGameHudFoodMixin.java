package net.seep.odd.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.seep.odd.abilities.vampire.client.VampireClientFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudFoodMixin {

    /**
     * Works across versions where hunger rendering is inlined (no renderFood method).
     * We draw AFTER vanilla status bars, then cover the hunger area and draw Blood.
     */
    @Inject(method = "renderStatusBars", at = @At("TAIL"))
    private void odd$vampireBloodInHungerSlot(DrawContext context, CallbackInfo ci) {
        if (!VampireClientFlag.hasVampire()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        PlayerEntity player = mc.player;

        // Vanilla hunger anchor (right side of hotbar)
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        int right = sw / 2 + 91;
        int y = sh - 39;

        // When riding a living entity, vanilla shifts bars up by a row.
        if (player.hasVehicle() && player.getVehicle() instanceof LivingEntity) {
            y -= 10;
        }

        int x = right - 81; // hunger row width

        // Always hide hunger visuals for vampires (even if HUD bar toggled off)
        coverArea(context, x, y, 81, 9);

        // Draw blood bar (optional toggle)
        if (VampireClientFlag.showHudBar()) {
            drawBloodBar(context, x, y, 81, 9, VampireClientFlag.pct());
        }
    }

    private static void coverArea(DrawContext context, int x, int y, int w, int h) {
        // Fully opaque cover so hunger icons cannot show through.
        context.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF000000);
    }

    private static void drawBloodBar(DrawContext context, int x, int y, int w, int h, float pct) {
        pct = Math.max(0f, Math.min(1f, pct));
        int fillW = Math.max(0, Math.min(w, (int)(pct * w)));

        // border
        int border = 0xFF3A0000;
        context.fill(x - 1, y - 1, x + w + 1, y, border);
        context.fill(x - 1, y + h, x + w + 1, y + h + 1, border);
        context.fill(x - 1, y, x, y + h, border);
        context.fill(x + w, y, x + w + 1, y + h, border);

        // background
        context.fill(x, y, x + w, y + h, 0xFF120001);

        // fill
        if (fillW > 0) {
            context.fill(x, y, x + fillW, y + h, 0xFF8B0D19);
        }

        // tiny highlight/shade
        context.fill(x, y, x + w, y + 1, 0x22FFFFFF);
        context.fill(x, y + h - 1, x + w, y + h, 0x22000000);
    }
}
