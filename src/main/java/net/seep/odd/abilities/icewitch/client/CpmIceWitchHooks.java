package net.seep.odd.abilities.icewitch.client;

import net.minecraft.server.network.ServerPlayerEntity;
import net.seep.odd.abilities.overdrive.client.CpmHooks;

/**
 * CPM (Custom Player Models) gesture hooks for the Ice Witch power.
 * No-ops by default so the mod compiles if CPM isn't present.
 * Wire these to your model:
 *  - "cast_spell" when secondary starts (1s windup).
 *  - "staff_fly"  looping while soar is enabled.
 */
public final class CpmIceWitchHooks {
    private CpmIceWitchHooks() {}

    public static void onCastSpell(ServerPlayerEntity p) {
        // Example (if you integrate CPM):
        CpmHooks.play("cast_spell");
    }

    public static void onStartSoar(ServerPlayerEntity p) {
        // CpmCompat.playGesture(p, "staff_fly", -1); // loop
    }

    public static void onStopSoar(ServerPlayerEntity p) {
        // CpmCompat.stopGesture(p, "staff_fly");
    }
}
