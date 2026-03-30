package net.seep.odd.block.rps_machine.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

public final class RpsMachineScreenHandlers {
    private RpsMachineScreenHandlers() {}

    public static final ScreenHandlerType<RpsMachineScreenHandler> RPS_MACHINE =
            Registry.register(
                    Registries.SCREEN_HANDLER,
                    new Identifier(Oddities.MOD_ID, "rps_machine"),
                    new ExtendedScreenHandlerType<>(RpsMachineScreenHandler::new)
            );

    public static void register() {
        Oddities.LOGGER.info("Registering RPS machine screen handlers for " + Oddities.MOD_ID);
    }
}