package net.seep.odd.abilities.vampire;

import net.minecraft.server.network.ServerPlayerEntity;
import net.seep.odd.abilities.overdrive.client.CpmHooks;

/**
 * CPM gesture hooks for Vampire.
 * Wire these gestures in your CPM model:
 *  - "blood_suck" looping while sucking (or just play once)
 */
public final class CpmVampireHooks {
    private CpmVampireHooks() {}

    public static void onStartBloodSuck(ServerPlayerEntity p) {
        // If your CpmHooks is global-only, this still matches your pattern.
        CpmHooks.play("blood_suck");
    }

    public static void onStopBloodSuck(ServerPlayerEntity p) {
        // If you have a stop method, call it. Otherwise leave empty.
        CpmHooks.stop("blood_suck");
    }
}
