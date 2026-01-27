// src/main/java/net/seep/odd/abilities/net/client/UmbraAirSwimBoostNetClient.java
package net.seep.odd.abilities.astral;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.seep.odd.abilities.astral.OddAirSwim;
import net.seep.odd.abilities.astral.OddUmbraBoostInput;
import net.seep.odd.abilities.net.UmbraAirSwimBoostNet;

@Environment(EnvType.CLIENT)
public final class UmbraAirSwimBoostNetClient {
    private UmbraAirSwimBoostNetClient() {}

    private static boolean lastSent = false;

    public static void initClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            boolean airSwim = (client.player instanceof OddAirSwim a) && a.oddities$isAirSwim();

            // real “CTRL held” (sprint key held)
            boolean held = airSwim && client.options.sprintKey.isPressed();

            // update local immediately (smooth client feel)
            if (client.player instanceof OddUmbraBoostInput bi) {
                bi.oddities$setUmbraBoostHeld(held, client.player.age);
            }

            // only send when changes, and send a final false when leaving airswim
            if (held != lastSent) {
                ClientPlayNetworking.send(
                        UmbraAirSwimBoostNet.UMBRA_SWIM_BOOST,
                        UmbraAirSwimBoostNet.makeBuf(held)
                );
                lastSent = held;
            }

            if (!airSwim && lastSent) {
                // if we left airswim while boosting, ensure server clears it
                ClientPlayNetworking.send(
                        UmbraAirSwimBoostNet.UMBRA_SWIM_BOOST,
                        UmbraAirSwimBoostNet.makeBuf(false)
                );
                lastSent = false;
            }
        });
    }
}
