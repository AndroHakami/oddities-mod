package net.seep.odd.shop.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

public final class ModScreenHandlers {

    public static ScreenHandlerType<DabloonsMachineScreenHandler> DABLOONS_MACHINE;

    public static void register() {
        DABLOONS_MACHINE = Registry.register(
                Registries.SCREEN_HANDLER,
                new Identifier(Oddities.MOD_ID, "dabloons_machine"),
                new ExtendedScreenHandlerType<>(DabloonsMachineScreenHandler::new)
        );
    }

    private ModScreenHandlers() {}
}
