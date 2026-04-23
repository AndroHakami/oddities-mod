package net.seep.odd.device.store.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

public final class StoreScreenHandlers {
    private StoreScreenHandlers() {}

    public static ScreenHandlerType<DabloonStoreScreenHandler> DABLOON_STORE;

    public static void register() {
        if (DABLOON_STORE != null) {
            return;
        }
        DABLOON_STORE = Registry.register(
                Registries.SCREEN_HANDLER,
                new Identifier(Oddities.MOD_ID, "dabloon_store"),
                new ExtendedScreenHandlerType<>(DabloonStoreScreenHandler::new)
        );
    }
}
