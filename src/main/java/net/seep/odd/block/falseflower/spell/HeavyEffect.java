// src/main/java/net/seep/odd/block/falseflower/spell/HeavyEffect.java
package net.seep.odd.block.falseflower.spell;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.seep.odd.block.falseflower.FalseFlowerBlockEntity;

public final class HeavyEffect implements FalseFlowerSpellEffect {
    @Override
    public void tick(ServerWorld w, BlockPos pos, BlockState state, FalseFlowerBlockEntity be, int R, Box box) {
        Vec3d c = Vec3d.ofCenter(pos);

        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, Entity::isAlive)) {
            if (!FalseFlowerSpellUtil.insideSphere(e.getPos(), c, R)) continue;

            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 2, true, false));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 40, 1, true, false));

            // ✅ strong constant downward force (fixed)
            e.addVelocity(0.0, -0.18, 0.0);
            e.velocityDirty = true;

            // downward “force” indicator (dripping/falling particles)
            if (w.getTime() % 2 == 0) {
                w.spawnParticles(ParticleTypes.DRIPPING_OBSIDIAN_TEAR,
                        e.getX(), e.getY() + 1.6, e.getZ(),
                        2, 0.25, 0.10, 0.25, 0.0);
                w.spawnParticles(ParticleTypes.FALLING_OBSIDIAN_TEAR,
                        e.getX(), e.getY() + 1.3, e.getZ(),
                        1, 0.15, 0.10, 0.15, 0.0);
            }
        }
    }
}
