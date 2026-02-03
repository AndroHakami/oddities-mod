package net.seep.odd.abilities.chef.client;

import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.seep.odd.abilities.chef.client.screen.SuperCookerFridgeScreen;
import net.seep.odd.abilities.chef.client.screen.SuperCookerFuelScreen;
import net.seep.odd.abilities.chef.net.ChefNet;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.block.supercooker.client.SuperCookerRenderer;
import net.seep.odd.screen.ModScreenHandlers;

public final class ChefClient {
    private ChefClient() {}

    public static void init() {
        // Packet receiver for stir animation
        ChefNet.initClient();

        // GeckoLib renderer for the block entity
        BlockEntityRendererRegistry.register(ModBlocks.SUPER_COOKER_BE, ctx -> new SuperCookerRenderer());

        // Screens for the bottom (fuel) and middle (fridge)
        HandledScreens.register(ModScreenHandlers.SUPER_COOKER_FUEL, SuperCookerFuelScreen::new);
        HandledScreens.register(ModScreenHandlers.SUPER_COOKER_FRIDGE, SuperCookerFridgeScreen::new);
    }
}
