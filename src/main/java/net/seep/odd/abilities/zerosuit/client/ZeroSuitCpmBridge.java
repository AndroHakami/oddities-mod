package net.seep.odd.abilities.zerosuit.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
// Your project-wide CPM helper:
import net.seep.odd.abilities.overdrive.client.CpmHooks;

/**
 * Client CPM bridge for Zero Suit power.
 * Expected CPM animation names (set to "Hold last frame" when needed):
 *  - "force_stance"  : loop while in Force stance
 *  - "force_push"    : one-shot push
 *  - "force_pull"    : one-shot pull
 *  - "zero_charge"   : loop while charging blast
 *  - "zero_blast"    : one-shot blast fire
 */
public final class ZeroSuitCpmBridge {
    private ZeroSuitCpmBridge() {}

    private static boolean stanceActive = false;
    private static boolean chargeActive = false;

    private static int blastHoldTicks = 0; // short hold on "zero_blast" last frame
    private static final int BLAST_HOLD_DEFAULT = 10;

    /** Optional: if you want per-tick cleanup for one-shots. */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (blastHoldTicks > 0 && --blastHoldTicks == 0) {
                CpmHooks.stop("zero_blast");
            }
        });
    }

    public static void playStance() {
        if (!stanceActive) {
            stanceActive = true;
            CpmHooks.play("force_stance");
        }
    }
    public static void stopStance() {
        if (stanceActive) {
            stanceActive = false;
            CpmHooks.stop("force_stance");
        }
    }

    public static void playForcePush() {
        CpmHooks.stop("force_push");
        CpmHooks.play("force_push");
    }
    public static void playForcePull() {
        CpmHooks.stop("force_pull");
        CpmHooks.play("force_pull");
    }

    public static void playBlastCharge() {
        if (!chargeActive) {
            chargeActive = true;
            CpmHooks.play("zero_charge");
        }
    }
    public static void stopBlastCharge() {
        if (chargeActive) {
            chargeActive = false;
            CpmHooks.stop("zero_charge");
        }
    }

    public static void playBlastFire() {
        CpmHooks.stop("zero_blast");
        CpmHooks.play("zero_blast");
        blastHoldTicks = BLAST_HOLD_DEFAULT;
    }
}
