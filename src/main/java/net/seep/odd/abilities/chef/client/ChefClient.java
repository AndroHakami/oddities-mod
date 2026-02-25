// src/main/java/net/seep/odd/abilities/chef/client/ChefClient.java
package net.seep.odd.abilities.chef.client;

import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.seep.odd.abilities.chef.net.ChefNet;
import net.seep.odd.abilities.chef.net.ChefFoodNet;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.block.supercooker.client.SuperCookerRenderer;

public final class ChefClient {
    private ChefClient() {}

    public static void init() {
        ChefNet.initClient();     // stir animation packet
        ChefFoodNet.initClient(); // ✅ dragon burrito triple jump
        ChefHud.init();           // timing UI

        BlockEntityRendererRegistry.register(
                ModBlocks.SUPER_COOKER_BE,
                SuperCookerRenderer::new
        );
    }
}