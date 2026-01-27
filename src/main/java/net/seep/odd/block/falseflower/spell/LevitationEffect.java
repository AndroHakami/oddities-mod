// src/main/java/net/seep/odd/block/falseflower/spell/LevitationEffect.java
package net.seep.odd.block.falseflower.spell;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.seep.odd.block.falseflower.FalseFlowerBlockEntity;

public final class LevitationEffect implements FalseFlowerSpellEffect {
    @Override
    public void tick(ServerWorld w, BlockPos pos, BlockState state, FalseFlowerBlockEntity be, int R, Box box) {
        Vec3d c = Vec3d.ofCenter(pos);
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, Entity::isAlive)) {
            if (!FalseFlowerSpellUtil.insideSphere(e.getPos(), c, R)) continue;
            // ✅ fixed strength
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 30, 0, true, false));
        }
    }
}
