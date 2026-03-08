package net.seep.odd.block.rotten_roots; // <- change to your package

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PlantBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;

import net.seep.odd.block.ModBlocks; // <- if your BLUE_MUSHROOM_BLOCK lives in ModBlocks

public class BlueMushroomPlantBlock extends PlantBlock {

    public BlueMushroomPlantBlock(Settings settings) {
        super(settings);
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        BlockPos belowPos = pos.down();
        BlockState below = world.getBlockState(belowPos);

        // ✅ allow same special blocks vanilla mushrooms allow
        if (below.isIn(BlockTags.MUSHROOM_GROW_BLOCK)) return true;

        // ✅ also allow your trampoline mushroom block
        if (below.isOf(ModBlocks.BLUE_MUSHROOM_BLOCK)) return true;

        // ✅ vanilla mushroom rule: any full opaque block if light is low enough
        return world.getBaseLightLevel(pos, 0) < 13
                && below.isOpaqueFullCube(world, belowPos);
    }

    @Override
    public boolean canPlantOnTop(BlockState floor, BlockView world, BlockPos pos) {
        // not strictly needed since canPlaceAt handles it,
        // but keeping it consistent helps with edge cases
        return floor.isIn(BlockTags.MUSHROOM_GROW_BLOCK)
                || floor.isOf(ModBlocks.BLUE_MUSHROOM_BLOCK)
                || super.canPlantOnTop(floor, world, pos);
    }
}