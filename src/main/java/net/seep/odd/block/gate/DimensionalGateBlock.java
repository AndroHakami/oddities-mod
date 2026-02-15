package net.seep.odd.block.gate;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;

import net.seep.odd.block.ModBlocks;
import org.jetbrains.annotations.Nullable;

public class DimensionalGateBlock extends BlockWithEntity {
    public static final int WIDTH = 4;
    public static final int HEIGHT = 5;

    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = Properties.OPEN;

    public static final IntProperty DX = IntProperty.of("dx", 0, WIDTH - 1);
    public static final IntProperty DY = IntProperty.of("dy", 0, HEIGHT - 1);

    private static final VoxelShape CLOSED_COLLISION = VoxelShapes.fullCube();
    private static final VoxelShape OPEN_COLLISION = VoxelShapes.empty();

    public DimensionalGateBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(OPEN, false)
                .with(DX, 0)
                .with(DY, 0));
    }

    public static boolean isController(BlockState state) {
        return state.get(DX) == 0 && state.get(DY) == 0;
    }

    public static Direction rightDir(BlockState state) {
        return state.get(FACING).rotateYClockwise();
    }

    public static BlockPos getBasePos(BlockPos partPos, BlockState partState) {
        Direction right = rightDir(partState);
        int dx = partState.get(DX);
        int dy = partState.get(DY);
        return partPos.offset(right.getOpposite(), dx).down(dy);
    }

    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
        World world = ctx.getWorld();
        BlockPos base = ctx.getBlockPos();
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        Direction right = facing.rotateYClockwise();

        if (base.getY() + (HEIGHT - 1) > world.getTopY() - 1) return null;

        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                BlockPos p = base.offset(right, x).up(y);
                if (!world.getBlockState(p).canReplace(ctx)) return null;
            }
        }

        return getDefaultState()
                .with(FACING, facing)
                .with(OPEN, false)
                .with(DX, 0)
                .with(DY, 0);
    }

    @Override
    public void onPlaced(World world, BlockPos base, BlockState state,
                         @Nullable net.minecraft.entity.LivingEntity placer,
                         net.minecraft.item.ItemStack itemStack) {
        if (world.isClient) return;

        Direction right = rightDir(state);

        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                BlockPos p = base.offset(right, x).up(y);
                BlockState part = state.with(DX, x).with(DY, y);
                world.setBlockState(p, part, Block.NOTIFY_ALL);
            }
        }
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;

        BlockPos base = getBasePos(pos, state);
        BlockState baseState = world.getBlockState(base);
        if (!(baseState.getBlock() instanceof DimensionalGateBlock)) return ActionResult.PASS;

        boolean newOpen = !baseState.get(OPEN);
        setOpen(world, base, baseState, newOpen);

        BlockEntity be = world.getBlockEntity(base);
        if (be instanceof DimensionalGateBlockEntity gateBe) {
            gateBe.onOpenStateChanged();
        }

        return ActionResult.CONSUME;
    }

    private static void setOpen(World world, BlockPos base, BlockState baseState, boolean open) {
        Direction right = rightDir(baseState);

        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                BlockPos p = base.offset(right, x).up(y);
                BlockState s = world.getBlockState(p);
                if (s.getBlock() instanceof DimensionalGateBlock) {
                    world.setBlockState(p, s.with(OPEN, open), Block.NOTIFY_ALL);
                }
            }
        }
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient) {
            BlockPos base = getBasePos(pos, state);
            BlockState baseState = world.getBlockState(base);

            if (baseState.getBlock() instanceof DimensionalGateBlock) {
                Direction right = rightDir(baseState);

                for (int x = 0; x < WIDTH; x++) {
                    for (int y = 0; y < HEIGHT; y++) {
                        BlockPos p = base.offset(right, x).up(y);
                        if (p.equals(pos)) continue;

                        BlockState s = world.getBlockState(p);
                        if (s.getBlock() instanceof DimensionalGateBlock) {
                            world.setBlockState(p, net.minecraft.block.Blocks.AIR.getDefaultState(),
                                    Block.NOTIFY_ALL | Block.SKIP_DROPS);
                        }
                    }
                }
            }
        }

        super.onBreak(world, pos, state, player);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return isController(state) ? BlockRenderType.ENTITYBLOCK_ANIMATED : BlockRenderType.INVISIBLE;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return isController(state) ? new DimensionalGateBlockEntity(pos, state) : null;
    }








    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, DX, DY);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, net.minecraft.world.BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(OPEN) ? OPEN_COLLISION : CLOSED_COLLISION;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, net.minecraft.world.BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.fullCube();
    }
}
