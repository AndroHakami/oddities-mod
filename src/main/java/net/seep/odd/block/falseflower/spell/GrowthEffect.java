// src/main/java/net/seep/odd/block/falseflower/spell/CropGrowthEffect.java
package net.seep.odd.block.falseflower.spell;

import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.seep.odd.block.falseflower.FalseFlowerBlockEntity;

public final class GrowthEffect implements FalseFlowerSpellEffect {
    @Override
    public void tick(ServerWorld w, BlockPos pos, BlockState state, FalseFlowerBlockEntity be, int R, Box box) {
        if (w.getTime() % 10 != 0) return;

        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int i = 0; i < 30; i++) {
            m.set(pos.getX() + w.random.nextBetween(-R, R),
                    pos.getY() + w.random.nextBetween(-2, 2),
                    pos.getZ() + w.random.nextBetween(-R, R));
            BlockState bs = w.getBlockState(m);
            if (bs.isIn(BlockTags.CROPS)) bs.randomTick(w, m, w.random);
        }
    }
}
