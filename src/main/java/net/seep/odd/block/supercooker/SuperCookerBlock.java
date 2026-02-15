package net.seep.odd.block.supercooker;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.seep.odd.abilities.chef.Chef;

public class SuperCookerBlock extends BlockWithEntity implements BlockEntityProvider {
    public static final net.minecraft.state.property.DirectionProperty FACING =
            net.minecraft.state.property.Properties.HORIZONTAL_FACING;

    private static final double U = 1.0 / 16.0;
    private static final double Y_FURNACE_TOP = 8 * U;
    private static final double Y_FRIDGE_TOP  = 12 * U;
    private static final double Y_CAP_TOP     = 14 * U;
    private static final VoxelShape OUTLINE = Block.createCuboidShape(0, 0, 0, 16, 18, 16);

    private enum Part { FURNACE, FRIDGE, COOKTOP }

    public SuperCookerBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(net.minecraft.state.StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SuperCookerBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return OUTLINE; }
    @Override public VoxelShape getRaycastShape(BlockState state, BlockView world, BlockPos pos) { return OUTLINE; }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return VoxelShapes.fullCube(); }

    private static Part partFromHit(BlockPos pos, BlockHitResult hit) {
        double localY = hit.getPos().y - pos.getY();
        if (localY < Y_FURNACE_TOP) return Part.FURNACE;
        if (localY < Y_FRIDGE_TOP)  return Part.FRIDGE;
        if (localY < Y_CAP_TOP)     return Part.FURNACE;
        return Part.COOKTOP;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof SuperCookerBlockEntity be)) return ActionResult.PASS;

        Part part = partFromHit(pos, hit);

        if (!world.isClient) {
            switch (part) {
                case FURNACE -> {
                    player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                            (syncId, playerInv, p) ->
                                    new GenericContainerScreenHandler(
                                            ScreenHandlerType.GENERIC_9X1,
                                            syncId,
                                            (PlayerInventory) playerInv,
                                            be.fuelUiInventory(),
                                            1
                                    ),
                            Text.literal("Super Cooker - Fuel")
                    ));
                    return ActionResult.SUCCESS;
                }
                case FRIDGE -> {
                    world.playSound(null, pos, SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.8f, 1.0f);

                    player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                            (syncId, playerInv, p) ->
                                    new GenericContainerScreenHandler(
                                            ScreenHandlerType.GENERIC_9X3,
                                            syncId,
                                            (PlayerInventory) playerInv,
                                            be.fridgeUiInventory(player), // ✅ shared fridge
                                            3
                                    ),
                            Text.literal("Super Cooker - Fridge")
                    ));
                    return ActionResult.SUCCESS;
                }
                case COOKTOP -> {
                    // finished dish pickup
                    if (be.isFinished()) {
                        if (be.tryGiveResult(player)) {
                            world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.9f, 1.1f);
                        }
                        return ActionResult.SUCCESS;
                    }

                    // ✅ shift-right-click recollect ingredients (only when not cooking)
                    if (player.isSneaking()) {
                        if (be.tryTakeOneIngredient(player)) {
                            world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.8f, 1.2f);
                        } else {
                            player.sendMessage(Text.literal("No ingredients to take."), true);
                        }
                        return ActionResult.SUCCESS;
                    }

                    var held = player.getStackInHand(hand);

                    // add ingredient
                    if (!held.isEmpty() && Chef.isIngredient(held)) {
                        if (be.tryInsertIngredient(player, hand)) {
                            world.playSound(null, pos, SoundEvents.BLOCK_WOOL_PLACE, SoundCategory.BLOCKS, 0.8f, 1.2f);
                        } else {
                            player.sendMessage(Text.literal("Cooktop is full (max 5)."), true);
                        }
                        return ActionResult.SUCCESS;
                    }

                    // stir
                    be.serverStir(player);
                    world.playSound(null, pos, SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.BLOCKS, 0.7f, 1.3f);
                    return ActionResult.SUCCESS;
                }
            }
        }

        return ActionResult.SUCCESS;
    }


    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.getBlock() != newState.getBlock()) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof SuperCookerBlockEntity cooker) {
                ItemScatterer.spawn(world, pos, cooker.asDropInventory());
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    @Override
    public <T extends BlockEntity> net.minecraft.block.entity.BlockEntityTicker<T> getTicker(
            World world, BlockState state, net.minecraft.block.entity.BlockEntityType<T> type
    ) {
        return world.isClient ? null : (w, p, s, be) -> {
            if (be instanceof SuperCookerBlockEntity cooker) {
                SuperCookerBlockEntity.tickServer((net.minecraft.server.world.ServerWorld) w, p, cooker);
            }
        };
    }
}
