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
 *  Loop/hold-while-active:
 *   - "beam"   (plays while beam is ON, stops when OFF)
 */
public final class FairyCpmBridge {
    private FairyCpmBridge() {}

    private static boolean inited = false;

    private static int upHold = 0, downHold = 0, leftHold = 0, rightHold = 0;
    private static final int HOLD_TICKS = 6;

    // beam
    private static boolean beamActive = false;
    private static long lastBeamWorldTime = -1;

    public static void init() {
        if (inited) return;
        inited = true;

        // Beam ON/OFF from server
        ClientPlayNetworking.registerGlobalReceiver(FairyPower.S2C_CPM_BEAM, (client, handler, buf, resp) -> {
            boolean on = buf.readBoolean();
            client.execute(() -> {
                // if we swapped worlds/powers and packets are weird, still safe
                if (client.player == null || client.world == null) {
                    stopBeam();
                    return;
                }

                lastBeamWorldTime = client.world.getTime();

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

            // Failsafe: if beamActive but packets stopped (power swapped), stop after ~1s
            if (beamActive) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.world == null) { stopBeam(); return; }
                long now = mc.world.getTime();
                if (lastBeamWorldTime >= 0 && (now - lastBeamWorldTime) > 20) {
                    stopBeam();
                }
            }
        });
    }

    private static void startBeam() {
        if (beamActive) return;
        beamActive = true;
        CpmHooks.stop("beam");
        CpmHooks.play("beam"); // set this animation to loop (or hold) in CPM
    }

    private static void stopBeam() {
        beamActive = false;
        CpmHooks.stop("beam");
        lastBeamWorldTime = -1;
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