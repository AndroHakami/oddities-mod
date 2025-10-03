package net.seep.odd.abilities.ghostlings.registry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.ghostlings.client.GhostlingRenderer;
import net.seep.odd.abilities.ghostlings.entity.GhostlingEntity;
import net.seep.odd.abilities.ghostlings.screen.GhostDashboardScreenHandler;
import net.seep.odd.abilities.ghostlings.screen.GhostManageScreenHandler;

public final class GhostRegistries {
    private GhostRegistries() {}

    public static EntityType<GhostlingEntity> GHOSTLING_TYPE;
    public static ScreenHandlerType<GhostManageScreenHandler> GHOST_MANAGE_HANDLER;
    public static ScreenHandlerType<GhostDashboardScreenHandler> GHOST_DASH_HANDLER;

    public static Identifier id(String path) { return new Identifier("odd", path); }

    public static void registerAll() {
        GHOSTLING_TYPE = Registry.register(
                Registries.ENTITY_TYPE,
                id("ghostling"),
                FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, GhostlingEntity::new)
                        .dimensions(EntityDimensions.fixed(0.6f, 1.5f))
                        .trackRangeBlocks(96).trackedUpdateRate(3)
                        .build()
        );

        GHOST_MANAGE_HANDLER = Registry.register(
                Registries.SCREEN_HANDLER,
                id("ghost_manage"),
                new ExtendedScreenHandlerType<>(GhostManageScreenHandler::new)
        );
        GHOST_DASH_HANDLER = Registry.register(
                Registries.SCREEN_HANDLER,
                id("ghost_dashboard"),
                new ExtendedScreenHandlerType<>(GhostDashboardScreenHandler::new)
        );
    }

    // call from client init
    public static void registerClient() {
        EntityRendererRegistry.register(GHOSTLING_TYPE, GhostlingRenderer::new);
    }
}
