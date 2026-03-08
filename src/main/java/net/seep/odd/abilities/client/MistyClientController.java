// FILE: src/main/java/net/seep/odd/abilities/client/MistyClientController.java
package net.seep.odd.abilities.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.seep.odd.abilities.net.MistyNet;
import net.seep.odd.abilities.power.MistyVeilPower;

@Environment(EnvType.CLIENT)
public final class MistyClientController {
    private static int lastMask = 0;
    private static int stale    = 0;

    // keep last sent movement too, so we don't spam packets
    private static float lastIx = 0f;
    private static float lastIz = 0f;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || client.isPaused()) return;
            if (client.player == null) return;

            String id = ClientPowerHolder.get();
            if (!"misty_veil".equals(id)) {
                lastMask = 0;
                stale = 0;
                lastIx = lastIz = 0f;
                return;
            }

            var opts = client.options;

            // movement intent (player-space)
            int f = (opts.forwardKey.isPressed() ? 1 : 0) + (opts.backKey.isPressed() ? -1 : 0);
            int s = (opts.rightKey.isPressed()  ? 1 : 0) + (opts.leftKey.isPressed() ? -1 : 0);

            float ix = 0f, iz = 0f;
            if (f != 0 || s != 0) {
                double len = Math.sqrt((double)(f * f + s * s));
                ix = (float)(s / len);  // strafe X
                iz = (float)(f / len);  // forward Z
            }

            boolean held    = opts.jumpKey.isPressed();
            boolean pressed = opts.jumpKey.wasPressed();

            int mask = 0;
            if (held)    mask |= MistyVeilPower.MASK_JUMP_HELD;
            if (pressed) mask |= MistyVeilPower.MASK_JUMP_PRESSED;

            // Send combined packet when something changed, otherwise keep-alive every ~8 ticks.
            stale++;
            boolean intentChanged = (Math.abs(ix - lastIx) > 0.0001f) || (Math.abs(iz - lastIz) > 0.0001f);
            boolean maskChanged   = (mask != lastMask);

            if (maskChanged || intentChanged || stale >= 8) {
                MistyNet.sendInput(mask, ix, iz); // ✅ single authoritative packet
                lastMask = mask;
                lastIx = ix;
                lastIz = iz;
                stale = 0;
            }
        });
    }

    private MistyClientController() {}
}