package net.seep.odd.abilities.artificer.mixer;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;              // <- this must exist in your Create
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.seep.odd.block.ModBlocks;
import org.jetbrains.annotations.Nullable;

public class PotionMixerBlock extends KineticBlock implements IBE<PotionMixerBlockEntity> {
    public PotionMixerBlock(Settings settings) { super(settings); }

    @Override public Direction.Axis getRotationAxis(BlockState state) { return Direction.Axis.Y; }

    // IBE wires the BE + ticking for you:
    @Override public Class<PotionMixerBlockEntity> getBlockEntityClass() { return PotionMixerBlockEntity.class; }
    @Override public BlockEntityType<? extends PotionMixerBlockEntity> getBlockEntityType() { return ModBlocks.POTION_MIXER_BE; }

    // optional (IBE can also supply a default)
    @Override public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PotionMixerBlockEntity(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        var be = world.getBlockEntity(pos);
        if (be instanceof PotionMixerBlockEntity mixer) {
            player.openHandledScreen(mixer);
            return ActionResult.CONSUME;
        }
        return ActionResult.PASS;
    }
}
