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
import net.seep.odd.abilities.tamer.entity.VillagerEvoEntity;
import net.seep.odd.abilities.tamer.projectile.EmeraldShurikenEntity;
import net.seep.odd.abilities.tamer.projectile.TameBallEntity;
import net.seep.odd.entity.creepy.CreepyEntity;
import net.seep.odd.entity.misty.MistyBubbleEntity;
import net.seep.odd.entity.outerman.OuterManEntity;
import net.seep.odd.entity.ufo.UfoSaucerEntity;

public final class ModEntities {
    private ModEntities() {}

    public static final Identifier CREEPY_ID            = new Identifier(Oddities.MOD_ID, "creepy");
    public static final Identifier MISTY_BUBBLE_ID      = new Identifier(Oddities.MOD_ID, "misty_bubble");
    public static final Identifier EMERALD_SHURIKEN_ID  = new Identifier(Oddities.MOD_ID, "emerald_shuriken");
    public static final Identifier VILLAGER_EVO_ID      = new Identifier(Oddities.MOD_ID, "villager_evo");
    public static final Identifier TAME_BALL_ID         = new Identifier(Oddities.MOD_ID, "tame_ball");
    public static final Identifier BREW_BOTTLE_ID       = new Identifier(Oddities.MOD_ID, "brew_bottle");

    // NEW: UFO Saucer
    public static final Identifier UFO_SAUCER_ID        = new Identifier(Oddities.MOD_ID, "ufo_saucer");
    public static final Identifier OUTERMAN_ID          = new Identifier(Oddities.MOD_ID, "outerman");

    /** Assigned in {@link #register()} during mod init. */
    public static EntityType<CreepyEntity>             CREEPY;
    public static EntityType<MistyBubbleEntity>        MISTY_BUBBLE;
    public static EntityType<EmeraldShurikenEntity>    EMERALD_SHURIKEN;
    public static EntityType<VillagerEvoEntity>        VILLAGER_EVO;
    public static EntityType<TameBallEntity>           TAME_BALL;
    public static EntityType<net.seep.odd.abilities.artificer.mixer.projectile.BrewBottleEntity> BREW_BOTTLE;

    // NEW: UFO Saucer
    public static EntityType<UfoSaucerEntity>          UFO_SAUCER;
    public static EntityType<OuterManEntity>          OUTERMAN;

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
        if (VILLAGER_EVO == null) {
            VILLAGER_EVO = Registry.register(
                    Registries.ENTITY_TYPE,
                    VILLAGER_EVO_ID,
                    FabricEntityTypeBuilder.create(
                                    SpawnGroup.MISC,
                                    (EntityType<VillagerEvoEntity> type, World world) -> new VillagerEvoEntity(type, world)
                            )
                            .dimensions(EntityDimensions.fixed(0.7f, 2.0f))
                            .trackRangeBlocks(64)
                            .trackedUpdateRate(1)
                            .build()
            );
            FabricDefaultAttributeRegistry.register(VILLAGER_EVO, VillagerEvoEntity.createAttributes());
        }

        if (TAME_BALL == null) {
            TAME_BALL = Registry.register(
                    Registries.ENTITY_TYPE,
                    TAME_BALL_ID,
                    FabricEntityTypeBuilder
                            .<TameBallEntity>create(SpawnGroup.MISC, TameBallEntity::new)
                            .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                            .trackRangeBlocks(64)
                            .trackedUpdateRate(10)
                            .build()
            );
        }

        if (BREW_BOTTLE == null) {
            BREW_BOTTLE = Registry.register(
                    Registries.ENTITY_TYPE,
                    BREW_BOTTLE_ID,
                    FabricEntityTypeBuilder
                            .<net.seep.odd.abilities.artificer.mixer.projectile.BrewBottleEntity>create(
                                    SpawnGroup.MISC,
                                    net.seep.odd.abilities.artificer.mixer.projectile.BrewBottleEntity::new
                            )
                            .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                            .trackRangeBlocks(64)
                            .trackedUpdateRate(10)
                            .build()
            );
        }

        // NEW: UFO Saucer (hostile flying mob; for now we test via /summon so group=MISC is fine)
        if (UFO_SAUCER == null) {
            UFO_SAUCER = Registry.register(
                    Registries.ENTITY_TYPE,
                    UFO_SAUCER_ID,
                    FabricEntityTypeBuilder.create(
                                    SpawnGroup.MISC,
                                    (EntityType<UfoSaucerEntity> type, World world) -> new UfoSaucerEntity(type, world)
                            )
                            .dimensions(EntityDimensions.fixed(1.8f, 0.9f)) // tweak to your model
                            .trackRangeBlocks(96)
                            .trackedUpdateRate(1)
                            .build()
            );
            FabricDefaultAttributeRegistry.register(UFO_SAUCER, UfoSaucerEntity.createAttributes());
        }
        if (OUTERMAN == null) {
            OUTERMAN = Registry.register(
                    Registries.ENTITY_TYPE,
                    OUTERMAN_ID,
                    FabricEntityTypeBuilder.create(
                                    SpawnGroup.MONSTER,
                                    (EntityType<OuterManEntity> type, World world) -> new OuterManEntity(type, world)
                            )
                            .dimensions(EntityDimensions.fixed(0.6f, 1.25f)) // small alien
                            .trackRangeBlocks(64)
                            .trackedUpdateRate(1)
                            .build()
            );
            FabricDefaultAttributeRegistry.register(OUTERMAN, OuterManEntity.createAttributes());
        }
    }
}
