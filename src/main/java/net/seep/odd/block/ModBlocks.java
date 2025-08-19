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
import net.seep.odd.block.custom.CrappyBlock;
import net.seep.odd.block.grandanvil.GrandAnvilBlock;
import net.seep.odd.block.grandanvil.GrandAnvilBlockEntity;
import net.seep.odd.block.custom.SoundBlock;
import net.seep.odd.sound.ModSounds;

public class ModBlocks {
    public static final Block RUBY_BLOCK = registerBlock("ruby_block",
            new Block(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).sounds(BlockSoundGroup.AMETHYST_BLOCK)), false);
    public static final Block RAW_RUBY_BLOCK = registerBlock("raw_ruby_block",
            new Block(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).sounds(BlockSoundGroup.AMETHYST_BLOCK)), false);
    public static final Block SOUND_BLOCK = registerBlock("sound_block",
            new SoundBlock(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).sounds(ModSounds.SOUND_BLOCK_SOUNDS)), false);
    public static final Block GRAND_ANVIL = Registry.register(
            Registries.BLOCK,
            new Identifier(Oddities.MOD_ID, "grand_anvil"),
            new GrandAnvilBlock(AbstractBlock.Settings.copy(Blocks.ANVIL).strength(5.0f, 1200f).requiresTool().nonOpaque())
    );

    public static final BlockEntityType<GrandAnvilBlockEntity> GRAND_ANVIL_BE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            new Identifier(Oddities.MOD_ID, "grand_anvil"),
            FabricBlockEntityTypeBuilder.create(GrandAnvilBlockEntity::new, GRAND_ANVIL).build(null)
    );




    public static final Block CRAPPY_BLOCK = registerBlock("crappy_block",
            new CrappyBlock(
                    AbstractBlock.Settings.copy(Blocks.STONE)
                            .sounds(ModSounds.CRAPPY_BLOCK_SOUNDS) // “goofy” feel
                            .requiresTool()
                            .nonOpaque()
                            .luminance(state -> 7), // easy to break if needed
                    80 // lifespan ticks (4s)
            ),
            false // set to true if you want a BlockItem
    );

    private static Block registerBlock(String id, Block block, boolean withItem) {
        Identifier rid = new Identifier("odd", id);
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

    public static void registerModBlocks() {
        Oddities.LOGGER.info("Registering ModBlocks for " + Oddities.MOD_ID);
    }
}