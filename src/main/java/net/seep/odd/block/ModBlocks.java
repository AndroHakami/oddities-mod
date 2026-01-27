// src/main/java/net/seep/odd/block/ModBlocks.java
package net.seep.odd.block;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.artificer.mixer.PotionMixerMegaBlock;
import net.seep.odd.abilities.artificer.mixer.PotionMixerBlockEntity;

import net.seep.odd.block.cultist.CentipedeSpawnBlock;
import net.seep.odd.block.cultist.CentipedeSpawnBlockEntity;

import net.seep.odd.block.custom.CrappyBlock;
import net.seep.odd.block.custom.SoundBlock;

import net.seep.odd.block.grandanvil.GrandAnvilBlock;
import net.seep.odd.block.grandanvil.GrandAnvilBlockEntity;

import net.seep.odd.sound.ModSounds;

// False Flower
import net.seep.odd.block.falseflower.FalseFlowerBlock;
import net.seep.odd.block.falseflower.FalseFlowerBlockEntity;

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

    /* ---------------- Cultist: Centipede Spawn ---------------- */
    public static Block CENTIPEDE_SPAWN;
    public static Item  CENTIPEDE_SPAWN_ITEM;
    public static BlockEntityType<CentipedeSpawnBlockEntity> CENTIPEDE_SPAWN_BE;

    /* ---------------- Grand Anvil ---------------- */
    public static Block GRAND_ANVIL;
    public static BlockEntityType<GrandAnvilBlockEntity> GRAND_ANVIL_BE;

    /* ---------------- Potion Mixer ---------------- */
    public static Block POTION_MIXER;
    public static Item  POTION_MIXER_ITEM;
    public static BlockEntityType<PotionMixerBlockEntity> POTION_MIXER_BE;

    /* ---------------- False Flower ---------------- */
    public static Block FALSE_FLOWER;
    public static Item  FALSE_FLOWER_ITEM;
    public static BlockEntityType<FalseFlowerBlockEntity> FALSE_FLOWER_BE;

    /*---------- DABLOONS ---------------*/
    public static Block DABLOONS_MACHINE;
    public static Item  DABLOONS_MACHINE_ITEM;

    public static void registerModBlocks() {
        Oddities.LOGGER.info("Registering ModBlocks for " + Oddities.MOD_ID);

        /* --------- REGISTER: Centipede Spawn (block + item + BE) --------- */
        CENTIPEDE_SPAWN = Registry.register(
                Registries.BLOCK, id("centipede_spawn"),
                new CentipedeSpawnBlock(AbstractBlock.Settings.copy(Blocks.RESPAWN_ANCHOR).strength(3.5f))
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

        /* --------- REGISTER: Potion Mixer (block + item + BE) --------- */
        POTION_MIXER = Registry.register(
                Registries.BLOCK, id("potion_mixer"),
                new PotionMixerMegaBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)
                        .strength(3.0f)
                        .nonOpaque())
        );

        POTION_MIXER_ITEM = Registry.register(
                Registries.ITEM, id("potion_mixer"),
                new BlockItem(POTION_MIXER, new Item.Settings())
        );

        POTION_MIXER_BE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE, id("potion_mixer_be"),
                FabricBlockEntityTypeBuilder.create(PotionMixerBlockEntity::new, POTION_MIXER).build(null)
        );

        /* --------- REGISTER: False Flower (block + item + BE) --------- */
        FALSE_FLOWER = Registry.register(
                Registries.BLOCK, id("false_flower"),
                new FalseFlowerBlock(
                        AbstractBlock.Settings.copy(Blocks.DANDELION) // plant-like defaults
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
    }
}
