package net.seep.odd.abilities.artificer.condenser;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class CondenserBlock extends BlockWithEntity {
    public CondenserBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public CondenserBlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CondenserBlockEntity(pos, state);
    }
    /* ===== Shape ===== */
    private static final VoxelShape SHAPE = makeShape();

    // Your exported shape (0..1 coordinates)
    private static VoxelShape makeShape() {
        VoxelShape shape = VoxelShapes.empty();
        // base slab-ish
        shape = VoxelShapes.combine(shape, VoxelShapes.cuboid(0, 0, 0, 1, 0.6875, 1), BooleanBiFunction.OR);
        // center column / chimney
        shape = VoxelShapes.combine(shape, VoxelShapes.cuboid(0.3125, 0.6875, 0.3125, 0.6875, 1.125, 0.6875), BooleanBiFunction.OR);
        return shape.simplify();
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return super.getOutlineShape(state, world, pos, context);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return super.getCollisionShape(state, world, pos, context);
    }
    // Selection outline in-world



    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient) {
            NamedScreenHandlerFactory f = state.createScreenHandlerFactory(world, pos);
            if (f != null) player.openHandledScreen(f);
        }
        return ActionResult.SUCCESS;
    }

    @Nullable
    @Override
    public NamedScreenHandlerFactory createScreenHandlerFactory(BlockState state, World world, BlockPos pos) {
        var be = world.getBlockEntity(pos);
        if (!(be instanceof CondenserBlockEntity cbe)) return null;

        // Send BlockPos to the client so it can look up the BE
        return new ExtendedScreenHandlerFactory() {
            @Override public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
                buf.writeBlockPos(pos);
            }
            @Override public Text getDisplayName() { return Text.literal("Condenser"); }
            @Override public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
                return new CondenserScreenHandler(syncId, inv, cbe);
            }
        };
    }
}
