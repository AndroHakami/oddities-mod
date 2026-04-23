package net.seep.odd.abilities.ghostlings.registry;

import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.ghostlings.screen.GhostManageScreenHandler;
import net.seep.odd.abilities.ghostlings.screen.courier.CourierPayScreenHandler;

/**
 * Compatibility holder for older code paths that still reference GhostScreens.
 *
 * Important: client handled-screen registration can happen after the common
 * screen handlers were registered, but before these static fields were copied.
 * So we resolve from the live registry by ID if needed instead of assuming
 * GhostRegistries already mirrored everything into here.
 */
public final class GhostScreens {
    private GhostScreens() {}

    public static ScreenHandlerType<GhostManageScreenHandler> GHOST_MANAGE_HANDLER;
    public static ScreenHandlerType<CourierPayScreenHandler> COURIER_PAY_HANDLER;

    @SuppressWarnings("unchecked")
    public static void register() {
        if (GHOST_MANAGE_HANDLER == null) {
            GHOST_MANAGE_HANDLER = GhostRegistries.GHOST_MANAGE_HANDLER;
            if (GHOST_MANAGE_HANDLER == null) {
                GHOST_MANAGE_HANDLER = (ScreenHandlerType<GhostManageScreenHandler>)
                        Registries.SCREEN_HANDLER.get(new Identifier("odd", "ghost_manage"));
            }
        }

        if (COURIER_PAY_HANDLER == null) {
            COURIER_PAY_HANDLER = GhostRegistries.COURIER_PAY_HANDLER;
            if (COURIER_PAY_HANDLER == null) {
                COURIER_PAY_HANDLER = (ScreenHandlerType<CourierPayScreenHandler>)
                        Registries.SCREEN_HANDLER.get(new Identifier("odd", "courier_pay"));
            }
        }
    }
}
