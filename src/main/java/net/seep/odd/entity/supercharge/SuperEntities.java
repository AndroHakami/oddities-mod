package net.seep.odd.entity.supercharge;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

public final class SuperEntities {
    private SuperEntities() {}
    public static EntityType<SuperThrownItemEntity> THROWN_ITEM;

    public static void register() {
        Identifier id = new Identifier(Oddities.MOD_ID, "super_thrown_item");
        THROWN_ITEM = Registry.register(
                Registries.ENTITY_TYPE, id,
                EntityType.Builder.<SuperThrownItemEntity>create(SuperThrownItemEntity::new, SpawnGroup.MISC)
                        .setDimensions(0.25f, 0.25f)
                        .maxTrackingRange(4)
                        .trackingTickInterval(10)
                        .build(id.toString())
        );
    }
}
