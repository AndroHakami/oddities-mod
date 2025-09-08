// net/seep/odd/abilities/overdrive/client/OverdriveCpmBridge.java
package net.seep.odd.abilities.overdrive.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.seep.odd.abilities.client.ClientPowerHolder;
import net.seep.odd.abilities.overdrive.OverdriveHudOverlay;

/**
 * Client-only CPM controller for Overdrive cosmetics.
 * - RUN plays only while in OVERDRIVE and moving horizontally.
 * - JUMP plays when leaving ground; stops on landing.
 * - CHARGE plays when you start holding the punch; stops on release/cancel.
 * - PUNCH plays on release and holds for PUNCH_HOLD_TICKS, then stops.
 *   While PUNCH is active, RUN is suppressed so clips don't fight.
 */
public final class OverdriveCpmBridge {
    private static final String RUN_ANIM     = "overdrive_run";
    private static final String JUMP_ANIM    = "overdrive_jump";
    private static final String CHARGE_ANIM  = "overdrive_charge";
    private static final String PUNCH_ANIM   = "overdrive_punch";

    // tune to match your CPM punch clip length (in ticks @ 20 TPS)
    private static final int PUNCH_HOLD_TICKS = 20;      // ~1.0s
    private static final int LANDING_GRACE_TICKS = 4;    // after landing, let punch finish a few ticks

    private static boolean running = false;
    private static boolean lastOnGround = true;
    private static boolean chargePlaying = false;
    private static int punchTicksLeft = 0;
    private static int suppressRunTicks = 0;

    private OverdriveCpmBridge() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientPlayerEntity p = MinecraftClient.getInstance().player;
            if (p == null) return;

            if (!"overdrive".equals(ClientPowerHolder.get())) {
                // Lost the power → hard stop everything
                stopRun(); stopJump(); stopCharge(); stopPunch();
                lastOnGround = true;
                return;
            }

            int mode = OverdriveHudOverlay.getMode();
            boolean inOverdrive = (mode == 2);
            boolean inEnergizedOrOverdrive = (mode >= 1);

            // === RUN (client-local, only in OVERDRIVE) ===
            // suppressed while punch is active to avoid clip fighting
            boolean allowRun = inOverdrive && suppressRunTicks <= 0;
            double dx = p.getX() - p.prevX;
            double dz = p.getZ() - p.prevZ;
            boolean movingHoriz = (dx*dx + dz*dz) > 0.00002; // ~0.14 blocks/tick

            if (allowRun && movingHoriz) {
                if (!running) { CpmHooks.play(RUN_ANIM); running = true; }
            } else {
                stopRun();
            }

            // === JUMP (rise/land edges, OVERDRIVE only) ===
            boolean onGround = p.isOnGround();
            if (inOverdrive && lastOnGround && !onGround) {
                CpmHooks.play(JUMP_ANIM);
            }
            if (inOverdrive && !lastOnGround && onGround) {
                // landed
                CpmHooks.stop(JUMP_ANIM);
                // if punch is still active, shorten its tail so we don't linger
                if (punchTicksLeft > LANDING_GRACE_TICKS) punchTicksLeft = LANDING_GRACE_TICKS;
            }
            lastOnGround = onGround;

            // === Timers ===
            if (punchTicksLeft > 0 && --punchTicksLeft == 0) {
                CpmHooks.stop(PUNCH_ANIM);
            }
            if (suppressRunTicks > 0) suppressRunTicks--;

            // Safety: if you leave energized/overdrive while charging → stop charge anim
            if (!inEnergizedOrOverdrive && chargePlaying) stopCharge();
        });
    }

    /* ---------------- called from client send helpers ---------------- */

    /** Call this right after you send START_PUNCH (client→server). */
    public static void onLocalChargeStart() {
        if (!chargePlaying) {
            CpmHooks.play(CHARGE_ANIM);
            chargePlaying = true;
        }
        // optional but recommended: keep run from overlapping with charge
        stopRun();
    }

    /** Call this if you implement a client-side cancel path (optional). */
    public static void onLocalChargeCancel() {
        stopCharge();
    }

    /** Call this right after you send RELEASE_PUNCH (client→server). */
    public static void onLocalPunch() {
        stopCharge();               // swap charge -> punch
        CpmHooks.play(PUNCH_ANIM);  // if your clip is a "layer", call CpmHooks.play(PUNCH_ANIM, 255);
        punchTicksLeft = PUNCH_HOLD_TICKS;
        suppressRunTicks = PUNCH_HOLD_TICKS; // don't let run clip override punch window
    }

    /* ---------------- internal stops ---------------- */
    private static void stopRun() {
        if (running) { CpmHooks.stop(RUN_ANIM); running = false; }
    }
    private static void stopJump() { CpmHooks.stop(JUMP_ANIM); }
    private static void stopCharge() {
        if (chargePlaying) { CpmHooks.stop(CHARGE_ANIM); chargePlaying = false; }
    }
    private static void stopPunch() {
        if (punchTicksLeft > 0) { CpmHooks.stop(PUNCH_ANIM); punchTicksLeft = 0; }
    }
}
