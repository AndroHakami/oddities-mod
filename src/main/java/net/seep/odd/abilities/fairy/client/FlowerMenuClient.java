// src/main/java/net/seep/odd/abilities/fairy/client/FlowerMenuClient.java
package net.seep.odd.abilities.fairy.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.seep.odd.abilities.fairy.FlowerMenu;

/**
 * Client hook (no ClientModInitializer here).
 * Call FlowerMenuClient.init() once from your existing OdditiesClient.
 */
public final class FlowerMenuClient {
    private FlowerMenuClient() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(FlowerMenu.S2C_OPEN, (client, handler, buf, resp) ->
                client.execute(() -> MinecraftClient.getInstance().setScreen(new ManageFlowersScreen()))
        );
    }
}
