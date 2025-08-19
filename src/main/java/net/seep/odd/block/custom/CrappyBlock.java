package net.seep.odd.block.custom;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;

public class CrappyBlock extends Block {
    private final int lifespanTicks; // how long before it poofs

    public CrappyBlock(Settings settings, int lifespanTicks) {
        super(settings);
        this.lifespanTicks = lifespanTicks;
    }


    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, net.minecraft.util.math.random.Random random) {
        // If any player is standing on THIS block, don't remove yet — check again soon.
        boolean playerOnTop = !world.getEntitiesByClass(
                net.minecraft.entity.player.PlayerEntity.class,
                // 1-block-high box just above the block
                new net.minecraft.util.math.Box(
                        pos.getX(), pos.getY() + 1, pos.getZ(),
                        pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1
                ),
                p -> p.isAlive() && !p.isSpectator()
        ).isEmpty();

        // Extra safety: also catch the case where their feet are exactly over this block
        if (!playerOnTop) {
            for (var p : world.getPlayers()) {
                if (!p.isSpectator() && p.getBlockPos().down().equals(pos)) {
                    playerOnTop = true;
                    break;
                }
            }
        }

        if (playerOnTop) {
            // Re-check in ~0.5s (10 ticks). You can tweak this.
            world.scheduleBlockTick(pos, this, 10);
            return;
        }

        // No one on it? Poof.
        world.removeBlock(pos, false);
    }
    @Override
    public ItemStack getPickStack(BlockView world, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }
}