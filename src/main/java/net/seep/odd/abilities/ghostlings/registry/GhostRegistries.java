package net.seep.odd.abilities.ghostlings.registry;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.ghostlings.entity.GhostlingEntity;
import net.seep.odd.abilities.ghostlings.screen.GhostDashboardScreenHandler;
import net.seep.odd.abilities.ghostlings.screen.GhostManageScreenHandler;
import net.seep.odd.abilities.ghostlings.screen.courier.CourierPayScreenHandler;

public final class GhostRegistries {
    private GhostRegistries() {}

    public static Identifier id(String path) {
        return new Identifier("odd", path);
    }



    public static final ScreenHandlerType<GhostManageScreenHandler> GHOST_MANAGE_HANDLER = Registry.register(
            Registries.SCREEN_HANDLER,
            id("ghost_manage"),
            new ExtendedScreenHandlerType<>(GhostManageScreenHandler::new)
    );

    public static final ScreenHandlerType<GhostDashboardScreenHandler> GHOST_DASH_HANDLER = Registry.register(
            Registries.SCREEN_HANDLER,
            id("ghost_dashboard"),
            new ExtendedScreenHandlerType<>(GhostDashboardScreenHandler::new)
    );

    public static final ScreenHandlerType<CourierPayScreenHandler> COURIER_PAY_HANDLER = Registry.register(
            Registries.SCREEN_HANDLER,
            id("courier_pay"),
            new ExtendedScreenHandlerType<>(CourierPayScreenHandler::new)
    );

    public static void registerAll() {
        // no-op: touching this class forces static initialization
    }
}