// src/main/java/net/seep/odd/block/falseflower/spell/FalseFlowerSpellEffect.java
package net.seep.odd.block.falseflower.spell;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.seep.odd.block.falseflower.FalseFlowerBlockEntity;

public interface FalseFlowerSpellEffect {
    void tick(ServerWorld w, BlockPos pos, BlockState state, FalseFlowerBlockEntity be, int radius, Box box);
}
