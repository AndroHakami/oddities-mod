package net.seep.odd.screen;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.block.supercooker.screen.SuperCookerFridgeScreenHandler;
import net.seep.odd.block.supercooker.screen.SuperCookerFuelScreenHandler;

public final class ModScreenHandlers {
    private ModScreenHandlers() {}

    public static final ScreenHandlerType<SuperCookerFuelScreenHandler> SUPER_COOKER_FUEL =
            Registry.register(Registries.SCREEN_HANDLER,
                    new Identifier(Oddities.MOD_ID, "super_cooker_fuel"),
                    new ScreenHandlerType<>(SuperCookerFuelScreenHandler::new, FeatureSet.empty()));

    public static final ScreenHandlerType<SuperCookerFridgeScreenHandler> SUPER_COOKER_FRIDGE =
            Registry.register(Registries.SCREEN_HANDLER,
                    new Identifier(Oddities.MOD_ID, "super_cooker_fridge"),
                    new ScreenHandlerType<>(SuperCookerFridgeScreenHandler::new, FeatureSet.empty()));

    public static void init() {}
}
