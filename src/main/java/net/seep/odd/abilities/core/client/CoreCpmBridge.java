package net.seep.odd.abilities.core.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.seep.odd.abilities.overdrive.client.CpmHooks;

public final class CoreCpmBridge {
    private CoreCpmBridge() {}

    private static int passiveSpinTicksLeft = 0;
    private static int shortSpinTicksLeft = 0;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) return;

            if (passiveSpinTicksLeft > 0 && --passiveSpinTicksLeft == 0) {
                CpmHooks.stop("core_spin");
            }
            if (shortSpinTicksLeft > 0 && --shortSpinTicksLeft == 0) {
                CpmHooks.stop("core_short_spin");
            }
        });
    }

    public static void playPassiveSpin(int holdTicks) {
        if (holdTicks <= 0) holdTicks = 65;
        CpmHooks.stop("core_spin");
        CpmHooks.play("core_spin");
        passiveSpinTicksLeft = holdTicks;
    }

    public static void playShortSpin(int holdTicks) {
        if (holdTicks <= 0) holdTicks = 24;
        CpmHooks.stop("core_short_spin");
        CpmHooks.play("core_short_spin");
        shortSpinTicksLeft = holdTicks;
    }

    public static void stopAll() {
        passiveSpinTicksLeft = 0;
        shortSpinTicksLeft = 0;
        CpmHooks.stop("core_spin");
        CpmHooks.stop("core_short_spin");
    }
}
