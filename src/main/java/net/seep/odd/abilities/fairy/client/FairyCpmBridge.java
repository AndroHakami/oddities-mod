package net.seep.odd.abilities.fairy.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.seep.odd.abilities.overdrive.client.CpmHooks;

/**
 * Client CPM bridge for Fairy power.
 *
 * Expected CPM animation names (one-shots):
 *  - "fairy_up"
 *  - "fairy_down"
 *  - "fairy_left"
 *  - "fairy_right"
 *
 * If you set any of these to "Hold last frame", we auto-stop them after a few ticks.
 */
public final class FairyCpmBridge {
    private FairyCpmBridge() {}

    private static boolean inited = false;

    private static int upHold = 0, downHold = 0, leftHold = 0, rightHold = 0;
    private static final int HOLD_TICKS = 6;

    public static void init() {
        if (inited) return;
        inited = true;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (upHold > 0 && --upHold == 0) CpmHooks.stop("fairy_up");
            if (downHold > 0 && --downHold == 0) CpmHooks.stop("fairy_down");
            if (leftHold > 0 && --leftHold == 0) CpmHooks.stop("fairy_left");
            if (rightHold > 0 && --rightHold == 0) CpmHooks.stop("fairy_right");
        });
    }

    public static void playDir(byte dir) {
        switch (dir) {
            case 0 -> playUp();
            case 1 -> playDown();
            case 2 -> playLeft();
            case 3 -> playRight();
            default -> {}
        }
    }

    public static void playUp() {
        CpmHooks.stop("fairy_up");
        CpmHooks.play("fairy_up");
        upHold = HOLD_TICKS;
    }

    public static void playDown() {
        CpmHooks.stop("fairy_down");
        CpmHooks.play("fairy_down");
        downHold = HOLD_TICKS;
    }

    public static void playLeft() {
        CpmHooks.stop("fairy_left");
        CpmHooks.play("fairy_left");
        leftHold = HOLD_TICKS;
    }

    public static void playRight() {
        CpmHooks.stop("fairy_right");
        CpmHooks.play("fairy_right");
        rightHold = HOLD_TICKS;
    }
}
