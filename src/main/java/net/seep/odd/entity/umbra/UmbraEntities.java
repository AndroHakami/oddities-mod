package net.seep.odd.entity.umbra;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;

import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.umbra.entity.ShadowKunaiEntity;

public final class UmbraEntities {
    private UmbraEntities() {}

    public static final EntityType<ShadowKunaiEntity> SHADOW_KUNAI = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Oddities.MOD_ID, "shadow_kunai"),
            FabricEntityTypeBuilder.<ShadowKunaiEntity>create(SpawnGroup.MISC, ShadowKunaiEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(10)
                    .build()
    );

    public static void init() {}
}