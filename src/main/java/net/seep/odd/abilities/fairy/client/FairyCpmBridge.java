package net.seep.odd.abilities.fairy.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.seep.odd.abilities.overdrive.client.CpmHooks;
import net.seep.odd.abilities.power.FairyPower;

/**
 * Client CPM bridge for Fairy power.
 *
 * Expected CPM animation names:
 *  One-shots:
 *   - "fairy_up"
 *   - "fairy_down"
 *   - "fairy_left"
 *   - "fairy_right"
 *
 *  Hold-while-active:
 *   - "beam"
 */
public final class FairyCpmBridge {
    private FairyCpmBridge() {}

    private static boolean inited = false;

    private static int upHold = 0, downHold = 0, leftHold = 0, rightHold = 0;
    private static final int HOLD_TICKS = 6;

    private static boolean beamActive = false;

    public static void init() {
        if (inited) return;
        inited = true;

        // Beam ON/OFF from server
        ClientPlayNetworking.registerGlobalReceiver(FairyPower.S2C_CPM_BEAM, (client, handler, buf, resp) -> {
            boolean on = buf.readBoolean();
            client.execute(() -> {
                if (client.player == null || client.world == null) {
                    stopBeam();
                    return;
                }

                if (on) startBeam();
                else stopBeam();
            });
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stopBeam());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> stopBeam());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (upHold > 0 && --upHold == 0) CpmHooks.stop("fairy_up");
            if (downHold > 0 && --downHold == 0) CpmHooks.stop("fairy_down");
            if (leftHold > 0 && --leftHold == 0) CpmHooks.stop("fairy_left");
            if (rightHold > 0 && --rightHold == 0) CpmHooks.stop("fairy_right");

            if (beamActive) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.world == null || mc.player == null) {
                    stopBeam();
                }
            }
        });
    }

    private static void startBeam() {
        if (beamActive) return;
        beamActive = true;

        // beam should fully own the pose while active
        CpmHooks.stop("fairy_up");
        CpmHooks.stop("fairy_down");
        CpmHooks.stop("fairy_left");
        CpmHooks.stop("fairy_right");
        upHold = downHold = leftHold = rightHold = 0;

        CpmHooks.stop("beam");
        CpmHooks.play("beam");
    }

    private static void stopBeam() {
        beamActive = false;
        CpmHooks.stop("beam");
    }

    public static void playDir(byte dir) {
        if (beamActive) return;

        switch (dir) {
            case 0 -> playUp();
            case 1 -> playDown();
            case 2 -> playLeft();
            case 3 -> playRight();
            default -> {}
        }
    }

    public static void playUp() {
        if (beamActive) return;
        CpmHooks.stop("fairy_up");
        CpmHooks.play("fairy_up");
        upHold = HOLD_TICKS;
    }

    public static void playDown() {
        if (beamActive) return;
        CpmHooks.stop("fairy_down");
        CpmHooks.play("fairy_down");
        downHold = HOLD_TICKS;
    }

    public static void playLeft() {
        if (beamActive) return;
        CpmHooks.stop("fairy_left");
        CpmHooks.play("fairy_left");
        leftHold = HOLD_TICKS;
    }

    public static void playRight() {
        if (beamActive) return;
        CpmHooks.stop("fairy_right");
        CpmHooks.play("fairy_right");
        rightHold = HOLD_TICKS;
    }
}
