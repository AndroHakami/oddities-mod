package net.seep.odd.block.grandanvil;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;                  // <-- correct ShapeContext import (1.20.1)
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
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.seep.odd.block.ModBlocks;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public class GrandAnvilBlock extends BlockWithEntity {

    // Horizontal facing so the model + hitbox rotate in-world
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    // ====== YOUR BLOCKBENCH VOXEL SHAPE (0..1 space) ======
    // Exported “Voxel shape (Fabric)” uses VoxelShapes.cuboid with normalized coords (0..1).
    private static VoxelShape makeShape() {
        return VoxelShapes.union(
                VoxelShapes.cuboid(0, 0, 0.1875, 0.3125, 0.1875, 0.875),
                VoxelShapes.cuboid(0.6875, 0, 0.1875, 1, 0.1875, 0.875),
                VoxelShapes.cuboid(0.3125, 0.125, 0.25, 0.6875, 0.1875, 0.8125),
                VoxelShapes.cuboid(0.3125, 0.1875, 0.36875, 0.6875, 0.5625, 0.375),
                VoxelShapes.cuboid(0.3125, 0.1875, 0.375, 0.6875, 0.5625, 0.625),
                VoxelShapes.cuboid(0.125, 0.5625, 0.1875, 1, 0.9375, 0.8125),
                VoxelShapes.cuboid(0, 0.625, 0.3125, 0.125, 0.8125, 0.6875),
                VoxelShapes.cuboid(0.3125, 0.0625, 0.1875, 0.6875, 0.09375, 0.8125),
                VoxelShapes.cuboid(0.3125, 0.1875, 0.625, 0.6875, 0.5625, 0.63125),
                VoxelShapes.cuboid(0.125, 0.9375, 0.1875, 1, 0.94375, 0.8125)
        );
    }

    private static final VoxelShape SHAPE_BASE = makeShape();

    // Pre-rotated shapes for each horizontal facing
    private static final Map<Direction, VoxelShape> SHAPES = buildShapes(SHAPE_BASE);

    public GrandAnvilBlock(Settings settings) {
        super(settings.nonOpaque()); // Non-opaque helps non-full-cubes render/light correctly
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.SOUTH));
    }

    // ----- blockstate properties -----
    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // getHorizontalPlayerFacing() is the correct Yarn name in 1.20.1
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

    // ----- model-based rendering (from JSON) -----
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    // ----- outline/collision shapes (rotated) -----
    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES.get(state.get(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES.get(state.get(FACING));
    }

    // ----- BE plumbing -----
    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new GrandAnvilBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : checkType(type, ModBlocks.GRAND_ANVIL_BE, GrandAnvilBlockEntity::tick);
    }

    // ----- open GUI -----
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS; // swing feedback
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof GrandAnvilBlockEntity anvil) {
            player.openHandledScreen(anvil); // BE implements ExtendedScreenHandlerFactory
            return ActionResult.CONSUME;
        }
        return ActionResult.PASS;
    }

    // ===== helper: build rotated shapes in 0..1 coordinate space =====
    private static Map<Direction, VoxelShape> buildShapes(VoxelShape base) {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        map.put(Direction.SOUTH, base);             // assume model's "forward" is SOUTH
        map.put(Direction.WEST,  rotateY01(base, 90));
        map.put(Direction.NORTH, rotateY01(base, 180));
        map.put(Direction.EAST,  rotateY01(base, 270));
        return map;
    }

    /**
     * Rotate a VoxelShape around Y in **0..1** coordinates.
     * 90° step mapping: (x, z) -> (1 - z, x)
     */
    private static VoxelShape rotateY01(VoxelShape shape, int degrees) {
        int steps = Math.floorMod(degrees / 90, 4);
        VoxelShape result = shape;
        for (int s = 0; s < steps; s++) {
            VoxelShape[] out = new VoxelShape[]{ VoxelShapes.empty() };
            result.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) -> {
                // rotate 90° in 0..1 space
                VoxelShape rotated = VoxelShapes.cuboid(
                        1.0 - maxZ, minY, minX,
                        1.0 - minZ, maxY, maxX
                );
                out[0] = VoxelShapes.union(out[0], rotated);
            });
            result = out[0];
        }
        return result;
    }
}
