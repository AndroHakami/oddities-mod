package net.seep.odd.block.false_memory;

import net.minecraft.block.*;

import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.block.entity.BlockEntity;

import net.seep.odd.item.ModItems;
import net.seep.odd.block.ModBlocks;

public class FalseMemoryBlock extends BlockWithEntity implements BlockEntityProvider {

    private static final VoxelShape OUTLINE = Block.createCuboidShape(0, 0, 0, 16, 16, 16);

    public FalseMemoryBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new FalseMemoryBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        return OUTLINE;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {

        if (world.isClient) return ActionResult.SUCCESS;
        if (!(world instanceof ServerWorld sw)) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.SUCCESS;
        if (!(sw.getBlockEntity(pos) instanceof FalseMemoryBlockEntity be)) return ActionResult.SUCCESS;

        ItemStack stack = player.getStackInHand(hand);

        if (!stack.isOf(ModItems.ROTTEN_MASK)) {
            return ActionResult.PASS;
        }

        if (be.isSummoning() || be.hasMaskInserted()) {
            sp.sendMessage(Text.literal("THE MEMORY IS ALREADY AWAKENING..."), true);
            return ActionResult.SUCCESS;
        }

        if (FalseMemoryBlockEntity.isBossWitchAlive(sw)) {
            sp.sendMessage(Text.literal("A BOSS WITCH IS ALREADY PRESENT."), true);
            return ActionResult.SUCCESS;
        }

        be.serverInsertMask(sp, stack);
        return ActionResult.SUCCESS;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : (w, p, s, be) -> {
            if (be instanceof FalseMemoryBlockEntity fm) {
                FalseMemoryBlockEntity.tickServer((ServerWorld) w, p, fm);
            }
        };
    }
}