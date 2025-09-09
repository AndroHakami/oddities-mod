package net.seep.odd.abilities.voids;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class VoidRegistry {
    private VoidRegistry(){}

    public static EntityType<VoidPortalEntity> VOID_PORTAL;

    public static void initCommon() {
        VOID_PORTAL = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier("odd","void_portal"),
                FabricEntityTypeBuilder.<VoidPortalEntity>create(SpawnGroup.MISC, VoidPortalEntity::new)
                        .dimensions(EntityDimensions.fixed(1.2f, 2.0f))
                        .trackRangeChunks(8)
                        .trackedUpdateRate(10)
                        .build()
        );
    }
}
