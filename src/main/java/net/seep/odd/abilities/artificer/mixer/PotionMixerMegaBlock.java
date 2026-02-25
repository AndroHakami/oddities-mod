package net.seep.odd.abilities.artificer.mixer;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.seep.odd.abilities.init.ArtificerMixerRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Potion Mixer: 3×3 footprint, 3 blocks tall (3×3×3), bottom-anchored.
 * - Place at the bottom-center.
 * - Controller is bottom-center block (x=0,z=0,y=0).
 * - Parts are y=0..2 and x/z = -1..1.
 * - IMPORTANT: every part HAS a BlockEntity (so Create pipes/shafts work anywhere),
 *   but ONLY the controller stores tanks + opens UI.
 */
public class PotionMixerMegaBlock extends HorizontalKineticBlock implements IBE<PotionMixerBlockEntity> {

    public static final EnumProperty<AxisPos> X = EnumProperty.of("xpos", AxisPos.class);
    public static final EnumProperty<AxisPos> Z = EnumProperty.of("zpos", AxisPos.class);

    // Bottom = 0, middle = 1, top = 2
    public static final IntProperty Y = IntProperty.of("ypos", 0, 2);

    public static final BooleanProperty CONTROLLER = BooleanProperty.of("controller");

    public PotionMixerMegaBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
                .with(X, AxisPos.ZERO)
                .with(Y, 0)
                .with(Z, AxisPos.ZERO)
                .with(CONTROLLER, true)
                // NOTE: HorizontalKineticBlock already defines/owns HORIZONTAL_FACING internally.
                .with(Properties.HORIZONTAL_FACING, Direction.NORTH)
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
        // IMPORTANT: every part gets a BE so pipes/shafts work anywhere
        return getBlockEntityType().instantiate(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (type == getBlockEntityType()) {
            return (w, p, st, be) -> {
                if (be instanceof PotionMixerBlockEntity mix) mix.tick();
            };
        }
        return null;
    }

    /* ---------- Kinetic connectivity ---------- */

    // Create calls one of these overloads depending on version; keep them all.
    public boolean hasShaftTowards(BlockState state, Direction face) {
        // Allow shafts on ANY part block’s “back” face (relative to facing)
        return face == state.get(Properties.HORIZONTAL_FACING).getOpposite();
    }

    @SuppressWarnings("unused")
    public boolean hasShaftTowards(WorldView world, BlockPos pos, BlockState state, Direction face) {
        return hasShaftTowards(state, face);
    }

    @SuppressWarnings("unused")
    public boolean hasShaftTowards(BlockView world, BlockPos pos, BlockState state, Direction face) {
        return hasShaftTowards(state, face);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.get(Properties.HORIZONTAL_FACING).getAxis();
    }

    /* ---------- placement (bottom-anchored 3×3×3) ---------- */

    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        BlockPos base = ctx.getBlockPos(); // bottom-center controller position

        if (!canPlaceCube(ctx, base)) return null;

        return getDefaultState()
                .with(Properties.HORIZONTAL_FACING, facing)
                .with(X, AxisPos.ZERO).with(Y, 0).with(Z, AxisPos.ZERO)
                .with(CONTROLLER, true);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         @Nullable LivingEntity placer, ItemStack itemStack) {
        if (world.isClient) return;
        assemble(world, pos, state);
    }

    /**
     * /setblock won’t run onPlaced; onBlockAdded DOES run.
     * So: controller self-assembles if placed by commands.
     */
    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (world.isClient) return;
        if (state.getBlock() != this) return;

        // Only controller should assemble
        if (!state.get(CONTROLLER)) return;

        // Normalize controller coords (for /setblock with weird props)
        BlockState fixed = state
                .with(X, AxisPos.ZERO)
                .with(Y, 0)
                .with(Z, AxisPos.ZERO)
                .with(CONTROLLER, true);

        if (fixed != state) {
            world.setBlockState(pos, fixed, Block.NOTIFY_ALL);
            state = fixed;
        }

        if (isAssembled(world, pos)) return;

        // For /setblock: only assemble if area is air/replaceable-ish
        if (!canAssembleViaCommand(world, pos)) return;

        assemble(world, pos, state);
    }

    private boolean canPlaceCube(ItemPlacementContext ctx, BlockPos base) {
        World w = ctx.getWorld();

        if (base.getY() + 2 >= w.getTopY()) return false;
        if (base.getY() < w.getBottomY()) return false;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos p = base.add(dx, dy, dz);
                    BlockState bs = w.getBlockState(p);

                    ItemPlacementContext probe = new ItemPlacementContext(
                            ctx.getWorld(),
                            ctx.getPlayer(),
                            ctx.getHand(),
                            ctx.getStack(), // IMPORTANT: not empty, so replace checks behave normally
                            new BlockHitResult(Vec3d.ofCenter(p), Direction.UP, p, false)
                    );

                    if (!w.canSetBlock(p)) return false;
                    if (!bs.canReplace(probe)) return false;
                }
            }
        }
        return true;
    }

    private boolean canAssembleViaCommand(World w, BlockPos base) {
        if (base.getY() + 2 >= w.getTopY()) return false;
        if (base.getY() < w.getBottomY()) return false;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos p = base.add(dx, dy, dz);
                    BlockState bs = w.getBlockState(p);
                    if (!w.canSetBlock(p)) return false;
                    if (!(bs.isAir() || bs.getBlock() == this || bs.isReplaceable())) return false;
                }
            }
        }
        return true;
    }

    private void assemble(World world, BlockPos base, BlockState controllerState) {
        Direction facing = controllerState.get(Properties.HORIZONTAL_FACING);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos p = base.add(dx, dy, dz);

                    boolean isController = (dx == 0 && dy == 0 && dz == 0);

                    BlockState st = getDefaultState()
                            .with(Properties.HORIZONTAL_FACING, facing)
                            .with(X, AxisPos.of(dx))
                            .with(Y, dy)
                            .with(Z, AxisPos.of(dz))
                            .with(CONTROLLER, isController);

                    world.setBlockState(p, st, Block.NOTIFY_ALL);
                }
            }
        }
    }

    private boolean isAssembled(World world, BlockPos base) {
        // quick check: top center exists and is our block
        BlockPos top = base.up(2);
        BlockState s = world.getBlockState(top);
        return s.getBlock() == this && s.contains(Y) && s.get(Y) == 2;
    }

    /* ---------- break / cleanup ---------- */

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!world.isClient && state.getBlock() != newState.getBlock()) {
            BlockPos controller = getControllerPos(pos, state);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        BlockPos p = controller.add(dx, dy, dz);
                        if (p.equals(pos)) continue;

                        BlockState bs = world.getBlockState(p);
                        if (bs.getBlock() == this) {
                            world.setBlockState(p, net.minecraft.block.Blocks.AIR.getDefaultState(),
                                    Block.NOTIFY_ALL | Block.SKIP_DROPS);
                        }
                    }
                }
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    /* ---------- interaction forwarding ---------- */

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        BlockPos c = getControllerPos(pos, state);
        if (!world.isClient) {
            BlockEntity be = world.getBlockEntity(c);
            if (be instanceof PotionMixerBlockEntity mix) {
                mix.openFor(player);
                return ActionResult.CONSUME;
            }
        }
        return ActionResult.SUCCESS;
    }

    /* ---------- render ---------- */

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        // IMPORTANT: you had INVISIBLE, which makes it render literally nothing.
        // MODEL means you’ll at least see missing texture if you haven’t made models yet.
        return BlockRenderType.MODEL;
    }

    /* ---------- shapes (placeholder full cubes) ---------- */

    private static final Map<Integer, VoxelShape> SHAPES = new HashMap<>();

    static {
        VoxelShape cube = VoxelShapes.cuboid(0, 0, 0, 1, 1, 1);
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = 0; dy <= 2; dy++)
                for (int dz = -1; dz <= 1; dz++)
                    SHAPES.put(key(dx, dy, dz), cube);
    }

    private static int key(int dx, int dy, int dz) {
        int ix = dx + 1;
        int iy = dy;
        int iz = dz + 1;
        return ix + (iy * 3) + (iz * 9);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        int dx = state.get(X).o;
        int dy = state.get(Y);
        int dz = state.get(Z).o;
        return SHAPES.getOrDefault(key(dx, dy, dz), VoxelShapes.fullCube());
    }

    /* ---------- controller pos helpers ---------- */

    public static BlockPos getControllerPos(BlockPos pos, BlockState state) {
        int dx = state.get(X).o;
        int dy = state.get(Y);
        int dz = state.get(Z).o;
        return pos.add(-dx, -dy, -dz);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        // DO NOT add facing here — HorizontalKineticBlock already adds it (duplicate-property crash).
        builder.add(X, Y, Z, CONTROLLER);
    }
}
