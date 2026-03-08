// FILE: src/main/java/net/seep/odd/block/ModBlocks.java
package net.seep.odd.block;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import net.fabricmc.fabric.api.object.builder.v1.block.type.BlockSetTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.block.type.WoodTypeBuilder;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.HangingSignItem;
import net.minecraft.item.Item;
import net.minecraft.item.SignItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import com.terraformersmc.terraform.sign.block.TerraformHangingSignBlock;
import com.terraformersmc.terraform.sign.block.TerraformSignBlock;
import com.terraformersmc.terraform.sign.block.TerraformWallHangingSignBlock;
import com.terraformersmc.terraform.sign.block.TerraformWallSignBlock;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.artificer.mixer.PotionMixerMegaBlock;
import net.seep.odd.abilities.artificer.mixer.PotionMixerBlockEntity;

import net.seep.odd.block.combiner.CombinerBlock;
import net.seep.odd.block.combiner.CombinerBlockEntity;
import net.seep.odd.block.cosmic_katana.CosmicKatanaBlock;
import net.seep.odd.block.cosmic_katana.CosmicKatanaBlockEntity;
import net.seep.odd.block.cultist.CentipedeSpawnBlock;
import net.seep.odd.block.cultist.CentipedeSpawnBlockEntity;

import net.seep.odd.block.custom.CrappyBlock;
import net.seep.odd.block.custom.SoundBlock;

import net.seep.odd.block.grandanvil.GrandAnvilBlock;
import net.seep.odd.block.grandanvil.GrandAnvilBlockEntity;

import net.seep.odd.block.rotten_roots.BlueMushroomPlantBlock;
import net.seep.odd.block.rotten_roots.BlueMushroomTrampolineBlock;
import net.seep.odd.sound.ModSounds;

// False Flower
import net.seep.odd.block.falseflower.FalseFlowerBlock;
import net.seep.odd.block.falseflower.FalseFlowerBlockEntity;

// ✅ Chef: Super Cooker
import net.seep.odd.block.supercooker.SuperCookerBlock;
import net.seep.odd.block.supercooker.SuperCookerBlockEntity;

// ✅ Dimensional Gate (NEW)
import net.seep.odd.block.gate.DimensionalGateBlock;
import net.seep.odd.block.gate.DimensionalGateBlockEntity;

import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.StrippableBlockRegistry;

public class ModBlocks {

    private static Identifier id(String path) {
        return new Identifier(Oddities.MOD_ID, path);
    }

    /* ---------------- Existing blocks ---------------- */

    public static final Block RUBY_BLOCK = Registry.register(
            Registries.BLOCK, id("ruby_block"),
            new Block(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).sounds(BlockSoundGroup.AMETHYST_BLOCK))
    );

    public static final Block RAW_RUBY_BLOCK = Registry.register(
            Registries.BLOCK, id("raw_ruby_block"),
            new Block(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).sounds(BlockSoundGroup.AMETHYST_BLOCK))
    );

    public static final Block COMBINER = Registry.register(
            Registries.BLOCK,
            new Identifier(Oddities.MOD_ID, "combiner"),
            new CombinerBlock(FabricBlockSettings.create().strength(3.5f).nonOpaque())
    );

    public static final BlockEntityType<CombinerBlockEntity> COMBINER_BE =
            Registry.register(Registries.BLOCK_ENTITY_TYPE,
                    new Identifier(Oddities.MOD_ID, "combiner_be"),
                    FabricBlockEntityTypeBuilder.create(CombinerBlockEntity::new, COMBINER).build(null)
            );

    public static final Block SOUND_BLOCK = Registry.register(
            Registries.BLOCK, id("sound_block"),
            new SoundBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).sounds(ModSounds.SOUND_BLOCK_SOUNDS))
    );

    public static final Block GLITCH_BLOCK = Registry.register(
            Registries.BLOCK, id("glitch_block"),
            new Block(AbstractBlock.Settings.copy(Blocks.GLASS)
                    .strength(-1.0f, 3600000.0f)
                    .nonOpaque()
                    .sounds(BlockSoundGroup.GLASS)
                    .dropsNothing())
    );

    public static final Block DREAM_BOOKSHELF = Registry.register(
            Registries.BLOCK, id("dream_bookshelf"),
            new Block(AbstractBlock.Settings.copy(Blocks.BOOKSHELF)
                    .strength(-1.0f, 3600000.0f)
                    .nonOpaque()
                    .sounds(BlockSoundGroup.CHISELED_BOOKSHELF)
                    .dropsNothing())
    );

    public static final Block SUSPICIOUS_BOOKSHELF = Registry.register(
            Registries.BLOCK, id("suspicious_bookshelf"),
            new SuspiciousBookshelfBlock(AbstractBlock.Settings.copy(Blocks.BOOKSHELF)
                    .sounds(BlockSoundGroup.CHISELED_BOOKSHELF))
    );

    public static final Item SUSPICIOUS_BOOKSHELF_ITEM = Registry.register(
            Registries.ITEM, id("suspicious_bookshelf"),
            new BlockItem(SUSPICIOUS_BOOKSHELF, new Item.Settings())
    );

    public static final Block CRAPPY_BLOCK = Registry.register(
            Registries.BLOCK, id("crappy_block"),
            new CrappyBlock(
                    AbstractBlock.Settings.copy(Blocks.STONE)
                            .sounds(ModSounds.CRAPPY_BLOCK_SOUNDS)
                            .requiresTool()
                            .nonOpaque()
                            .luminance(state -> 7),
                    80
            )
    );

    public static final Block DABLOON_BOOKSHELF = Registry.register(
            Registries.BLOCK, id("dabloon_bookshelf"),
            new DabloonBookshelfBlock(AbstractBlock.Settings.copy(Blocks.BOOKSHELF)
                    .sounds(BlockSoundGroup.CHISELED_BOOKSHELF))
    );

    public static final Block USED_DABLOON_BOOKSHELF = Registry.register(
            Registries.BLOCK, id("used_dabloon_bookshelf"),
            new UsedDabloonBookshelfBlock(AbstractBlock.Settings.copy(Blocks.BOOKSHELF)
                    .sounds(BlockSoundGroup.CHISELED_BOOKSHELF))
    );

    public static final Item DABLOON_BOOKSHELF_ITEM = Registry.register(
            Registries.ITEM, id("dabloon_bookshelf"),
            new BlockItem(DABLOON_BOOKSHELF, new Item.Settings())
    );

    public static final Item USED_DABLOON_BOOKSHELF_ITEM = Registry.register(
            Registries.ITEM, id("used_dabloon_bookshelf"),
            new BlockItem(USED_DABLOON_BOOKSHELF, new Item.Settings())
    );

    /* ---------------- Cultist: Centipede Spawn ---------------- */
    public static Block CENTIPEDE_SPAWN;
    public static Item  CENTIPEDE_SPAWN_ITEM;
    public static BlockEntityType<CentipedeSpawnBlockEntity> CENTIPEDE_SPAWN_BE;

    /* ---------------- Grand Anvil ---------------- */
    public static Block GRAND_ANVIL;
    public static BlockEntityType<GrandAnvilBlockEntity> GRAND_ANVIL_BE;

    /* ---------------- False Flower ---------------- */
    public static Block FALSE_FLOWER;
    public static Item  FALSE_FLOWER_ITEM;
    public static BlockEntityType<FalseFlowerBlockEntity> FALSE_FLOWER_BE;

    /*---------- DABLOONS ---------------*/
    public static Block DABLOONS_MACHINE;
    public static Item  DABLOONS_MACHINE_ITEM;

    /* ---------- VAMPIRE: Blood Crystal Blocks ---------- */
    public static Block BLOOD_CRYSTAL_BLOCK;
    public static Item  BLOOD_CRYSTAL_BLOCK_ITEM;

    public static Block BLOOD_CRYSTAL;
    public static Item  BLOOD_CRYSTAL_ITEM;

    /* ---------- CHEF: Super Cooker ---------- */
    public static Block SUPER_COOKER;
    public static Item  SUPER_COOKER_ITEM;
    public static BlockEntityType<SuperCookerBlockEntity> SUPER_COOKER_BE;

    /* ---------- DIMENSIONAL GATE (NEW) ---------- */
    public static Block DIMENSIONAL_GATE;
    public static Item  DIMENSIONAL_GATE_ITEM;
    public static BlockEntityType<DimensionalGateBlockEntity> DIMENSIONAL_GATE_BE;

    // cosmic katana //
    public static Block COSMIC_KATANA_BLOCK;
    public static Item  COSMIC_KATANA_BLOCK_ITEM;
    public static BlockEntityType<CosmicKatanaBlockEntity> COSMIC_KATANA_BLOCK_BE;

    // ---------- BOGGY WOOD SET ----------
    public static Block BOGGY_LOG;
    public static Block STRIPPED_BOGGY_LOG;
    public static Block BOGGY_WOOD;
    public static Block STRIPPED_BOGGY_WOOD;

    public static Block BOGGY_PLANKS;
    public static Block BOGGY_STAIRS;
    public static Block BOGGY_SLAB;

    public static Block BOGGY_FENCE;
    public static Block BOGGY_FENCE_GATE;

    public static Block BOGGY_DOOR;
    public static Block BOGGY_TRAPDOOR;

    public static Block BOGGY_BUTTON;
    public static Block BOGGY_PRESSURE_PLATE;

    // Boggy signs
    public static Block BOGGY_SIGN;
    public static Block BOGGY_WALL_SIGN;


    public static Block BOGGY_HANGING_SIGN;
    public static Block BOGGY_WALL_HANGING_SIGN;

    // Mushroom
    public static Block BLUE_MUSHROOM;
    public static Block BLUE_MUSHROOM_BLOCK;

    public static Item BLUE_MUSHROOM_ITEM;
    public static Item BLUE_MUSHROOM_BLOCK_ITEM;

    public static Block GLOW_SAP;


    // ✅ IMPORTANT: register (not build) so Minecraft/Fabric properly knows about the type

    public static final Identifier BOGGY_WOODTYPE_ID = id("boggy");
    public static final BlockSetType BOGGY_BLOCK_SET =
            BlockSetTypeBuilder.copyOf(BlockSetType.OAK).register(BOGGY_WOODTYPE_ID);
    public static final WoodType BOGGY_WOOD_TYPE =
            WoodTypeBuilder.copyOf(WoodType.OAK).register(BOGGY_WOODTYPE_ID, BOGGY_BLOCK_SET);
    public static final Identifier BOGGY_SIGN_TEXTURE =
            id("entity/signs/boggy");
    public static final Identifier BOGGY_HANGING_SIGN_TEXTURE =
            id("entity/signs/hanging/boggy");
    // yes, Terraform expects the "textures/gui/..." style id like Kaupenjoe
    public static final Identifier BOGGY_HANGING_SIGN_GUI_TEXTURE =
            id("textures/gui/hanging_signs/boggy");

    public static void registerModBlocks() {
        GLOW_SAP = Registry.register(
                Registries.BLOCK, id("glow_sap"),
                new Block(AbstractBlock.Settings.copy(Blocks.GLOWSTONE)
                        .strength(0.3f)
                        .sounds(BlockSoundGroup.HONEY)
                        .luminance(s -> 15))
        );

        Registry.register(
                Registries.ITEM, id("glow_sap"),
                new BlockItem(GLOW_SAP, new FabricItemSettings())
        );
        Oddities.LOGGER.info("Registering ModBlocks for " + Oddities.MOD_ID);
        BLUE_MUSHROOM = Registry.register(
                Registries.BLOCK, id("blue_mushroom"),
                new BlueMushroomPlantBlock(
                        AbstractBlock.Settings.copy(Blocks.RED_MUSHROOM)
                )
        );
        BLUE_MUSHROOM_ITEM = Registry.register(
                Registries.ITEM, id("blue_mushroom"),
                new BlockItem(BLUE_MUSHROOM, new FabricItemSettings())
        );

        BLUE_MUSHROOM_BLOCK = Registry.register(
                Registries.BLOCK, id("blue_mushroom_block"),
                new BlueMushroomTrampolineBlock(
                        AbstractBlock.Settings.copy(Blocks.RED_MUSHROOM_BLOCK)
                )
        );
        BLUE_MUSHROOM_BLOCK_ITEM = Registry.register(
                Registries.ITEM, id("blue_mushroom_block"),
                new BlockItem(BLUE_MUSHROOM_BLOCK, new FabricItemSettings())
        );


        /* =========================
           BOGGY: Signs
           ========================= */




        BOGGY_SIGN = Registry.register(
                Registries.BLOCK, id("boggy_sign"),
                new TerraformSignBlock(BOGGY_SIGN_TEXTURE, AbstractBlock.Settings.copy(Blocks.OAK_SIGN))
        );

        BOGGY_WALL_SIGN = Registry.register(
                Registries.BLOCK, id("boggy_wall_sign"),
                new TerraformWallSignBlock(BOGGY_SIGN_TEXTURE, AbstractBlock.Settings.copy(Blocks.OAK_WALL_SIGN))
        );

        BOGGY_HANGING_SIGN = Registry.register(
                Registries.BLOCK, id("boggy_hanging_sign"),
                new TerraformHangingSignBlock(
                        BOGGY_HANGING_SIGN_TEXTURE,
                        BOGGY_HANGING_SIGN_GUI_TEXTURE,
                        AbstractBlock.Settings.copy(Blocks.OAK_HANGING_SIGN)
                )
        );

        BOGGY_WALL_HANGING_SIGN = Registry.register(
                Registries.BLOCK, id("boggy_wall_hanging_sign"),
                new TerraformWallHangingSignBlock(
                        BOGGY_HANGING_SIGN_TEXTURE,
                        BOGGY_HANGING_SIGN_GUI_TEXTURE,
                        AbstractBlock.Settings.copy(Blocks.OAK_WALL_HANGING_SIGN)
                )
        );



        // NOTE:
        // We DO NOT try to mutate BlockEntityType.SIGN/HANGING_SIGN here (it’s immutable in 1.20.1 and causes crashes).
        // Ignore "invalid for ticking" warnings if you see them; signs don’t tick anyway.

        /* =========================
           BOGGY: Core wood blocks
           ========================= */

        BOGGY_LOG = Registry.register(
                Registries.BLOCK, id("boggy_log"),
                new PillarBlock(AbstractBlock.Settings.copy(Blocks.OAK_LOG))
        );
        Registry.register(Registries.ITEM, id("boggy_log"),
                new BlockItem(BOGGY_LOG, new Item.Settings()));

        STRIPPED_BOGGY_LOG = Registry.register(
                Registries.BLOCK, id("stripped_boggy_log"),
                new PillarBlock(AbstractBlock.Settings.copy(Blocks.STRIPPED_OAK_LOG))
        );
        Registry.register(Registries.ITEM, id("stripped_boggy_log"),
                new BlockItem(STRIPPED_BOGGY_LOG, new Item.Settings()));

        BOGGY_WOOD = Registry.register(
                Registries.BLOCK, id("boggy_wood"),
                new PillarBlock(AbstractBlock.Settings.copy(Blocks.OAK_WOOD))
        );
        Registry.register(Registries.ITEM, id("boggy_wood"),
                new BlockItem(BOGGY_WOOD, new Item.Settings()));

        STRIPPED_BOGGY_WOOD = Registry.register(
                Registries.BLOCK, id("stripped_boggy_wood"),
                new PillarBlock(AbstractBlock.Settings.copy(Blocks.STRIPPED_OAK_WOOD))
        );
        Registry.register(Registries.ITEM, id("stripped_boggy_wood"),
                new BlockItem(STRIPPED_BOGGY_WOOD, new Item.Settings()));

        BOGGY_PLANKS = Registry.register(
                Registries.BLOCK, id("boggy_planks"),
                new Block(AbstractBlock.Settings.copy(Blocks.OAK_PLANKS))
        );
        Registry.register(Registries.ITEM, id("boggy_planks"),
                new BlockItem(BOGGY_PLANKS, new Item.Settings()));

        BOGGY_STAIRS = Registry.register(
                Registries.BLOCK, id("boggy_stairs"),
                new StairsBlock(BOGGY_PLANKS.getDefaultState(), AbstractBlock.Settings.copy(Blocks.OAK_STAIRS))
        );
        Registry.register(Registries.ITEM, id("boggy_stairs"),
                new BlockItem(BOGGY_STAIRS, new Item.Settings()));

        BOGGY_SLAB = Registry.register(
                Registries.BLOCK, id("boggy_slab"),
                new SlabBlock(AbstractBlock.Settings.copy(Blocks.OAK_SLAB))
        );
        Registry.register(Registries.ITEM, id("boggy_slab"),
                new BlockItem(BOGGY_SLAB, new Item.Settings()));

        BOGGY_FENCE = Registry.register(
                Registries.BLOCK, id("boggy_fence"),
                new FenceBlock(AbstractBlock.Settings.copy(Blocks.OAK_FENCE))
        );
        Registry.register(Registries.ITEM, id("boggy_fence"),
                new BlockItem(BOGGY_FENCE, new Item.Settings()));

        BOGGY_FENCE_GATE = Registry.register(
                Registries.BLOCK, id("boggy_fence_gate"),
                new FenceGateBlock(AbstractBlock.Settings.copy(Blocks.OAK_FENCE_GATE), BOGGY_WOOD_TYPE)
        );
        Registry.register(Registries.ITEM, id("boggy_fence_gate"),
                new BlockItem(BOGGY_FENCE_GATE, new Item.Settings()));

        BOGGY_DOOR = Registry.register(
                Registries.BLOCK, id("boggy_door"),
                new DoorBlock(AbstractBlock.Settings.copy(Blocks.OAK_DOOR), BOGGY_BLOCK_SET)
        );
        Registry.register(Registries.ITEM, id("boggy_door"),
                new BlockItem(BOGGY_DOOR, new Item.Settings()));

        BOGGY_TRAPDOOR = Registry.register(
                Registries.BLOCK, id("boggy_trapdoor"),
                new TrapdoorBlock(AbstractBlock.Settings.copy(Blocks.OAK_TRAPDOOR), BOGGY_BLOCK_SET)
        );
        Registry.register(Registries.ITEM, id("boggy_trapdoor"),
                new BlockItem(BOGGY_TRAPDOOR, new Item.Settings()));

        BOGGY_BUTTON = Registry.register(
                Registries.BLOCK, id("boggy_button"),
                new ButtonBlock(AbstractBlock.Settings.copy(Blocks.OAK_BUTTON), BOGGY_BLOCK_SET, 30, true)
        );
        Registry.register(Registries.ITEM, id("boggy_button"),
                new BlockItem(BOGGY_BUTTON, new Item.Settings()));

        BOGGY_PRESSURE_PLATE = Registry.register(
                Registries.BLOCK, id("boggy_pressure_plate"),
                new PressurePlateBlock(PressurePlateBlock.ActivationRule.EVERYTHING,
                        AbstractBlock.Settings.copy(Blocks.OAK_PRESSURE_PLATE),
                        BOGGY_BLOCK_SET)
        );
        Registry.register(Registries.ITEM, id("boggy_pressure_plate"),
                new BlockItem(BOGGY_PRESSURE_PLATE, new Item.Settings()));

        // --- “usual wood behavior” extras: stripping + flammability ---
        StrippableBlockRegistry.register(BOGGY_LOG, STRIPPED_BOGGY_LOG);
        StrippableBlockRegistry.register(BOGGY_WOOD, STRIPPED_BOGGY_WOOD);

        FlammableBlockRegistry flammables = FlammableBlockRegistry.getDefaultInstance();
        flammables.add(BOGGY_PLANKS, 5, 20);
        flammables.add(BOGGY_STAIRS, 5, 20);
        flammables.add(BOGGY_SLAB, 5, 20);
        flammables.add(BOGGY_FENCE, 5, 20);
        flammables.add(BOGGY_FENCE_GATE, 5, 20);
        flammables.add(BOGGY_LOG, 5, 5);
        flammables.add(BOGGY_WOOD, 5, 5);
        flammables.add(STRIPPED_BOGGY_LOG, 5, 5);
        flammables.add(STRIPPED_BOGGY_WOOD, 5, 5);

        /* --------- REGISTER: Centipede Spawn (block + item + BE) --------- */
        CENTIPEDE_SPAWN = Registry.register(
                Registries.BLOCK, id("centipede_spawn"),
                new CentipedeSpawnBlock(AbstractBlock.Settings.copy(Blocks.RESPAWN_ANCHOR).strength(1.0f).requiresTool())
        );

        CENTIPEDE_SPAWN_ITEM = Registry.register(
                Registries.ITEM, id("centipede_spawn"),
                new BlockItem(CENTIPEDE_SPAWN, new Item.Settings())
        );

        CENTIPEDE_SPAWN_BE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE, id("centipede_spawn"),
                FabricBlockEntityTypeBuilder.create(CentipedeSpawnBlockEntity::new, CENTIPEDE_SPAWN).build(null)
        );

        /* --------- REGISTER: Grand Anvil --------- */
        GRAND_ANVIL = Registry.register(
                Registries.BLOCK, id("grand_anvil"),
                new GrandAnvilBlock(AbstractBlock.Settings.copy(Blocks.ANVIL)
                        .strength(5.0f, 1200f).requiresTool().nonOpaque())
        );

        GRAND_ANVIL_BE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE, id("grand_anvil"),
                FabricBlockEntityTypeBuilder.create(GrandAnvilBlockEntity::new, GRAND_ANVIL).build(null)
        );

        /* --------- REGISTER: False Flower (block + item + BE) --------- */
        FALSE_FLOWER = Registry.register(
                Registries.BLOCK, id("false_flower"),
                new FalseFlowerBlock(
                        AbstractBlock.Settings.copy(Blocks.DANDELION)
                                .noCollision()
                                .nonOpaque()
                                .strength(0.4f)
                                .sounds(BlockSoundGroup.GRASS)
                )
        );

        FALSE_FLOWER_ITEM = Registry.register(
                Registries.ITEM, id("false_flower"),
                new BlockItem(FALSE_FLOWER, new FabricItemSettings())
        );

        FALSE_FLOWER_BE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE, id("false_flower_be"),
                FabricBlockEntityTypeBuilder.create(FalseFlowerBlockEntity::new, FALSE_FLOWER).build(null)
        );

        /* --------- REGISTER: Dabloons Machine --------- */
        DABLOONS_MACHINE = Registry.register(
                Registries.BLOCK, id("dabloons_machine"),
                new DabloonsMachineBlock()
        );

        DABLOONS_MACHINE_ITEM = Registry.register(
                Registries.ITEM, id("dabloons_machine"),
                new BlockItem(DABLOONS_MACHINE, new Item.Settings())
        );

        /* --------- REGISTER: Vampire Blood Crystal Blocks --------- */
        BLOOD_CRYSTAL_BLOCK = Registry.register(
                Registries.BLOCK, id("blood_crystal_block"),
                new Block(AbstractBlock.Settings.copy(Blocks.AMETHYST_BLOCK)
                        .strength(-1.0f, 3600000.0f)
                        .dropsNothing()
                        .requiresTool().nonOpaque()
                        .sounds(BlockSoundGroup.AMETHYST_BLOCK))
        );

        BLOOD_CRYSTAL_BLOCK_ITEM = Registry.register(
                Registries.ITEM, id("blood_crystal_block"),
                new BlockItem(BLOOD_CRYSTAL_BLOCK, new Item.Settings())
        );

        BLOOD_CRYSTAL = Registry.register(
                Registries.BLOCK, id("blood_crystal"),
                new Block(AbstractBlock.Settings.copy(Blocks.AMETHYST_BLOCK)
                        .strength(-1.0f, 3600000.0f)
                        .dropsNothing()
                        .sounds(BlockSoundGroup.AMETHYST_BLOCK)
                        .nonOpaque())
        );

        BLOOD_CRYSTAL_ITEM = Registry.register(
                Registries.ITEM, id("blood_crystal"),
                new BlockItem(BLOOD_CRYSTAL, new Item.Settings())
        );

        /* --------- REGISTER: CHEF Super Cooker (block + item + BE) --------- */
        SUPER_COOKER = Registry.register(
                Registries.BLOCK, id("super_cooker"),
                new SuperCookerBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)
                        .strength(2.5f)
                        .nonOpaque())
        );

        SUPER_COOKER_ITEM = Registry.register(
                Registries.ITEM, id("super_cooker"),
                new BlockItem(SUPER_COOKER, new Item.Settings())
        );

        SUPER_COOKER_BE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE, id("super_cooker"),
                FabricBlockEntityTypeBuilder.create(SuperCookerBlockEntity::new, SUPER_COOKER).build(null)
        );

        /* --------- REGISTER: DIMENSIONAL GATE (block + item + BE) --------- */
        DIMENSIONAL_GATE = Registry.register(
                Registries.BLOCK, id("dimensional_gate"),
                new DimensionalGateBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)
                        .strength(4.0f)
                        .requiresTool()
                        .nonOpaque())
        );

        DIMENSIONAL_GATE_ITEM = Registry.register(
                Registries.ITEM, id("dimensional_gate"),
                new BlockItem(DIMENSIONAL_GATE, new Item.Settings())
        );

        DIMENSIONAL_GATE_BE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE, id("dimensional_gate"),
                FabricBlockEntityTypeBuilder.create(DimensionalGateBlockEntity::new, DIMENSIONAL_GATE).build(null)
        );

        // katana block //
        COSMIC_KATANA_BLOCK = Registry.register(
                Registries.BLOCK, id("cosmic_katana_block"),
                new CosmicKatanaBlock(AbstractBlock.Settings.copy(Blocks.OBSIDIAN)
                        .strength(-1.0f, 3600000.0f)
                        .dropsNothing()
                        .nonOpaque())
        );

        COSMIC_KATANA_BLOCK_ITEM = Registry.register(
                Registries.ITEM, id("cosmic_katana_block"),
                new BlockItem(COSMIC_KATANA_BLOCK, new Item.Settings())
        );

        COSMIC_KATANA_BLOCK_BE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE, id("cosmic_katana_block"),
                FabricBlockEntityTypeBuilder.create(CosmicKatanaBlockEntity::new, COSMIC_KATANA_BLOCK).build(null)
        );
    }
}