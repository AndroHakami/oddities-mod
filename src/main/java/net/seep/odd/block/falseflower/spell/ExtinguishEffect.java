// src/main/java/net/seep/odd/block/falseflower/spell/ExtinguishEffect.java
package net.seep.odd.block.falseflower.spell;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.*;
import net.seep.odd.block.falseflower.FalseFlowerBlockEntity;

public final class ExtinguishEffect implements FalseFlowerSpellEffect {
    @Override
    public void tick(ServerWorld w, BlockPos pos, BlockState state, FalseFlowerBlockEntity be, int R, Box box) {
        int r2 = R * R;
        BlockPos.Mutable m = new BlockPos.Mutable();

        for (int dx = -R; dx <= R; dx++) for (int dy = -R; dy <= R; dy++) for (int dz = -R; dz <= R; dz++) {
            if (dx*dx + dy*dy + dz*dz > r2) continue;
            m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);

            BlockState bs = w.getBlockState(m);
            if (bs.isOf(Blocks.FIRE) || bs.isOf(Blocks.SOUL_FIRE)) {
                w.setBlockState(m, Blocks.AIR.getDefaultState());
                continue;
            }

            FluidState fs = bs.getFluidState();
            if (!fs.isEmpty() && fs.isIn(FluidTags.LAVA)) {
                w.setBlockState(m, Blocks.OBSIDIAN.getDefaultState());
            }
        }

        w.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }
}
