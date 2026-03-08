// src/main/java/net/seep/odd/abilities/supercharge/SuperHud.java
package net.seep.odd.abilities.supercharge;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

import net.seep.odd.abilities.supercharge.client.SuperChargeFx;

@Environment(EnvType.CLIENT)
public final class SuperHud {
    private SuperHud() {}

    private static boolean show;
    private static int cur;
    private static int max;

    public static void onHud(boolean s, int c, int m) {
        show = s;
        cur = c;
        max = m;

        float pct = (max <= 0) ? 0f : MathHelper.clamp(cur / (float) max, 0f, 1f);
        SuperChargeFx.setActive(show, pct);

        // ✅ Purely visual "blocking" pose: we locally put the player into using-item state.
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.player != null) {
            if (show) {
                // do NOT send gameplay packets; this is client-only visual state
                mc.player.setCurrentHand(Hand.MAIN_HAND);
            } else {
                mc.player.clearActiveItem();
            }
        }
    }

    public static void init() {
        SuperChargeFx.init();

        // keep the pose stable while charging (client-only)
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) return;
            if (!show) return;

            // If anything clears it, re-apply while charging
            if (!mc.player.isUsingItem()) {
                mc.player.setCurrentHand(Hand.MAIN_HAND);
            }
        });

        // reset on disconnect
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            show = false;
            cur = 0;
            max = 0;
            SuperChargeFx.setActive(false, 0f);

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.player != null) mc.player.clearActiveItem();
        });
    }

    // used by the client mixin to decide if we spoof BLOCK use action
    public static boolean isChargingVisual() {
        return show;
    }
}