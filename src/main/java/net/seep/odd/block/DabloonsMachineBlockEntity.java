package net.seep.odd.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public final class DabloonsMachineBlockEntity extends BlockEntity {
    private static final double SHOW_DISTANCE = 4.5D;

    private float hologramProgress = 0.0f;
    private float prevHologramProgress = 0.0f;

    private float spinDegrees = 0.0f;
    private float prevSpinDegrees = 0.0f;

    public DabloonsMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DABLOONS_MACHINE_BE, pos, state);
    }

    public static void clientTick(World world, BlockPos pos, BlockState state, DabloonsMachineBlockEntity be) {
        be.prevHologramProgress = be.hologramProgress;
        be.prevSpinDegrees = be.spinDegrees;

        PlayerEntity nearest = world.getClosestPlayer(
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D,
                SHOW_DISTANCE,
                false
        );

        float target = nearest != null ? 1.0f : 0.0f;
        float speed = target > be.hologramProgress ? 0.12f : 0.08f;

        be.hologramProgress = approach(be.hologramProgress, target, speed);

        // only start spinning after the pop-out is basically finished
        if (be.hologramProgress > 0.92f) {
            be.spinDegrees = (be.spinDegrees + 2.5f) % 360.0f;
        }
    }

    private static float approach(float current, float target, float amount) {
        if (current < target) {
            return Math.min(current + amount, target);
        }
        return Math.max(current - amount, target);
    }

    public float getHologramProgress(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevHologramProgress, hologramProgress);
    }

    public float getSpinDegrees(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevSpinDegrees, spinDegrees);
    }
}