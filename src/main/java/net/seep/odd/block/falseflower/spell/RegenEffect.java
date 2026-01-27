// src/main/java/net/seep/odd/block/falseflower/spell/RegenEffect.java
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

public final class RegenEffect implements FalseFlowerSpellEffect {
    @Override
    public void tick(ServerWorld w, BlockPos pos, BlockState state, FalseFlowerBlockEntity be, int R, Box box) {
        Vec3d c = Vec3d.ofCenter(pos);
        long t = w.getTime();

        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, Entity::isAlive)) {
            if (!FalseFlowerSpellUtil.insideSphere(e.getPos(), c, R)) continue;

            e.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 40, 0, true, true));

            if (t % 4 == 0) {
                double ang = (t % 360) * (Math.PI / 180.0);
                double rx = Math.cos(ang) * 0.55;
                double rz = Math.sin(ang) * 0.55;
                w.spawnParticles(ParticleTypes.HEART,
                        e.getX() + rx, e.getY() + 0.9, e.getZ() + rz,
                        1, 0, 0.02, 0, 0.0);
            }
        }
    }
}
