package net.seep.odd.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.World; // <-- needed for the typed lambda
import net.seep.odd.Oddities;
import net.seep.odd.entity.creepy.CreepyEntity;
import net.seep.odd.entity.misty.MistyBubbleEntity;

public final class ModEntities {
    private ModEntities() {}

    public static final Identifier CREEPY_ID       = new Identifier(Oddities.MOD_ID, "creepy");
    public static final Identifier MISTY_BUBBLE_ID = new Identifier(Oddities.MOD_ID, "misty_bubble");

    /** Assigned in {@link #register()} during mod init. */
    public static EntityType<CreepyEntity>      CREEPY;
    public static EntityType<MistyBubbleEntity> MISTY_BUBBLE;

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

        // Misty Bubble — use a typed lambda so generics match exactly
        if (MISTY_BUBBLE == null) {
            MISTY_BUBBLE = Registry.register(
                    Registries.ENTITY_TYPE,
                    MISTY_BUBBLE_ID,
                    FabricEntityTypeBuilder.create(
                                    SpawnGroup.MISC,
                                    (EntityType<MistyBubbleEntity> type, World world) -> new MistyBubbleEntity(type, world)
                            )
                            .dimensions(EntityDimensions.fixed(0.0f, 0.0f)) // visual-only hitbox
                            .trackRangeChunks(8)
                            .trackedUpdateRate(1)
                            .build()
            );
        }
    }
}
