package net.seep.odd.abilities.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.seep.odd.abilities.net.MistyNet;
import net.seep.odd.abilities.power.MistyVeilPower;

@Environment(EnvType.CLIENT)
public final class MistyClientController {
    private static int lastMask = 0;
    private static int stale    = 0;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.isPaused()) return;
            var p = client.player;
            if (p == null) return;

            String id = ClientPowerHolder.get();
            if (!"misty_veil".equals(id)) { lastMask = 0; return; }

            // Your ability key system – adjust if needed
            var opts = client.options;
            int f = (opts.forwardKey.isPressed() ? 1 : 0) + (opts.backKey.isPressed() ? -1 : 0);
            int s = (opts.rightKey.isPressed()  ? 1 : 0) + (opts.leftKey.isPressed() ? -1 : 0);

// normalize to [-1..1] vector
            float ix = 0f, iz = 0f;
            if (f != 0 || s != 0) {
                double len = Math.sqrt((double)(f*f + s*s));
                ix = (float)(s / len);  // strafe (player-space X)
                iz = (float)(f / len);  // forward (player-space Z)
            }

            boolean held    = opts.jumpKey.isPressed();
            boolean pressed = opts.jumpKey.wasPressed();

            int mask = 0;
            if (held)    mask |= MistyVeilPower.MASK_JUMP_HELD;
            if (pressed) mask |= MistyVeilPower.MASK_JUMP_PRESSED;

// NEW: send mask + ix + iz
            MistyNet.sendInput(mask, ix, iz);

            stale++;
            if (mask != lastMask || stale >= 8) {
                MistyNet.sendInput(mask);     // << use MistyNet now
                lastMask = mask;
                stale = 0;
            }
        });
    }

    private MistyClientController() {}
}
