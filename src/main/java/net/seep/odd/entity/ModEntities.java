package net.seep.odd.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.icewitch.IceProjectileEntity;
import net.seep.odd.abilities.icewitch.IceSpellAreaEntity;
import net.seep.odd.abilities.tamer.entity.VillagerEvoEntity;
import net.seep.odd.abilities.tamer.projectile.EmeraldShurikenEntity;
import net.seep.odd.abilities.tamer.projectile.TameBallEntity;
import net.seep.odd.entity.car.RiderCarEntity;
import net.seep.odd.entity.creepy.CreepyEntity;
import net.seep.odd.entity.misty.MistyBubbleEntity;
import net.seep.odd.entity.outerman.OuterManEntity;
import net.seep.odd.entity.ufo.UfoSaucerEntity;
import net.seep.odd.abilities.cosmic.entity.HomingCosmicSwordEntity;
import net.seep.odd.abilities.ghostlings.entity.GhostlingEntity;
import net.seep.odd.entity.spotted.PhantomBuddyEntity;

// Falling Snow (new)
import net.seep.odd.abilities.fallingsnow.HealingSnowballEntity;
import net.seep.odd.abilities.fallingsnow.BigSnowballEntity;

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

    // Car
    public static final Identifier RIDER_CAR_ID         = new Identifier(Oddities.MOD_ID, "rider_car");

    // Cosmic Katana
    public static final Identifier HOMING_COSMIC_SWORD_ID = new Identifier(Oddities.MOD_ID, "homing_cosmic_sword");

    // Ghost
    public static final Identifier GHOSTLING_ID = new Identifier(Oddities.MOD_ID, "ghostling");

    // Ice Witch
    public static final Identifier ICE_SPELL_AREA_ID = new Identifier(Oddities.MOD_ID, "ice_spell_area");
    public static final Identifier ICE_PROJECTILE_ID  = new Identifier(Oddities.MOD_ID, "ice_projectile");

    // Spotted Phantom
    public static final Identifier PHANTOM_BUDDY_ID   = new Identifier(Oddities.MOD_ID, "phantom_buddy");

    // Zero Gravity
    public static final Identifier ZERO_BEAM_ID = new Identifier(Oddities.MOD_ID, "zero_beam");

    // Falling Snow (new)
    public static final Identifier HEALING_SNOWBALL_ID = new Identifier(Oddities.MOD_ID, "healing_snowball");
    public static final Identifier BIG_SNOWBALL_ID     = new Identifier(Oddities.MOD_ID, "big_snowball");
    public static final Identifier ORBITING_SNOWBALL_ID = new Identifier(Oddities.MOD_ID, "orbiting_snowball");

    // False Frog
    public static final Identifier FALSE_FROG_ID = new Identifier(Oddities.MOD_ID, "false_frog");




    /** Assigned in {@link #register()} during mod init. */
    public static EntityType<CreepyEntity>             CREEPY;
    public static EntityType<MistyBubbleEntity>        MISTY_BUBBLE;
    public static EntityType<EmeraldShurikenEntity>    EMERALD_SHURIKEN;
    public static EntityType<VillagerEvoEntity>        VILLAGER_EVO;
    public static EntityType<TameBallEntity>           TAME_BALL;
    public static EntityType<net.seep.odd.abilities.artificer.mixer.projectile.BrewBottleEntity> BREW_BOTTLE;

    // NEW: UFO Saucer
    public static EntityType<UfoSaucerEntity>          UFO_SAUCER;
    public static EntityType<OuterManEntity>           OUTERMAN;

    // Car
    public static EntityType<RiderCarEntity>           RIDER_CAR;

    // Cosmic Katana
    public static EntityType<HomingCosmicSwordEntity>  HOMING_COSMIC_SWORD;

    public static EntityType<GhostlingEntity>          GHOSTLING;

    // Ice Witch
    public static EntityType<IceSpellAreaEntity>       ICE_SPELL_AREA;
    public static EntityType<IceProjectileEntity>      ICE_PROJECTILE;

    // Spotted Phantom
    public static EntityType<PhantomBuddyEntity>       PHANTOM_BUDDY;

    // Zero Gravity
    public static EntityType<net.seep.odd.entity.zerosuit.ZeroBeamEntity> ZERO_BEAM;

    // Falling Snow
    public static EntityType<HealingSnowballEntity>    HEALING_SNOWBALL;
    public static EntityType<BigSnowballEntity>        BIG_SNOWBALL;
    public static EntityType<net.seep.odd.abilities.fallingsnow.OrbitingSnowballEntity> ORBITING_SNOWBALL;

    // False Frog
    public static EntityType<net.seep.odd.entity.falsefrog.FalseFrogEntity> FALSE_FROG;


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

        // Villager Evo
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
        if (ZERO_BEAM == null) {
            ZERO_BEAM = Registry.register(
                    Registries.ENTITY_TYPE,
                    ZERO_BEAM_ID,
                    FabricEntityTypeBuilder
                            .<net.seep.odd.entity.zerosuit.ZeroBeamEntity>create(SpawnGroup.MISC,
                                    (EntityType<net.seep.odd.entity.zerosuit.ZeroBeamEntity> t, World w) -> new net.seep.odd.entity.zerosuit.ZeroBeamEntity(t, w))
                            .dimensions(EntityDimensions.fixed(0.1f, 0.1f))
                            .trackRangeBlocks(128)
                            .trackedUpdateRate(1)
                            .build()
            );
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

        if (GHOSTLING == null) {
            GHOSTLING = Registry.register(
                    Registries.ENTITY_TYPE,
                    GHOSTLING_ID,
                    FabricEntityTypeBuilder.create(
                                    SpawnGroup.CREATURE,
                                    (EntityType<GhostlingEntity> type, World world) -> new GhostlingEntity(type, world)
                            )
                            .dimensions(EntityDimensions.fixed(0.6f, 1.5f))
                            .trackRangeBlocks(96)
                            .trackedUpdateRate(3)
                            .build()
            );
            FabricDefaultAttributeRegistry.register(GHOSTLING, GhostlingEntity.createAttributes());
        }

        // Ice Spell Area
        if (ICE_SPELL_AREA == null) {
            ICE_SPELL_AREA = Registry.register(
                    Registries.ENTITY_TYPE,
                    ICE_SPELL_AREA_ID,
                    FabricEntityTypeBuilder.create(
                                    SpawnGroup.MISC,
                                    (EntityType<IceSpellAreaEntity> type, World world) -> new IceSpellAreaEntity(type, world)
                            )
                            .dimensions(EntityDimensions.fixed(0.1f, 0.1f))
                            .trackRangeBlocks(64)
                            .trackedUpdateRate(1)
                            .build()
            );
        }

        // Ice Projectile
        if (ICE_PROJECTILE == null) {
            ICE_PROJECTILE = Registry.register(
                    Registries.ENTITY_TYPE,
                    ICE_PROJECTILE_ID,
                    FabricEntityTypeBuilder.<IceProjectileEntity>create(
                                    SpawnGroup.MISC,
                                    (EntityType<IceProjectileEntity> type, World world) -> new IceProjectileEntity(type, world)
                            )
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

        if (RIDER_CAR == null) {
            RIDER_CAR = Registry.register(
                    Registries.ENTITY_TYPE,
                    RIDER_CAR_ID,
                    FabricEntityTypeBuilder.create(
                                    SpawnGroup.MISC,
                                    (EntityType<RiderCarEntity> type, World world) -> new RiderCarEntity(type, world)
                            )
                            .dimensions(EntityDimensions.fixed(3.6f, 1.2f))
                            .trackRangeBlocks(96)
                            .trackedUpdateRate(2)
                            .build()
            );
            FabricDefaultAttributeRegistry.register(RIDER_CAR, RiderCarEntity.createAttributes());
        }

        // Cosmic: Homing Sword projectile
        if (HOMING_COSMIC_SWORD == null) {
            HOMING_COSMIC_SWORD = Registry.register(
                    Registries.ENTITY_TYPE,
                    HOMING_COSMIC_SWORD_ID,
                    FabricEntityTypeBuilder.<HomingCosmicSwordEntity>create(
                                    SpawnGroup.MISC,
                                    HomingCosmicSwordEntity::new)
                            .dimensions(EntityDimensions.fixed(0.5f, 0.5f))
                            .trackRangeBlocks(96)
                            .trackedUpdateRate(1)
                            .build()
            );
        }

        // NEW: UFO Saucer
        if (UFO_SAUCER == null) {
            UFO_SAUCER = Registry.register(
                    Registries.ENTITY_TYPE,
                    UFO_SAUCER_ID,
                    FabricEntityTypeBuilder.create(
                                    SpawnGroup.MISC,
                                    (EntityType<UfoSaucerEntity> type, World world) -> new UfoSaucerEntity(type, world)
                            )
                            .dimensions(EntityDimensions.fixed(1.8f, 0.9f))
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
                            .dimensions(EntityDimensions.fixed(0.6f, 1.25f))
                            .trackRangeBlocks(64)
                            .trackedUpdateRate(1)
                            .build()
            );
            FabricDefaultAttributeRegistry.register(OUTERMAN, OuterManEntity.createAttributes());
        }

        // === Spotted Phantom: Phantom Buddy ===
        if (PHANTOM_BUDDY == null) {
            PHANTOM_BUDDY = Registry.register(
                    Registries.ENTITY_TYPE,
                    PHANTOM_BUDDY_ID,
                    FabricEntityTypeBuilder.create(
                                    SpawnGroup.CREATURE,
                                    (EntityType<PhantomBuddyEntity> type, World world) -> new PhantomBuddyEntity(type, world)
                            )
                            .dimensions(EntityDimensions.fixed(0.8f, 0.9f)) // tweak to model
                            .trackRangeBlocks(64)
                            .trackedUpdateRate(2)
                            .build()
            );
            FabricDefaultAttributeRegistry.register(PHANTOM_BUDDY, PhantomBuddyEntity.createAttributes());
        }

        // === Falling Snow: projectiles ===
        if (HEALING_SNOWBALL == null) {
            HEALING_SNOWBALL = Registry.register(
                    Registries.ENTITY_TYPE,
                    HEALING_SNOWBALL_ID,
                    FabricEntityTypeBuilder.<HealingSnowballEntity>create(
                                    SpawnGroup.MISC,
                                    (EntityType<HealingSnowballEntity> type, World world) -> new HealingSnowballEntity(type, world))
                            .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                            .trackRangeBlocks(64)
                            .trackedUpdateRate(10)
                            .build()
            );
        }

        if (BIG_SNOWBALL == null) {
            BIG_SNOWBALL = Registry.register(
                    Registries.ENTITY_TYPE,
                    BIG_SNOWBALL_ID,
                    FabricEntityTypeBuilder.<BigSnowballEntity>create(
                                    SpawnGroup.MISC,
                                    (EntityType<BigSnowballEntity> type, World world) -> new BigSnowballEntity(type, world))
                            .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                            .trackRangeBlocks(64)
                            .trackedUpdateRate(10)
                            .build()
            );
        }
        if (ORBITING_SNOWBALL == null) {
            ORBITING_SNOWBALL = Registry.register(
                    Registries.ENTITY_TYPE,
                    ORBITING_SNOWBALL_ID,
                    FabricEntityTypeBuilder
                            .<net.seep.odd.abilities.fallingsnow.OrbitingSnowballEntity>create(
                                    SpawnGroup.MISC,
                                    (EntityType<net.seep.odd.abilities.fallingsnow.OrbitingSnowballEntity> t, World w) ->
                                            new net.seep.odd.abilities.fallingsnow.OrbitingSnowballEntity(t, w)
                            )
                            .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                            .trackRangeBlocks(64)
                            .trackedUpdateRate(10)
                            .build()
            );
        }
        if (FALSE_FROG == null) {
            FALSE_FROG = Registry.register(
                    Registries.ENTITY_TYPE,
                    FALSE_FROG_ID,
                    FabricEntityTypeBuilder.create(
                                    SpawnGroup.MONSTER,
                                    (EntityType<net.seep.odd.entity.falsefrog.FalseFrogEntity> t, World w) ->
                                            new net.seep.odd.entity.falsefrog.FalseFrogEntity(t, w)
                            )
                            .dimensions(EntityDimensions.fixed(
                                    net.seep.odd.entity.falsefrog.FalseFrogEntity.WIDTH,
                                    net.seep.odd.entity.falsefrog.FalseFrogEntity.HEIGHT))
                            .trackRangeBlocks(96)
                            .trackedUpdateRate(2)
                            .build()
            );
            FabricDefaultAttributeRegistry.register(
                    FALSE_FROG,
                    net.seep.odd.entity.falsefrog.FalseFrogEntity.createAttributes()
            );
        }

    }
}
