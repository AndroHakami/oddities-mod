// src/main/java/net/seep/odd/block/ModBlocks.java
package net.seep.odd.block;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
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
import net.seep.odd.block.custom.CrappyBlock;
import net.seep.odd.block.custom.SoundBlock;
import net.seep.odd.block.grandanvil.GrandAnvilBlock;
import net.seep.odd.block.grandanvil.GrandAnvilBlockEntity;
import net.seep.odd.sound.ModSounds;

// NEW: False Flower
import net.seep.odd.block.falseflower.FalseFlowerBlock;
import net.seep.odd.block.falseflower.FalseFlowerBlockEntity;

public class ModBlocks {

    /* ---------------- Existing blocks ---------------- */

    public static final Block RUBY_BLOCK = registerBlock("ruby_block",
            new Block(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).sounds(BlockSoundGroup.AMETHYST_BLOCK)), false);

    public static final Block RAW_RUBY_BLOCK = registerBlock("raw_ruby_block",
            new Block(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).sounds(BlockSoundGroup.AMETHYST_BLOCK)), false);

    public static final Block SOUND_BLOCK = registerBlock("sound_block",
            new SoundBlock(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).sounds(ModSounds.SOUND_BLOCK_SOUNDS)), false);

    // NEW: Glitch Block (indestructible glass-like)
    public static final Block GLITCH_BLOCK = registerBlock("glitch_block",
            new Block(FabricBlockSettings.copyOf(Blocks.GLASS)
                    .strength(-1.0f, 3600000.0f)   // unbreakable / blast-proof
                    .nonOpaque()
                    .sounds(BlockSoundGroup.GLASS)
                    .dropsNothing()

            ),
            false
    );

    public static final Block GRAND_ANVIL = Registry.register(
            Registries.BLOCK,
            new Identifier(Oddities.MOD_ID, "grand_anvil"),
            new GrandAnvilBlock(AbstractBlock.Settings.copy(Blocks.ANVIL)
                    .strength(5.0f, 1200f).requiresTool().nonOpaque())
    );

    public static final BlockEntityType<GrandAnvilBlockEntity> GRAND_ANVIL_BE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            new Identifier(Oddities.MOD_ID, "grand_anvil"),
            FabricBlockEntityTypeBuilder.create(GrandAnvilBlockEntity::new, GRAND_ANVIL).build(null)
    );

    public static final Block CRAPPY_BLOCK = registerBlock("crappy_block",
            new CrappyBlock(
                    AbstractBlock.Settings.copy(Blocks.STONE)
                            .sounds(ModSounds.CRAPPY_BLOCK_SOUNDS)
                            .requiresTool()
                            .nonOpaque()
                            .luminance(state -> 7),
                    80
            ),
            false
    );

    /* ---------------- Potion Mixer (blocks only) ---------------- */

    public static Block POTION_MIXER;
    public static Item  POTION_MIXER_ITEM;
    public static BlockEntityType<PotionMixerBlockEntity> POTION_MIXER_BE;

    /* ---------------- False Flower (Fairy power) ---------------- */

    public static Block FALSE_FLOWER;
    public static Item  FALSE_FLOWER_ITEM;
    public static BlockEntityType<FalseFlowerBlockEntity> FALSE_FLOWER_BE;

    /* ---------------- Helpers ---------------- */

    private static Block registerBlock(String id, Block block, boolean withItem) {
        Identifier rid = new Identifier(Oddities.MOD_ID, id);
        Registry.register(Registries.BLOCK, rid, block);
        if (withItem) {
            Registry.register(Registries.ITEM, rid, new BlockItem(block, new Item.Settings()));
        }
        return block;
    }

    private static Item registerBlockItem(String name, Block block) {
        return Registry.register(Registries.ITEM, new Identifier(Oddities.MOD_ID, name),
                new BlockItem(block, new FabricItemSettings()));
    }

    /* ---------------- Entry point ---------------- */

    public static void registerModBlocks() {
        Oddities.LOGGER.info("Registering ModBlocks for " + Oddities.MOD_ID);

        // Block
        POTION_MIXER = Registry.register(
                Registries.BLOCK,
                new Identifier(Oddities.MOD_ID, "potion_mixer"),
                new PotionMixerMegaBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)
                        .strength(3.0f)
                        .nonOpaque())
        );

        // Block item
        POTION_MIXER_ITEM = Registry.register(
                Registries.ITEM,
                new Identifier(Oddities.MOD_ID, "potion_mixer"),
                new BlockItem(POTION_MIXER, new Item.Settings())
        );

        // Block entity type
        POTION_MIXER_BE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier(Oddities.MOD_ID, "potion_mixer"),
                FabricBlockEntityTypeBuilder.create(PotionMixerBlockEntity::new, POTION_MIXER).build(null)
        );

        /* --------- REGISTER: False Flower --------- */

        // Block
        FALSE_FLOWER = Registry.register(
                Registries.BLOCK,
                new Identifier(Oddities.MOD_ID, "false_flower"),
                new FalseFlowerBlock(
                        AbstractBlock.Settings.copy(Blocks.AZALEA)
                                .nonOpaque()
                                .strength(0.4f)
                                .sounds(BlockSoundGroup.AZALEA)
                )
        );

        // Block item
        FALSE_FLOWER_ITEM = Registry.register(
                Registries.ITEM,
                new Identifier(Oddities.MOD_ID, "false_flower"),
                new BlockItem(FALSE_FLOWER, new Item.Settings())
        );

        // Block entity type
        FALSE_FLOWER_BE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier(Oddities.MOD_ID, "false_flower"),
                FabricBlockEntityTypeBuilder.create(FalseFlowerBlockEntity::new, FALSE_FLOWER).build(null)
        );
    }
}
