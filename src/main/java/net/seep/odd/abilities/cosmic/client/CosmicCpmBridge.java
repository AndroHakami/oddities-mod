package net.seep.odd.abilities.cosmic.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.seep.odd.abilities.overdrive.client.CpmHooks;

/**
 * VOID-style CPM bridge for Cosmic.
 * Animations (set both to "Holding last frame" in CPM):
 *  - "cosmic_stance" : play while charging; stop on release/force-release.
 *  - "cosmic_slash"  : play once on release; we auto-stop after a short hold so it can retrigger cleanly.
 */
public final class CosmicCpmBridge {
    private CosmicCpmBridge() {}

    private static boolean stanceActive = false;
    private static int slashTicksLeft = 0;      // countdown to stop "cosmic_slash"
    private static int defaultSlashHold = 10;   // ~0.5s (10 ticks) of held last frame

    /** Call once from your ClientModInitializer. */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) return;

            if (slashTicksLeft > 0) {
                if (--slashTicksLeft == 0) {
                    CpmHooks.stop("cosmic_slash");
                }
            }
        });
    }

    /** Begin stance loop. */
    public static void stanceStart() {
        if (stanceActive) return;
        stanceActive = true;
        CpmHooks.play("cosmic_stance");
    }

    /** End stance loop. */
    public static void stanceStop() {
        if (!stanceActive) return;
        stanceActive = false;
        CpmHooks.stop("cosmic_stance");
    }

    /** Play slash one-shot; keep last frame for `holdTicks` (or default). */
    public static void slash(int holdTicks) {
        if (holdTicks <= 0) holdTicks = defaultSlashHold;
        // restart cleanly
        CpmHooks.stop("cosmic_slash");
        CpmHooks.play("cosmic_slash");
        slashTicksLeft = holdTicks;
    }
}
