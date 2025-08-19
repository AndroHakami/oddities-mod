package net.seep.odd.abilities.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.seep.odd.abilities.net.UmbraNet;

/** Sends HOLD states for push (LMB) / pull (RMB) while Umbra is active power. */
public final class AstralClientController {
    private AstralClientController(){}

    private static int lastMask = 0;
    private static int tick = 0;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tick++;

            if (!"umbra_soul".equals(ClientPowerHolder.get())) {
                lastMask = 0;
                return;
            }

            var mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) return;

            boolean push = mc.options.attackKey.isPressed(); // LMB
            boolean pull = mc.options.useKey.isPressed();    // RMB

            int mask = 0;
            if (push) mask |= 1; // UmbraSoulPower.ASTRAL_MASK_PUSH
            if (pull) mask |= 2; // UmbraSoulPower.ASTRAL_MASK_PULL

            // send on change, and keep-alive every 4 ticks while holding
            if (mask != lastMask || (mask != 0 && (tick & 3) == 0)) {
                UmbraNet.clientSendAstralInput(mask);
                lastMask = mask;
            }
        });
    }
}
