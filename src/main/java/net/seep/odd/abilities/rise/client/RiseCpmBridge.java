package net.seep.odd.abilities.rise.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.seep.odd.abilities.overdrive.client.CpmHooks;

/**
 * Client CPM bridge for Rise power.
 *
 * Expected CPM one-shot animation:
 *  - "rise"
 *
 * Animation length: 1.3s (26 ticks)
 * We auto-stop so it never gets stuck.
 */
@Environment(EnvType.CLIENT)
public final class RiseCpmBridge {
    private RiseCpmBridge() {}

    private static boolean inited = false;
    private static int hold = 0;

    public static void init() {
        if (inited) return;
        inited = true;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (hold > 0 && --hold == 0) CpmHooks.stop("rise");
        });
    }

    public static void playRise(int animTicks) {
        CpmHooks.stop("rise");
        CpmHooks.play("rise");
        hold = Math.max(1, animTicks);
    }

    public static void cancelRise() {
        hold = 0;
        CpmHooks.stop("rise");
    }
}
