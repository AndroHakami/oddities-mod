package net.seep.odd.entity;

import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;

import net.seep.odd.Oddities; // your mod main w/ MODID
import net.seep.odd.entity.creepy.CreepyEntity;

public final class ModEntities {
    private ModEntities() {}

    public static final Identifier CREEPY_ID = new Identifier(Oddities.MOD_ID, "creepy");

    /** Assigned in {@link #register()} during mod init. */
    public static EntityType<CreepyEntity> CREEPY;

    public static void register() {
        CREEPY = Registry.register(
                Registries.ENTITY_TYPE,
                CREEPY_ID,
                FabricEntityTypeBuilder.create(SpawnGroup.MISC, CreepyEntity::new)
                        .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
                        .trackRangeBlocks(64)      // tune as you like
                        .trackedUpdateRate(1)      // tune as you like
                        .build()
        );
    }
}
