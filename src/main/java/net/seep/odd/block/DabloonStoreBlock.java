package net.seep.odd.block;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.seep.odd.device.store.DabloonStoreManager;
import net.seep.odd.device.store.screen.DabloonStoreScreenHandler;
import org.jetbrains.annotations.Nullable;

public final class DabloonStoreBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    public DabloonStoreBlock() {
        super(Settings.create().strength(2.0f).requiresTool());
        setDefaultState(getStateManager().getDefaultState().with(FACING, net.minecraft.util.math.Direction.SOUTH));
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient && placer instanceof PlayerEntity player && world.getBlockEntity(pos) instanceof DabloonStoreBlockEntity be) {
            be.setOwner(player);
        }
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new DabloonStoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? checkType(type, ModBlocks.DABLOON_STORE_BE, DabloonStoreBlockEntity::clientTick) : null;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;

        player.openHandledScreen(new ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(ServerPlayerEntity serverPlayer, PacketByteBuf buf) {
                buf.writeBlockPos(pos);
            }

            @Override
            public Text getDisplayName() {
                return Text.literal("Dabloon Store");
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
                return new DabloonStoreScreenHandler(syncId, inv, pos);
            }
        });
        return ActionResult.CONSUME;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof DabloonStoreBlockEntity be && world instanceof ServerWorld serverWorld) {
                be.dropContents(serverWorld);
                DabloonStoreManager.remove(serverWorld, pos);
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
