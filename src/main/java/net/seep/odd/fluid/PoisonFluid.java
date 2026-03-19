package net.seep.odd.fluid;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.WaterFluid;
import net.minecraft.item.Item;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.item.ModItems;

public abstract class PoisonFluid extends WaterFluid {

    @Override
    public Fluid getStill() {
        return ModFluids.STILL_POISON;
    }

    @Override
    public Fluid getFlowing() {
        return ModFluids.FLOWING_POISON;
    }

    @Override
    public Item getBucketItem() {
        return ModItems.POISON_BUCKET;
    }

    @Override
    public BlockState toBlockState(FluidState state) {
        return ModBlocks.POISON.getDefaultState()
                .with(FluidBlock.LEVEL, getBlockStateLevel(state));
    }

    @Override
    public boolean matchesType(Fluid fluid) {
        return fluid == ModFluids.STILL_POISON || fluid == ModFluids.FLOWING_POISON;
    }

    public static final class Flowing extends PoisonFluid {
        @Override
        protected void appendProperties(StateManager.Builder<Fluid, FluidState> builder) {
            super.appendProperties(builder);
            builder.add(Properties.LEVEL_1_8);
        }

        @Override
        public int getLevel(FluidState state) {
            return state.get(Properties.LEVEL_1_8);
        }

        @Override
        public boolean isStill(FluidState state) {
            return false;
        }
    }

    public static final class Still extends PoisonFluid {
        @Override
        public int getLevel(FluidState state) {
            return 8;
        }

        @Override
        public boolean isStill(FluidState state) {
            return true;
        }
    }
}