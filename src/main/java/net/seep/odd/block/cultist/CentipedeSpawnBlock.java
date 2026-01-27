package net.seep.odd.block.cultist;

import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import net.seep.odd.block.ModBlocks;

public final class CentipedeSpawnBlock extends BlockWithEntity {

    public CentipedeSpawnBlock(Settings settings) {
        // IMPORTANT:
        // RespawnAnchor settings copy includes luminance based on CHARGES.
        // Our block does NOT have that property, so we must override luminance here.
        super(settings.luminance(state -> 0));
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CentipedeSpawnBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) return null;

        return checkType(type, ModBlocks.CENTIPEDE_SPAWN_BE, CentipedeSpawnBlockEntity::tick);
    }
}
