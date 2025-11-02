package net.seep.odd.abilities.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

import net.seep.odd.abilities.net.PowerNetworking;

@Environment(EnvType.CLIENT)
public final class AbilityHoldInput {
    private AbilityHoldInput(){}

    private static KeyBinding secondaryBind;
    private static boolean holding = false;
    private static int heldTicks = 0;

    /** Call this once from your client init, passing your existing SECONDARY keybinding. */
    public static void hook(KeyBinding secondaryKey) {
        secondaryBind = secondaryKey;
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
    }

    private static void tick(MinecraftClient mc) {
        if (secondaryBind == null || mc.player == null) return;

        boolean pressed = secondaryBind.isPressed();

        if (pressed && !holding) {
            holding = true;
            heldTicks = 0;
            PowerNetworking.sendHoldStart("secondary");
            // local HUD fade
            net.seep.odd.abilities.client.AbilityHudOverlay.ClientHeldState.set("secondary", true);
        }

        if (pressed && holding) {
            heldTicks++;
            // keep server in sync (lightweight varint). Every tick is fine.
            PowerNetworking.sendHoldTick("secondary", heldTicks);
        }

        if (!pressed && holding) {
            holding = false;
            PowerNetworking.sendHoldRelease("secondary", heldTicks, false);
            net.seep.odd.abilities.client.AbilityHudOverlay.ClientHeldState.set("secondary", false);
            heldTicks = 0;
        }
    }
}
