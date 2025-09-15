package net.seep.odd.abilities.artificer.mixer;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

public class PotionMixerBlock extends KineticBlock {
    public PotionMixerBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<net.minecraft.block.Block, BlockState> builder) {
        builder.add(Properties.HORIZONTAL_FACING);
    }

    @Nullable

    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PotionMixerBlockEntity(net.seep.odd.abilities.init.ArtificerMixerRegistry.POTION_MIXER_BE, pos, state);
    }


    public boolean hasShaftTowards(BlockView world, BlockPos pos, BlockState state, Direction face) {
        // Shaft connects on the back (opposite of facing)
        return face == state.get(Properties.HORIZONTAL_FACING).getOpposite();
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        // Spin around the block's facing axis (matches Create's expectation here)
        return state.get(Properties.HORIZONTAL_FACING).getAxis();
    }
}
