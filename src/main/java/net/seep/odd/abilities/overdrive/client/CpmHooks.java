package net.seep.odd.abilities.overdrive.client;

import net.seep.odd.compat.cpm.OddCpmPlugin;

public final class CpmHooks {
    private CpmHooks() {}

    public static void play(String anim) {
        if (OddCpmPlugin.CLIENT != null) OddCpmPlugin.CLIENT.playAnimation(anim, 1);
    }

    /** Use for "Layer" type clips that expect 0..255 weight. */
    public static void play(String anim, int value0to255) {
        if (OddCpmPlugin.CLIENT != null) OddCpmPlugin.CLIENT.playAnimation(anim, value0to255);
    }

    public static void stop(String anim) {
        if (OddCpmPlugin.CLIENT != null) OddCpmPlugin.CLIENT.playAnimation(anim, 0);
    }
}