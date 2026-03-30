package net.seep.odd.abilities.sun.client;

import net.seep.odd.abilities.overdrive.client.CpmHooks;

/**
 * Cleanest way to get the requested spear/trident hold in this project without adding a player renderer mixin.
 * Make a CPM animation called "sun_hold" that matches the trident throw pose.
 */
public final class SunCpmBridge {
    private SunCpmBridge() {}

    private static boolean holding = false;

    public static void holdStart() {
        if (holding) return;
        holding = true;
        CpmHooks.play("sun_hold");
    }

    public static void holdStop() {
        if (!holding) return;
        holding = false;
        CpmHooks.stop("sun_hold");
    }
}
