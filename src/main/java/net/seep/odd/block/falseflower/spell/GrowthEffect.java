package net.seep.odd.block.falseflower.spell;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Fertilizable;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.seep.odd.block.falseflower.FalseFlowerBlockEntity;

public final class GrowthEffect implements FalseFlowerSpellEffect {
    private static final int TICK_INTERVAL = 10;
    private static final int ATTEMPTS_PER_PULSE = 36;
    private static final int FERTILIZE_PASSES = 2;
    private static final int RANDOM_TICK_PASSES = 3;

    @Override
    public void tick(ServerWorld w, BlockPos pos, BlockState state, FalseFlowerBlockEntity be, int R, Box box) {
        if (w.getTime() % TICK_INTERVAL != 0) return;

        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int i = 0; i < ATTEMPTS_PER_PULSE; i++) {
            m.set(
                    pos.getX() + w.random.nextBetween(-R, R),
                    pos.getY() + w.random.nextBetween(-2, 2),
                    pos.getZ() + w.random.nextBetween(-R, R)
            );

            accelerateGrowthAt(w, m);
        }
    }

    private static void accelerateGrowthAt(ServerWorld w, BlockPos pos) {
        BlockState bs = w.getBlockState(pos);
        if (bs.isAir()) return;

        boolean handled = false;

        if (bs.getBlock() instanceof Fertilizable fertilizable) {
            for (int i = 0; i < FERTILIZE_PASSES; i++) {
                BlockState current = w.getBlockState(pos);
                if (!(current.getBlock() instanceof Fertilizable currentFertilizable)) break;
                if (!currentFertilizable.isFertilizable(w, pos, current, w.isClient())) break;
                if (!currentFertilizable.canGrow(w, w.random, pos, current)) break;

                currentFertilizable.grow(w, w.random, pos, current);
                handled = true;
            }
        }

        if (handled) return;

        if (!isRandomTickGrowthTarget(bs)) return;

        for (int i = 0; i < RANDOM_TICK_PASSES; i++) {
            BlockState current = w.getBlockState(pos);
            if (!isRandomTickGrowthTarget(current) || !current.hasRandomTicks()) break;
            current.randomTick(w, pos, w.random);
        }
    }

    private static boolean isRandomTickGrowthTarget(BlockState state) {
        return state.isIn(BlockTags.CROPS)
                || state.isIn(BlockTags.SAPLINGS)
                || state.isOf(Blocks.NETHER_WART)
                || state.isOf(Blocks.SUGAR_CANE)
                || state.isOf(Blocks.CACTUS)
                || state.isOf(Blocks.BAMBOO)
                || state.isOf(Blocks.COCOA)
                || state.isOf(Blocks.SWEET_BERRY_BUSH)
                || state.isOf(Blocks.KELP)
                || state.isOf(Blocks.TWISTING_VINES)
                || state.isOf(Blocks.WEEPING_VINES)
                || state.isOf(Blocks.CAVE_VINES)
                || state.isOf(Blocks.CAVE_VINES_PLANT);
    }
}