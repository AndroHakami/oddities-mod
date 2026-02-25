// src/main/java/net/seep/odd/block/combiner/CombinerBlock.java
package net.seep.odd.block.combiner;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import net.seep.odd.block.ModBlocks;

import org.jetbrains.annotations.Nullable;

public class CombinerBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    public CombinerBlock(Settings settings) {
        super(settings.nonOpaque());
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.SOUTH));
    }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING); }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override public BlockState rotate(BlockState state, BlockRotation rot) { return state.with(FACING, rot.rotate(state.get(FACING))); }
    @Override public BlockState mirror(BlockState state, BlockMirror mirror) { return state.rotate(mirror.getRotation(state.get(FACING))); }

    @Override public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.ENTITYBLOCK_ANIMATED; }

    @Nullable @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CombinerBlockEntity(pos, state);
    }

    @Nullable @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : checkType(type, ModBlocks.COMBINER_BE, CombinerBlockEntity::tickServer);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (world.getBlockEntity(pos) instanceof CombinerBlockEntity be) {
            player.openHandledScreen(be);
            return ActionResult.CONSUME;
        }
        return ActionResult.PASS;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.getBlock() != newState.getBlock()) {
            if (!world.isClient && world.getBlockEntity(pos) instanceof CombinerBlockEntity be) {
                be.dropAllContents((net.minecraft.server.world.ServerWorld) world);
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }
}