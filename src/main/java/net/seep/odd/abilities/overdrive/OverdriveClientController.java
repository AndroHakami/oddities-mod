package net.seep.odd.abilities.overdrive;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

/**
 * Client-only QoL:
 * - Detects right-click hold with an EMPTY main hand to charge Titan Breaker.
 *   Sends start/release edges to the server. Server validates power/state.
 *
 * Ability buttons (primary/secondary) are already handled by your existing system
 * by calling Power#activate / #activateSecondary server-side, so nothing to do here.
 */
public final class OverdriveClientController {
    private OverdriveClientController() {}

    private static boolean wasUseDown = false;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var p = client.player;
            if (p == null || client.world == null) return;
            

            // Only react when the main hand is empty (prevents fighting other right-click uses).
            ItemStack main = p.getMainHandStack();
            boolean emptyMain = main.isEmpty();

            boolean useDown = client.options.useKey.isPressed();

            if (emptyMain) {
                if (useDown && !wasUseDown) {
                    // edge: pressed -> begin charging
                    OverdriveNet.sendStartPunch();
                } else if (!useDown && wasUseDown) {
                    // edge: released -> fire the punch
                    OverdriveNet.sendReleasePunch();
                }
            }

            wasUseDown = useDown;
        });
    }
}
