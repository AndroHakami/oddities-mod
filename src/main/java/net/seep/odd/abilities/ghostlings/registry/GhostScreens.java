package net.seep.odd.abilities.ghostlings.registry;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.ghostlings.screen.GhostManageScreenHandler;

public final class GhostScreens {
    private GhostScreens() {}
    public static ScreenHandlerType<GhostManageScreenHandler> GHOST_MANAGE_HANDLER;

    public static void register() {
        if (GHOST_MANAGE_HANDLER == null) {
            GHOST_MANAGE_HANDLER = Registry.register(
                    Registries.SCREEN_HANDLER,
                    new Identifier("odd", "ghost_manage"),
                    new ExtendedScreenHandlerType<>(GhostManageScreenHandler::new)
            );
        }
    }
}
