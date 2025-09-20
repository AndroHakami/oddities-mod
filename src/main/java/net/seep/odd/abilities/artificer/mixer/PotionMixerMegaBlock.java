package net.seep.odd.abilities.artificer.mixer;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.seep.odd.abilities.init.ArtificerMixerRegistry;
import org.jetbrains.annotations.Nullable;


import java.util.HashMap;
import java.util.Map;

/**
 * 3×3×3 mega version of the Potion Mixer with Create kinetics.
 * - Places a 3x3x3 footprint in one click.
 * - Only the center (controller) has the BlockEntity & kinetic logic.
 * - Any part forwards use/break to the controller.
 * - Voxel shapes per sub-block (placeholder full cubes below).
 * - Render via your BE renderer; block models can be invisible.
 */
public class PotionMixerMegaBlock extends HorizontalKineticBlock
        implements IBE<PotionMixerBlockEntity> {

    // Which cube part am I (relative to controller)?
    public static final EnumProperty<AxisPos> X = EnumProperty.of("xpos", AxisPos.class);
    public static final EnumProperty<AxisPos> Y = EnumProperty.of("ypos", AxisPos.class);
    public static final EnumProperty<AxisPos> Z = EnumProperty.of("zpos", AxisPos.class);
    // True only on the center controller
    public static final BooleanProperty CONTROLLER = BooleanProperty.of("controller");
    // Keep facing (for shafts/renderer)
    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;

    public PotionMixerMegaBlock(Settings settings) {
        super(settings);
        setDefaultState(
                stateManager.getDefaultState()
                        .with(X, AxisPos.ZERO).with(Y, AxisPos.ZERO).with(Z, AxisPos.ZERO)
                        .with(CONTROLLER, true)
                        .with(FACING, Direction.NORTH)
        );
    }

    /* ---------- Create BE wiring via IBE ---------- */

    @Override
    public Class<PotionMixerBlockEntity> getBlockEntityClass() {
        return PotionMixerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PotionMixerBlockEntity> getBlockEntityType() {
        return ArtificerMixerRegistry.POTION_MIXER_BE;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        // Only center creates the BE
        if (!state.get(CONTROLLER)) return null;
        return getBlockEntityType().instantiate(pos, state);
    }

    @Override
    public <T extends BlockEntity> @org.jetbrains.annotations.Nullable BlockEntityTicker<T> getTicker(
            net.minecraft.world.World world,
            net.minecraft.block.BlockState state,
            net.minecraft.block.entity.BlockEntityType<T> type) {

        // Only the controller ticks
        if (!state.get(CONTROLLER)) return null;

        // Match this block's BE type; then delegate to the BE's instance tick()
        if (type == getBlockEntityType()) {
            return (w, pos, st, be) -> {
                if (be instanceof PotionMixerBlockEntity mix) {
                    // Create builds without KineticBlockEntity.standardTick: just run the instance tick
                    mix.tick();
                } else if (be instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity kbe) {
                    // Fallback: some builds keep an instance tick() on KineticBlockEntity as well
                    kbe.tick();
                }
            };
        }
        return null;
    }


    /* ---------- Kinetic connectivity ---------- */

    // Shaft connects on the back (opposite of facing), same as your old mixer.
    @SuppressWarnings("unused")
    public boolean hasShaftTowards(BlockView world, BlockPos pos, BlockState state, Direction face) {
        return state.get(CONTROLLER) && face == state.get(FACING).getOpposite();
    }

    // Rotation axis follows facing axis (E/W = X, N/S = Z) — same as before.
    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.get(FACING).getAxis();
    }

    /* ---------- placement ---------- */

    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        BlockPos center  = ctx.getBlockPos();

        if (!canPlaceCube(ctx.getWorld(), center, ctx)) return null;

        return getDefaultState()
                .with(FACING, facing)
                .with(X, AxisPos.ZERO).with(Y, AxisPos.ZERO).with(Z, AxisPos.ZERO)
                .with(CONTROLLER, true);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         @Nullable net.minecraft.entity.LivingEntity placer, ItemStack itemStack) {
        if (world.isClient) return;

        // Fill surrounding 26 positions with this same block as "parts"
        forEachOffset((dx, dy, dz) -> {
            if (dx == 0 && dy == 0 && dz == 0) return;
            BlockPos p = pos.add(dx, dy, dz);
            BlockState part = getDefaultState()
                    .with(FACING, state.get(FACING))
                    .with(X, AxisPos.of(dx)).with(Y, AxisPos.of(dy)).with(Z, AxisPos.of(dz))
                    .with(CONTROLLER, false);
            world.setBlockState(p, part, Block.NOTIFY_ALL);
        });
    }

    private boolean canPlaceCube(World w, BlockPos center, ItemPlacementContext ctx) {
        return forEachOffsetBool((dx, dy, dz) -> {
            BlockPos p = center.add(dx, dy, dz);
            BlockState bs = w.getBlockState(p);
            // Mirror vanilla "can replace" logic at this position
            ItemPlacementContext probe = new ItemPlacementContext(
                    w, ctx.getPlayer(), ctx.getHand(), ItemStack.EMPTY,
                    new BlockHitResult(Vec3d.ofCenter(p), Direction.UP, p, false)
            );
            return w.canSetBlock(p) && bs.canReplace(probe);
        });
    }

    /* ---------- break / cleanup ---------- */

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.getBlock() != newState.getBlock()) {
            // Remove the entire 3x3x3 when any part is broken
            BlockPos controller = controllerPos(pos, state);
            forEachOffset((dx, dy, dz) -> {
                BlockPos p = controller.add(dx, dy, dz);
                BlockState bs = world.getBlockState(p);
                if (bs.getBlock() == this) world.breakBlock(p, false);
            });
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    /* ---------- interaction forwarding ---------- */

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        BlockPos c = controllerPos(pos, state);
        if (!world.isClient) {
            BlockEntity be = world.getBlockEntity(c);
            if (be instanceof PotionMixerBlockEntity mix) {
                player.openHandledScreen(mix);
                return ActionResult.CONSUME;
            }
        }
        return ActionResult.SUCCESS;
    }

    /* ---------- render: let the BER/GeckoLib draw the big model ---------- */

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE; // the BE renderer will render the whole 3×3×3
    }

    /* ---------- shapes ---------- */

    private static final Map<Long, VoxelShape> SHAPES = new HashMap<>();
    private static long key(int dx, int dy, int dz) { return (((dx + 1) & 3) << 4) | (((dy + 1) & 3) << 2) | ((dz + 1) & 3); }

    static {
        VoxelShape cube = VoxelShapes.cuboid(0, 0, 0, 1, 1, 1); // placeholder
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++)
                    SHAPES.put(key(dx, dy, dz), cube);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        int dx = state.get(X).o, dy = state.get(Y).o, dz = state.get(Z).o;
        return SHAPES.getOrDefault(key(dx, dy, dz), VoxelShapes.fullCube());
    }

    /* ---------- utils ---------- */

    private static BlockPos controllerPos(BlockPos pos, BlockState state) {
        int dx = state.get(X).o, dy = state.get(Y).o, dz = state.get(Z).o;
        return pos.add(-dx, -dy, -dz);
    }

    private interface Off { void run(int dx, int dy, int dz); }
    private interface Test { boolean ok(int dx, int dy, int dz); }

    private static void forEachOffset(Off fn) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++)
                    fn.run(dx, dy, dz);
    }

    private static boolean forEachOffsetBool(Test fn) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++)
                    if (!fn.ok(dx, dy, dz)) return false;
        return true;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(X, Y, Z, CONTROLLER, FACING);
    }
}
