package net.seep.odd.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.World; // typed lambdas
import net.seep.odd.Oddities;
import net.seep.odd.abilities.tamer.entity.VillagerEvo1Entity;
import net.seep.odd.abilities.tamer.projectile.EmeraldShurikenEntity;
import net.seep.odd.entity.creepy.CreepyEntity;
import net.seep.odd.entity.misty.MistyBubbleEntity;

public final class ModEntities {
    private ModEntities() {}

    public static final Identifier CREEPY_ID            = new Identifier(Oddities.MOD_ID, "creepy");
    public static final Identifier MISTY_BUBBLE_ID      = new Identifier(Oddities.MOD_ID, "misty_bubble");
    public static final Identifier EMERALD_SHURIKEN_ID  = new Identifier(Oddities.MOD_ID, "emerald_shuriken");
    public static final Identifier VILLAGER_EVO1_ID     = new Identifier(Oddities.MOD_ID, "villager_evo1");

    /** Assigned in {@link #register()} during mod init. */
    public static EntityType<CreepyEntity>             CREEPY;
    public static EntityType<MistyBubbleEntity>        MISTY_BUBBLE;
    public static EntityType<EmeraldShurikenEntity>    EMERALD_SHURIKEN;
    public static EntityType<VillagerEvo1Entity>       VILLAGER_EVO1;

    public static void register() {
        // Creepy
        if (CREEPY == null) {
            CREEPY = Registry.register(
                    Registries.ENTITY_TYPE,
                    CREEPY_ID,
                    FabricEntityTypeBuilder.create(SpawnGroup.MISC, CreepyEntity::new)
                            .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
                            .trackRangeChunks(8)
                            .trackedUpdateRate(1)
                            .build()
            );
        }

        // Misty Bubble — visual-only
        if (MISTY_BUBBLE == null) {
            MISTY_BUBBLE = Registry.register(
                    Registries.ENTITY_TYPE,
                    MISTY_BUBBLE_ID,
                    FabricEntityTypeBuilder.create(
                                    SpawnGroup.MISC,
                                    (EntityType<MistyBubbleEntity> type, World world) -> new MistyBubbleEntity(type, world)
                            )
                            .dimensions(EntityDimensions.fixed(0.0f, 0.0f))
                            .trackRangeChunks(8)
                            .trackedUpdateRate(1)
                            .build()
            );
        }

        // Emerald Shuriken
        if (EMERALD_SHURIKEN == null) {
            EMERALD_SHURIKEN = Registry.register(
                    Registries.ENTITY_TYPE,
                    EMERALD_SHURIKEN_ID,
                    FabricEntityTypeBuilder.<EmeraldShurikenEntity>create(
                                    SpawnGroup.MISC,
                                    EmeraldShurikenEntity::new)
                            .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                            .trackRangeBlocks(64)
                            .trackedUpdateRate(10)
                            .build()
            );
        }

        // Villager Evo 1 — tanky GeckoLib mob (needs attributes registered)
        if (VILLAGER_EVO1 == null) {
            VILLAGER_EVO1 = Registry.register(
                    Registries.ENTITY_TYPE,
                    VILLAGER_EVO1_ID,
                    FabricEntityTypeBuilder.create(
                                    SpawnGroup.MISC,
                                    (EntityType<VillagerEvo1Entity> type, World world) -> new VillagerEvo1Entity(type, world)
                            )
                            .dimensions(EntityDimensions.fixed(0.7f, 2.0f))
                            .trackRangeBlocks(64)
                            .trackedUpdateRate(1)
                            .build()
            );
            FabricDefaultAttributeRegistry.register(VILLAGER_EVO1, VillagerEvo1Entity.createAttributes());
        }
    }
}
