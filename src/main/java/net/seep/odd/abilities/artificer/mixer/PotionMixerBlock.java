package net.seep.odd.abilities.artificer.mixer;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.seep.odd.block.ModBlocks;
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
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS; // allow client anim

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof PotionMixerBlockEntity mixer) {
            player.openHandledScreen(mixer); // uses BE as factory
            return ActionResult.CONSUME;     // we handled it
        }
        return ActionResult.PASS;
    }

    @Nullable

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PotionMixerBlockEntity(pos, state);
    }
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World w, BlockState s, BlockEntityType<T> type) {
        // if you already have a helper, keep using it; otherwise:
        return type == ModBlocks.POTION_MIXER_BE ? KineticBlockEntity::tick : null;
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
