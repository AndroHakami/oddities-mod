package net.seep.odd.abilities.overdrive.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public final class OverdriveClientState {
    private OverdriveClientState() {}

    // set by S2C packet
    private static volatile int modeOrdinal = 0; // 0=NORMAL, 1=ENERGIZED, 2=OVERDRIVE
    public static void setModeOrdinal(int ord) { modeOrdinal = ord; }

    private static boolean playing = false;

    /** Call once from your client init. */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                if (playing) { CpmBridge.stopGesture("overdrive_run"); playing = false; }
                return;
            }

            boolean overdrive = (modeOrdinal == 2);
            if (!overdrive) {
                if (playing) { CpmBridge.stopGesture("overdrive_run"); playing = false; }
                return;
            }

            // speed check (horizontal). No onGround requirement – Overdrive gives jump/speed.
            Vec3d v = client.player.getVelocity();
            double speed = Math.hypot(v.x, v.z);

            boolean shouldPlay = speed > 0.05; // tweak threshold as you like
            if (shouldPlay && !playing) {
                CpmBridge.playGestureLoop("overdrive_run");
                playing = true;
            } else if (!shouldPlay && playing) {
                CpmBridge.stopGesture("overdrive_run");
                playing = false;
            }
        });
    }
}
