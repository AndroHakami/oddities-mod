// FILE: src/main/java/net/seep/odd/entity/ModEntities.java
package net.seep.odd.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.seep.odd.Oddities;

import net.seep.odd.abilities.climber.entity.ClimberPullTetherEntity;
import net.seep.odd.abilities.climber.entity.ClimberRopeAnchorEntity;
import net.seep.odd.abilities.climber.entity.ClimberRopeShotEntity;
import net.seep.odd.abilities.conquer.entity.DarkHorseEntity;
import net.seep.odd.abilities.conquer.entity.CorruptedVillagerEntity;
import net.seep.odd.abilities.conquer.entity.CorruptedIronGolemEntity;

import net.seep.odd.abilities.firesword.entity.FireSwordProjectileEntity;
import net.seep.odd.abilities.icewitch.IceProjectileEntity;
import net.seep.odd.abilities.icewitch.IceSpellAreaEntity;
import net.seep.odd.abilities.lunar.entity.LunarMarkProjectileEntity;
import net.seep.odd.abilities.necromancer.entity.NecroBoltEntity;
import net.seep.odd.abilities.tamer.entity.VillagerEvoEntity;
import net.seep.odd.abilities.tamer.projectile.EmeraldShurikenEntity;
import net.seep.odd.abilities.tamer.projectile.TameBallEntity;
import net.seep.odd.abilities.vampire.entity.BloodCrystalProjectileEntity;
import net.seep.odd.abilities.wizard.entity.*;
import net.seep.odd.entity.booklet.BookletEntity;
import net.seep.odd.entity.car.RiderCarEntity;
import net.seep.odd.entity.creepy.CreepyEntity;
import net.seep.odd.entity.cultist.CentipedeEntity;
import net.seep.odd.entity.cultist.ShyGuyEntity;
import net.seep.odd.entity.cultist.SightseerEntity;
import net.seep.odd.entity.cultist.WeepingAngelEntity;
import net.seep.odd.entity.firefly.FireflyEntity;
import net.seep.odd.entity.misty.MistyBubbleEntity;
import net.seep.odd.entity.outerman.OuterManEntity;
import net.seep.odd.entity.seal.SealEntity;
import net.seep.odd.entity.ufo.UfoSaucerEntity;
import net.seep.odd.abilities.cosmic.entity.HomingCosmicSwordEntity;
import net.seep.odd.abilities.ghostlings.entity.GhostlingEntity;
import net.seep.odd.entity.spotted.PhantomBuddyEntity;


// Falling Snow
import net.seep.odd.abilities.fallingsnow.HealingSnowballEntity;
import net.seep.odd.abilities.fallingsnow.BigSnowballEntity;

import net.seep.odd.entity.zerosuit.ZeroSuitMissileEntity;

// ===== NEW: Necromancer corpses =====
import net.seep.odd.entity.necromancer.ZombieCorpseEntity;
import net.seep.odd.entity.necromancer.SkeletonCorpseEntity;

// ===== NEW: Sniper grapple =====
import net.seep.odd.abilities.sniper.entity.SniperGrappleAnchorEntity;
import net.seep.odd.abilities.sniper.entity.SniperGrappleShotEntity;

public final class ModEntities {
    private ModEntities() {}

    public static final Identifier CREEPY_ID             = new Identifier(Oddities.MOD_ID, "creepy");
    public static final Identifier MISTY_BUBBLE_ID       = new Identifier(Oddities.MOD_ID, "misty_bubble");
    public static final Identifier EMERALD_SHURIKEN_ID   = new Identifier(Oddities.MOD_ID, "emerald_shuriken");
    public static final Identifier VILLAGER_EVO_ID       = new Identifier(Oddities.MOD_ID, "villager_evo");
    public static final Identifier TAME_BALL_ID          = new Identifier(Oddities.MOD_ID, "tame_ball");
    public static final Identifier BREW_BOTTLE_ID        = new Identifier(Oddities.MOD_ID, "brew_bottle");

    public static final Identifier UFO_SAUCER_ID         = new Identifier(Oddities.MOD_ID, "ufo_saucer");
    public static final Identifier OUTERMAN_ID           = new Identifier(Oddities.MOD_ID, "outerman");

    public static final Identifier RIDER_CAR_ID          = new Identifier(Oddities.MOD_ID, "rider_car");

    public static final Identifier HOMING_COSMIC_SWORD_ID = new Identifier(Oddities.MOD_ID, "homing_cosmic_sword");

    public static final Identifier GHOSTLING_ID          = new Identifier(Oddities.MOD_ID, "ghostling");

    public static final Identifier ICE_SPELL_AREA_ID     = new Identifier(Oddities.MOD_ID, "ice_spell_area");
    public static final Identifier ICE_PROJECTILE_ID     = new Identifier(Oddities.MOD_ID, "ice_projectile");

    public static final Identifier PHANTOM_BUDDY_ID      = new Identifier(Oddities.MOD_ID, "phantom_buddy");

    public static final Identifier ZERO_BEAM_ID          = new Identifier(Oddities.MOD_ID, "zero_beam");
    public static final Identifier ZERO_SUIT_MISSILE_ID  = new Identifier(Oddities.MOD_ID, "zero_suit_missile");

    public static final Identifier HEALING_SNOWBALL_ID   = new Identifier(Oddities.MOD_ID, "healing_snowball");
    public static final Identifier BIG_SNOWBALL_ID       = new Identifier(Oddities.MOD_ID, "big_snowball");
    public static final Identifier ORBITING_SNOWBALL_ID  = new Identifier(Oddities.MOD_ID, "orbiting_snowball");

    public static final Identifier FALSE_FROG_ID         = new Identifier(Oddities.MOD_ID, "false_frog");
    public static final Identifier FIREFLY_ID            = new Identifier(Oddities.MOD_ID, "firefly");

    public static final Identifier LUNAR_MARK_ID         = new Identifier(Oddities.MOD_ID, "lunar_mark");

    public static final Identifier FIRE_SWORD_ID         = new Identifier(Oddities.MOD_ID, "fire_sword_projectile");

    // Conquer
    public static final Identifier DARK_HORSE_ID           = new Identifier(Oddities.MOD_ID, "dark_horse");
    public static final Identifier CORRUPTED_VILLAGER_ID   = new Identifier(Oddities.MOD_ID, "corrupted_villager");
    public static final Identifier CORRUPTED_IRON_GOLEM_ID = new Identifier(Oddities.MOD_ID, "corrupted_iron_golem");

    public static final Identifier SEAL_ID = new Identifier(Oddities.MOD_ID, "seal");
    public static final Identifier SIGHTSEER_ID = new Identifier(Oddities.MOD_ID, "sightseer");
    public static final Identifier SHY_GUY_ID = new Identifier(Oddities.MOD_ID, "shy_guy");

    public static final Identifier WEEPING_ANGEL_ID = new Identifier(Oddities.MOD_ID, "weeping_angel");
    public static final Identifier CENTIPEDE_ID = new Identifier(Oddities.MOD_ID, "centipede");

    // Climber
    public static final Identifier CLIMBER_ROPE_SHOT_ID   = new Identifier(Oddities.MOD_ID, "climber_rope_shot");
    public static final Identifier CLIMBER_ROPE_ANCHOR_ID = new Identifier(Oddities.MOD_ID, "climber_rope_anchor");
    public static final Identifier CLIMBER_PULL_TETHER_ID = new Identifier(Oddities.MOD_ID, "climber_pull_tether");

    // ===== NEW: Sniper grapple (IDs) =====
    public static final Identifier SNIPER_GRAPPLE_SHOT_ID   = new Identifier(Oddities.MOD_ID, "sniper_grapple_shot");
    public static final Identifier SNIPER_GRAPPLE_ANCHOR_ID = new Identifier(Oddities.MOD_ID, "sniper_grapple_anchor");

    // Rise
    public static final Identifier RISEN_ZOMBIE_ID = new Identifier(Oddities.MOD_ID, "risen_zombie");

    // ===== NEW: Necromancer corpses (IDs) =====
    public static final Identifier ZOMBIE_CORPSE_ID   = new Identifier(Oddities.MOD_ID, "zombie_corpse");
    public static final Identifier SKELETON_CORPSE_ID = new Identifier(Oddities.MOD_ID, "skeleton_corpse");
    public static final Identifier NECRO_BOLT_ID = new Identifier(Oddities.MOD_ID, "necro_bolt");
    public static final Identifier BLOOD_CRYSTAL_PROJECTILE_ID = new Identifier(Oddities.MOD_ID, "blood_crystal_project");

    // Atheneum
    public static final Identifier BOOKLET_ID = new Identifier(Oddities.MOD_ID, "booklet");

    // Wizard
    public static final Identifier WIZARD_FIRE_PROJECTILE_ID   = new Identifier(Oddities.MOD_ID, "wizard_fire_projectile");
    public static final Identifier WIZARD_WATER_PROJECTILE_ID  = new Identifier(Oddities.MOD_ID, "wizard_water_projectile");
    public static final Identifier WIZARD_EARTH_PROJECTILE_ID  = new Identifier(Oddities.MOD_ID, "wizard_earth_projectile");

    // Big/area effects that must keep ticking even if wizard dies/leaves
    public static final Identifier WIZARD_TORNADO_ID           = new Identifier(Oddities.MOD_ID, "wizard_tornado");
    public static final Identifier WIZARD_STEAM_CLOUD_ID       = new Identifier(Oddities.MOD_ID, "wizard_steam_cloud");
    public static final Identifier WIZARD_EARTHQUAKE_ID        = new Identifier(Oddities.MOD_ID, "wizard_earthquake");
    public static final Identifier WIZARD_FIRE_TORNADO_ID      = new Identifier(Oddities.MOD_ID, "wizard_fire_tornado");

    // Familiar
    public static final Identifier CAPYBARA_FAMILIAR_ID        = new Identifier(Oddities.MOD_ID, "capybara_familiar");




    /* =========================================================
       EntityType registration — “static final” style
       ========================================================= */
    // ADD TO ModEntities
    public static final EntityType<WizardMeteorEntity> WIZARD_METEOR =
            Registry.register(Registries.ENTITY_TYPE,
                    new Identifier(Oddities.MOD_ID, "wizard_meteor"),
                    FabricEntityTypeBuilder.create(SpawnGroup.MISC, WizardMeteorEntity::new)
                            .dimensions(EntityDimensions.fixed(1.0f, 1.0f))
                            .trackRangeBlocks(96).trackedUpdateRate(1)
                            .build()
            );

    // Wizard: Fire projectile
    public static final EntityType<WizardFireProjectileEntity> WIZARD_FIRE_PROJECTILE = Registry.register(
            Registries.ENTITY_TYPE,
            WIZARD_FIRE_PROJECTILE_ID,
            FabricEntityTypeBuilder.<WizardFireProjectileEntity>create(SpawnGroup.MISC, WizardFireProjectileEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                    .trackRangeBlocks(96)
                    .trackedUpdateRate(1)
                    .forceTrackedVelocityUpdates(true)
                    .build()
    );

    // Wizard: Water projectile
    public static final EntityType<WizardWaterProjectileEntity> WIZARD_WATER_PROJECTILE = Registry.register(
            Registries.ENTITY_TYPE,
            WIZARD_WATER_PROJECTILE_ID,
            FabricEntityTypeBuilder.<WizardWaterProjectileEntity>create(SpawnGroup.MISC, WizardWaterProjectileEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                    .trackRangeBlocks(96)
                    .trackedUpdateRate(1)
                    .forceTrackedVelocityUpdates(true)
                    .build()
    );

    // Wizard: Earth projectile
    public static final EntityType<WizardEarthProjectileEntity> WIZARD_EARTH_PROJECTILE = Registry.register(
            Registries.ENTITY_TYPE,
            WIZARD_EARTH_PROJECTILE_ID,
            FabricEntityTypeBuilder.<WizardEarthProjectileEntity>create(SpawnGroup.MISC, WizardEarthProjectileEntity::new)
                    .dimensions(EntityDimensions.fixed(1.9f, 1.9f))
                    .trackRangeBlocks(96)
                    .trackedUpdateRate(1)
                    .forceTrackedVelocityUpdates(true)
                    .build()
    );

    // Wizard: Tornado (big air)
    public static final EntityType<WizardTornadoEntity> WIZARD_TORNADO = Registry.register(
            Registries.ENTITY_TYPE,
            WIZARD_TORNADO_ID,
            FabricEntityTypeBuilder.<WizardTornadoEntity>create(SpawnGroup.MISC, WizardTornadoEntity::new)
                    .dimensions(EntityDimensions.fixed(1.4f, 3.0f))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(1)
                    .build()
    );

    // Wizard: Steam cloud (combo)
    public static final EntityType<WizardSteamCloudEntity> WIZARD_STEAM_CLOUD = Registry.register(
            Registries.ENTITY_TYPE,
            WIZARD_STEAM_CLOUD_ID,
            FabricEntityTypeBuilder.<WizardSteamCloudEntity>create(SpawnGroup.MISC, WizardSteamCloudEntity::new)
                    .dimensions(EntityDimensions.fixed(0.1f, 0.1f))
                    .trackRangeBlocks(96)
                    .trackedUpdateRate(1)
                    .build()
    );

    // Wizard: Earthquake (big earth)
    public static final EntityType<WizardEarthquakeEntity> WIZARD_EARTHQUAKE = Registry.register(
            Registries.ENTITY_TYPE,
            WIZARD_EARTHQUAKE_ID,
            FabricEntityTypeBuilder.<WizardEarthquakeEntity>create(SpawnGroup.MISC, WizardEarthquakeEntity::new)
                    .dimensions(EntityDimensions.fixed(0.1f, 0.1f))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(1)
                    .build()
    );

    // Wizard: Fire tornado (combo)
    public static final EntityType<WizardFireTornadoEntity> WIZARD_FIRE_TORNADO = Registry.register(
            Registries.ENTITY_TYPE,
            WIZARD_FIRE_TORNADO_ID,
            FabricEntityTypeBuilder.<WizardFireTornadoEntity>create(SpawnGroup.MISC, WizardFireTornadoEntity::new)
                    .dimensions(EntityDimensions.fixed(1.4f, 3.0f))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(1)
                    .build()
    );

    // Wizard: Capybara familiar
    public static final EntityType<CapybaraFamiliarEntity> CAPYBARA_FAMILIAR = Registry.register(
            Registries.ENTITY_TYPE,
            CAPYBARA_FAMILIAR_ID,
            FabricEntityTypeBuilder.<CapybaraFamiliarEntity>create(SpawnGroup.CREATURE, CapybaraFamiliarEntity::new)
                    .dimensions(EntityDimensions.fixed(0.9f, 0.8f))
                    .trackRangeBlocks(96)
                    .trackedUpdateRate(2)
                    .build()
    );

    public static final EntityType<BookletEntity> BOOKLET = Registry.register(
            Registries.ENTITY_TYPE,
            BOOKLET_ID,
            FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, BookletEntity::new)
                    .dimensions(EntityDimensions.fixed(0.75f, 0.85f))
                    .trackRangeChunks(8)
                    .trackedUpdateRate(3)
                    .build()
    );

    public static final EntityType<BloodCrystalProjectileEntity> BLOOD_CRYSTAL_PROJECTILE = Registry.register(
            Registries.ENTITY_TYPE,
            BLOOD_CRYSTAL_PROJECTILE_ID,
            FabricEntityTypeBuilder.<BloodCrystalProjectileEntity>create(SpawnGroup.MISC, BloodCrystalProjectileEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                    .trackRangeBlocks(96)
                    .trackedUpdateRate(1)
                    .forceTrackedVelocityUpdates(true)
                    .build()
    );

    public static final EntityType<NecroBoltEntity> NECRO_BOLT = Registry.register(
            Registries.ENTITY_TYPE,
            NECRO_BOLT_ID,
            FabricEntityTypeBuilder.<NecroBoltEntity>create(SpawnGroup.MISC, NecroBoltEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                    .trackRangeBlocks(96)
                    .trackedUpdateRate(1)
                    .forceTrackedVelocityUpdates(true)
                    .build()
    );

    // Rise: Risen Zombie
    public static final EntityType<net.seep.odd.abilities.rise.entity.RisenZombieEntity> RISEN_ZOMBIE =
            Registry.register(Registries.ENTITY_TYPE,
                    new Identifier(Oddities.MOD_ID, "risen_zombie"),
                    FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, net.seep.odd.abilities.rise.entity.RisenZombieEntity::new)
                            .dimensions(EntityDimensions.fixed(0.6F, 1.95F))
                            .trackRangeBlocks(64)
                            .trackedUpdateRate(3)
                            .build()
            );

    // =========================
    // Climber: Rope shot / Anchor / Pull tether
    // =========================

    public static final EntityType<ClimberRopeShotEntity> CLIMBER_ROPE_SHOT = Registry.register(
            Registries.ENTITY_TYPE,
            CLIMBER_ROPE_SHOT_ID,
            FabricEntityTypeBuilder.<ClimberRopeShotEntity>create(SpawnGroup.MISC, ClimberRopeShotEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(10)
                    .build()
    );

    public static final EntityType<ClimberRopeAnchorEntity> CLIMBER_ROPE_ANCHOR = Registry.register(
            Registries.ENTITY_TYPE,
            CLIMBER_ROPE_ANCHOR_ID,
            FabricEntityTypeBuilder.<ClimberRopeAnchorEntity>create(SpawnGroup.MISC, ClimberRopeAnchorEntity::new)
                    .dimensions(EntityDimensions.fixed(0.10f, 0.10f))
                    .trackRangeBlocks(96)
                    .trackedUpdateRate(20)
                    .build()
    );

    public static final EntityType<ClimberPullTetherEntity> CLIMBER_PULL_TETHER = Registry.register(
            Registries.ENTITY_TYPE,
            CLIMBER_PULL_TETHER_ID,
            FabricEntityTypeBuilder.<ClimberPullTetherEntity>create(SpawnGroup.MISC, ClimberPullTetherEntity::new)
                    .dimensions(EntityDimensions.fixed(0.10f, 0.10f))
                    .trackRangeBlocks(96)
                    .trackedUpdateRate(1)
                    .build()
    );

    // =========================
    // ===== NEW: Sniper grapple (shot + anchor)
    // =========================

    public static final EntityType<SniperGrappleShotEntity> SNIPER_GRAPPLE_SHOT = Registry.register(
            Registries.ENTITY_TYPE,
            SNIPER_GRAPPLE_SHOT_ID,
            FabricEntityTypeBuilder.<SniperGrappleShotEntity>create(SpawnGroup.MISC, SniperGrappleShotEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(10)
                    .build()
    );

    public static final EntityType<SniperGrappleAnchorEntity> SNIPER_GRAPPLE_ANCHOR = Registry.register(
            Registries.ENTITY_TYPE,
            SNIPER_GRAPPLE_ANCHOR_ID,
            FabricEntityTypeBuilder.<SniperGrappleAnchorEntity>create(SpawnGroup.MISC, SniperGrappleAnchorEntity::new)
                    .dimensions(EntityDimensions.fixed(0.10f, 0.10f))
                    .trackRangeBlocks(96)
                    .trackedUpdateRate(20)
                    .build()
    );

    // Cultist: Centipede
    public static final EntityType<CentipedeEntity> CENTIPEDE = Registry.register(
            Registries.ENTITY_TYPE,
            CENTIPEDE_ID,
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, CentipedeEntity::new)
                    .dimensions(EntityDimensions.fixed(0.9f, 0.25f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(2)
                    .build()
    );

    // Cultist: Weeping Angel
    public static final EntityType<WeepingAngelEntity> WEEPING_ANGEL = Registry.register(
            Registries.ENTITY_TYPE,
            WEEPING_ANGEL_ID,
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, WeepingAngelEntity::new)
                    .dimensions(EntityDimensions.fixed(0.98f, 0.98f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(2)
                    .build()
    );

    // Cultist: Shy Guy
    public static final EntityType<ShyGuyEntity> SHY_GUY = Registry.register(
            Registries.ENTITY_TYPE,
            SHY_GUY_ID,
            FabricEntityTypeBuilder.create(
                            SpawnGroup.MONSTER,
                            (EntityType<ShyGuyEntity> type, World world) -> new ShyGuyEntity(type, world)
                    )
                    .dimensions(EntityDimensions.fixed(0.85f, 2.1f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(2)
                    .build()
    );

    // Cultist: Sightseer
    public static final EntityType<SightseerEntity> SIGHTSEER = Registry.register(
            Registries.ENTITY_TYPE,
            SIGHTSEER_ID,
            FabricEntityTypeBuilder.create(
                            SpawnGroup.MONSTER,
                            (EntityType<SightseerEntity> type, World world) -> new SightseerEntity(type, world)
                    )
                    .dimensions(EntityDimensions.fixed(0.85f, 2.1f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(2)
                    .build()
    );

    // Seal
    public static final EntityType<SealEntity> SEAL = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Oddities.MOD_ID, "seal"),
            FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, SealEntity::new)
                    .dimensions(EntityDimensions.fixed(0.9f, 0.6f))
                    .trackRangeChunks(8)
                    .trackedUpdateRate(3)
                    .build()
    );

    // Creepy
    public static final EntityType<CreepyEntity> CREEPY = Registry.register(
            Registries.ENTITY_TYPE,
            CREEPY_ID,
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, CreepyEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
                    .trackRangeChunks(8)
                    .trackedUpdateRate(1)
                    .build()
    );

    // Misty Bubble — visual-only
    public static final EntityType<MistyBubbleEntity> MISTY_BUBBLE = Registry.register(
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

    // Lunar Mark projectile
    public static final EntityType<LunarMarkProjectileEntity> LUNAR_MARK = Registry.register(
            Registries.ENTITY_TYPE,
            LUNAR_MARK_ID,
            FabricEntityTypeBuilder.<LunarMarkProjectileEntity>create(SpawnGroup.MISC, LunarMarkProjectileEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(10)
                    .build()
    );

    // Zero Suit: Missile
    public static final EntityType<ZeroSuitMissileEntity> ZERO_SUIT_MISSILE = Registry.register(
            Registries.ENTITY_TYPE,
            ZERO_SUIT_MISSILE_ID,
            FabricEntityTypeBuilder.<ZeroSuitMissileEntity>create(SpawnGroup.MONSTER, ZeroSuitMissileEntity::new)
                    .dimensions(EntityDimensions.fixed(1.1f, 0.25f))
                    .trackRangeBlocks(200)
                    .trackedUpdateRate(1)
                    .forceTrackedVelocityUpdates(true)
                    .build()
    );

    // Fire Sword projectile
    public static final EntityType<FireSwordProjectileEntity> FIRE_SWORD_PROJECTILE = Registry.register(
            Registries.ENTITY_TYPE,
            FIRE_SWORD_ID,
            FabricEntityTypeBuilder.<FireSwordProjectileEntity>create(SpawnGroup.MISC, FireSwordProjectileEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(10)
                    .build()
    );

    // Emerald Shuriken
    public static final EntityType<EmeraldShurikenEntity> EMERALD_SHURIKEN = Registry.register(
            Registries.ENTITY_TYPE,
            EMERALD_SHURIKEN_ID,
            FabricEntityTypeBuilder.<EmeraldShurikenEntity>create(SpawnGroup.MISC, EmeraldShurikenEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(10)
                    .build()
    );

    // Villager Evo
    public static final EntityType<VillagerEvoEntity> VILLAGER_EVO = Registry.register(
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

    // Zero Gravity beam
    public static final EntityType<net.seep.odd.entity.zerosuit.ZeroBeamEntity> ZERO_BEAM = Registry.register(
            Registries.ENTITY_TYPE,
            ZERO_BEAM_ID,
            FabricEntityTypeBuilder
                    .<net.seep.odd.entity.zerosuit.ZeroBeamEntity>create(
                            SpawnGroup.MISC,
                            (EntityType<net.seep.odd.entity.zerosuit.ZeroBeamEntity> t, World w) ->
                                    new net.seep.odd.entity.zerosuit.ZeroBeamEntity(t, w)
                    )
                    .dimensions(EntityDimensions.fixed(1f, 1f))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(1)
                    .build()
    );

    // Tame Ball
    public static final EntityType<TameBallEntity> TAME_BALL = Registry.register(
            Registries.ENTITY_TYPE,
            TAME_BALL_ID,
            FabricEntityTypeBuilder
                    .<TameBallEntity>create(SpawnGroup.MISC, TameBallEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(10)
                    .build()
    );

    // Ghostling
    public static final EntityType<GhostlingEntity> GHOSTLING = Registry.register(
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

    // Ice Spell Area
    public static final EntityType<IceSpellAreaEntity> ICE_SPELL_AREA = Registry.register(
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

    // Ice Projectile
    public static final EntityType<IceProjectileEntity> ICE_PROJECTILE = Registry.register(
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

    // Brew Bottle
    public static final EntityType<net.seep.odd.abilities.artificer.mixer.projectile.BrewBottleEntity> BREW_BOTTLE = Registry.register(
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

    // Car
    public static final EntityType<RiderCarEntity> RIDER_CAR = Registry.register(
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

    // Cosmic: Homing Sword projectile
    public static final EntityType<HomingCosmicSwordEntity> HOMING_COSMIC_SWORD = Registry.register(
            Registries.ENTITY_TYPE,
            HOMING_COSMIC_SWORD_ID,
            FabricEntityTypeBuilder.<HomingCosmicSwordEntity>create(SpawnGroup.MISC, HomingCosmicSwordEntity::new)
                    .dimensions(EntityDimensions.fixed(0.5f, 0.5f))
                    .trackRangeBlocks(96)
                    .trackedUpdateRate(1)
                    .build()
    );

    // UFO Saucer
    public static final EntityType<UfoSaucerEntity> UFO_SAUCER = Registry.register(
            Registries.ENTITY_TYPE,
            UFO_SAUCER_ID,
            FabricEntityTypeBuilder.create(
                            SpawnGroup.MISC,
                            (EntityType<UfoSaucerEntity> type, World world) -> new UfoSaucerEntity(type, world)
                    )
                    .dimensions(EntityDimensions.fixed(3.8f, 2.2f))
                    .trackRangeBlocks(96)
                    .trackedUpdateRate(1)
                    .build()
    );

    // Outerman
    public static final EntityType<OuterManEntity> OUTERMAN = Registry.register(
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

    // Spotted Phantom: Phantom Buddy
    public static final EntityType<PhantomBuddyEntity> PHANTOM_BUDDY = Registry.register(
            Registries.ENTITY_TYPE,
            PHANTOM_BUDDY_ID,
            FabricEntityTypeBuilder.create(
                            SpawnGroup.CREATURE,
                            (EntityType<PhantomBuddyEntity> type, World world) -> new PhantomBuddyEntity(type, world)
                    )
                    .dimensions(EntityDimensions.fixed(0.8f, 0.9f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(2)
                    .build()
    );

    // Falling Snow: Healing Snowball
    public static final EntityType<HealingSnowballEntity> HEALING_SNOWBALL = Registry.register(
            Registries.ENTITY_TYPE,
            HEALING_SNOWBALL_ID,
            FabricEntityTypeBuilder.<HealingSnowballEntity>create(
                            SpawnGroup.MISC,
                            (EntityType<HealingSnowballEntity> type, World world) -> new HealingSnowballEntity(type, world)
                    )
                    .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(10)
                    .build()
    );

    // Falling Snow: Big Snowball
    public static final EntityType<BigSnowballEntity> BIG_SNOWBALL = Registry.register(
            Registries.ENTITY_TYPE,
            BIG_SNOWBALL_ID,
            FabricEntityTypeBuilder.<BigSnowballEntity>create(
                            SpawnGroup.MISC,
                            (EntityType<BigSnowballEntity> type, World world) -> new BigSnowballEntity(type, world)
                    )
                    .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(10)
                    .build()
    );

    // Falling Snow: Orbiting Snowball
    public static final EntityType<net.seep.odd.abilities.fallingsnow.OrbitingSnowballEntity> ORBITING_SNOWBALL = Registry.register(
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

    // Rotten Roots: False Frog
    public static final EntityType<net.seep.odd.entity.falsefrog.FalseFrogEntity> FALSE_FROG = Registry.register(
            Registries.ENTITY_TYPE,
            FALSE_FROG_ID,
            FabricEntityTypeBuilder.create(
                            SpawnGroup.MONSTER,
                            (EntityType<net.seep.odd.entity.falsefrog.FalseFrogEntity> t, World w) ->
                                    new net.seep.odd.entity.falsefrog.FalseFrogEntity(t, w)
                    )
                    .dimensions(EntityDimensions.fixed(
                            net.seep.odd.entity.falsefrog.FalseFrogEntity.WIDTH,
                            net.seep.odd.entity.falsefrog.FalseFrogEntity.HEIGHT
                    ))
                    .trackRangeBlocks(96)
                    .trackedUpdateRate(2)
                    .build()
    );

    // Rotten Roots: Firefly (ambient)
    public static final EntityType<FireflyEntity> FIREFLY = Registry.register(
            Registries.ENTITY_TYPE,
            FIREFLY_ID,
            FabricEntityTypeBuilder.create(
                            SpawnGroup.AMBIENT,
                            (EntityType<FireflyEntity> t, World w) -> new FireflyEntity(t, w)
                    )
                    .dimensions(EntityDimensions.fixed(0.35f, 0.35f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(2)
                    .build()
    );

    // Conquer: Dark Horse
    public static final EntityType<DarkHorseEntity> DARK_HORSE = Registry.register(
            Registries.ENTITY_TYPE,
            DARK_HORSE_ID,
            FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, DarkHorseEntity::new)
                    .dimensions(EntityDimensions.fixed(1.95507816F, 2.24F))
                    .trackRangeBlocks(96)
                    .trackedUpdateRate(3)
                    .build()
    );

    // Conquer: Corrupted Villager
    public static final EntityType<CorruptedVillagerEntity> CORRUPTED_VILLAGER = Registry.register(
            Registries.ENTITY_TYPE,
            CORRUPTED_VILLAGER_ID,
            FabricEntityTypeBuilder.create(
                            SpawnGroup.MISC,
                            (EntityType<CorruptedVillagerEntity> t, World w) -> new CorruptedVillagerEntity(t, w)
                    )
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .trackRangeBlocks(80)
                    .trackedUpdateRate(3)
                    .build()
    );

    // Conquer: Corrupted Iron Golem
    public static final EntityType<CorruptedIronGolemEntity> CORRUPTED_IRON_GOLEM = Registry.register(
            Registries.ENTITY_TYPE,
            CORRUPTED_IRON_GOLEM_ID,
            FabricEntityTypeBuilder.create(
                            SpawnGroup.MISC,
                            (EntityType<CorruptedIronGolemEntity> t, World w) -> new CorruptedIronGolemEntity(t, w)
                    )
                    .dimensions(EntityDimensions.fixed(1.4f, 2.7f))
                    .trackRangeBlocks(96)
                    .trackedUpdateRate(3)
                    .build()
    );

    // Necromancer corpses
    public static final EntityType<ZombieCorpseEntity> ZOMBIE_CORPSE = Registry.register(
            Registries.ENTITY_TYPE,
            ZOMBIE_CORPSE_ID,
            FabricEntityTypeBuilder.<ZombieCorpseEntity>create(SpawnGroup.MISC, ZombieCorpseEntity::new)
                    .dimensions(EntityDimensions.fixed(0.9f, 0.2f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(1)
                    .build()
    );

    public static final EntityType<SkeletonCorpseEntity> SKELETON_CORPSE = Registry.register(
            Registries.ENTITY_TYPE,
            SKELETON_CORPSE_ID,
            FabricEntityTypeBuilder.<SkeletonCorpseEntity>create(SpawnGroup.MISC, SkeletonCorpseEntity::new)
                    .dimensions(EntityDimensions.fixed(0.9f, 0.2f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(1)
                    .build()
    );

    /**
     * Call this once in mod init (you already do).
     * EntityTypes are already registered above; this is only for attribute registration.
     */
    public static void register() {
        FabricDefaultAttributeRegistry.register(VILLAGER_EVO, VillagerEvoEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(GHOSTLING, GhostlingEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(RIDER_CAR, RiderCarEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(UFO_SAUCER, UfoSaucerEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(OUTERMAN, OuterManEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(PHANTOM_BUDDY, PhantomBuddyEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(FALSE_FROG, net.seep.odd.entity.falsefrog.FalseFrogEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(FIREFLY, FireflyEntity.createAttributes());

        // Conquer
        FabricDefaultAttributeRegistry.register(DARK_HORSE, DarkHorseEntity.createDarkHorseAttributes());
        FabricDefaultAttributeRegistry.register(CORRUPTED_VILLAGER, VillagerEntity.createVillagerAttributes());
        FabricDefaultAttributeRegistry.register(CORRUPTED_IRON_GOLEM, IronGolemEntity.createIronGolemAttributes());

        // Cultist
        FabricDefaultAttributeRegistry.register(SIGHTSEER, SightseerEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SHY_GUY, ShyGuyEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(WEEPING_ANGEL, WeepingAngelEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(CENTIPEDE, CentipedeEntity.createAttributes());

        // Rise
        FabricDefaultAttributeRegistry.register(RISEN_ZOMBIE,
                net.seep.odd.abilities.rise.entity.RisenZombieEntity.createRisenZombieAttributes());
        FabricDefaultAttributeRegistry.register(BOOKLET, BookletEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(CAPYBARA_FAMILIAR, CapybaraFamiliarEntity.createAttributes());



        // NOTE: sniper grapple + climber rope are not LivingEntities (no attributes)
        // NOTE: corpses have no attributes
    }
}
