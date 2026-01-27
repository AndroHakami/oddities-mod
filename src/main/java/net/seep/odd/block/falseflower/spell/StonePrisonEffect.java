// src/main/java/net/seep/odd/block/falseflower/spell/StonePrisonEffect.java
package net.seep.odd.block.falseflower.spell;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.seep.odd.block.falseflower.FalseFlowerBlockEntity;

import java.util.ArrayList;
import java.util.List;

public final class StonePrisonEffect implements FalseFlowerSpellEffect {
    @Override
    public void tick(ServerWorld w, BlockPos pos, BlockState state, FalseFlowerBlockEntity be, int R, Box box) {
        Vec3d c = Vec3d.ofCenter(pos);

        List<BlockPos> targets = new ArrayList<>();
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, Entity::isAlive)) {
            if (!FalseFlowerSpellUtil.insideSphere(e.getPos(), c, R)) continue;
            targets.add(e.getBlockPos());
        }

        be.spawnStonePrisons(w, targets, 50);
    }
}
