package net.seep.odd.abilities.artificer.mixer;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

import net.seep.odd.abilities.init.ArtificerMixerRegistry;

/**
 * Create-powered Potion Mixer block:
 * - Horizontal facing
 * - Shaft attaches on the back (opposite of facing)
 * - Rotation axis = facing axis
 * - Opens the BE screen on use
 * - Uses Create's IBE<T> to provide the BlockEntity + ticking (no getTicker override!)
 */
public class PotionMixerBlock extends KineticBlock implements IBE<PotionMixerBlockEntity> {

    public PotionMixerBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
    }

    /* -------- state -------- */

    @Override
    protected void appendProperties(StateManager.Builder<net.minecraft.block.Block, BlockState> builder) {
        builder.add(Properties.HORIZONTAL_FACING);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    /* -------- interaction -------- */

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        var be = world.getBlockEntity(pos);
        if (be instanceof PotionMixerBlockEntity mixer) {
            player.openHandledScreen((NamedScreenHandlerFactory) mixer);
            return ActionResult.CONSUME;
        }
        return ActionResult.PASS;
    }

    /* -------- kinetics (IRotate) -------- */

    // NOTE: In your Create jar, IRotate.hasShaftTowards(...) uses WorldView as the first param.
    public boolean hasShaftTowards(WorldView world, BlockPos pos, BlockState state, Direction face) {
        // Connect shaft on the back
        return face == state.get(Properties.HORIZONTAL_FACING).getOpposite();
    }

    public Direction.Axis getRotationAxis(BlockState state) {
        return state.get(Properties.HORIZONTAL_FACING).getAxis();
    }

    /* -------- IBE<PotionMixerBlockEntity> -------- */

    @Override
    public Class<PotionMixerBlockEntity> getBlockEntityClass() {
        return PotionMixerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PotionMixerBlockEntity> getBlockEntityType() {
        return ArtificerMixerRegistry.POTION_MIXER_BE;
    }
}
