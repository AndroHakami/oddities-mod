// src/main/java/net/seep/odd/block/cosmic_katana/CosmicKatanaBlock.java
package net.seep.odd.block.cosmic_katana;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
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

import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.status.ModStatusEffects;

public class CosmicKatanaBlock extends BlockWithEntity implements BlockEntityProvider {

    private static final VoxelShape OUTLINE = Block.createCuboidShape(0, 0, 0, 16, 16, 16);

    public CosmicKatanaBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CosmicKatanaBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) { return OUTLINE; }

    private static boolean isCosmic(ServerPlayerEntity sp) {
        if (sp.hasStatusEffect(ModStatusEffects.POWERLESS)) return false;
        return "cosmic".equals(PowerAPI.get(sp));
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {

        if (world.isClient) return ActionResult.SUCCESS;
        if (!(world instanceof ServerWorld sw)) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.SUCCESS;
        if (!(sw.getBlockEntity(pos) instanceof CosmicKatanaBlockEntity be)) return ActionResult.SUCCESS;

        if (!isCosmic(sp)) {
            // no unleash
            sp.sendMessage(Text.literal("YOU ARE NOT WORTHY..."), true);
            return ActionResult.SUCCESS;
        }

        if (be.isClaiming()) {
            return ActionResult.SUCCESS;
        }

        // Start claim (2.5s) + play unleash immediately (success case only)
        be.serverStartClaim(sp);
        return ActionResult.SUCCESS;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : (w, p, s, be) -> {
            if (be instanceof CosmicKatanaBlockEntity ck) {
                CosmicKatanaBlockEntity.tickServer((ServerWorld) w, p, ck);
            }
        };
    }
}