// src/main/java/net/seep/odd/block/falseflower/spell/LiftOffEffect.java
package net.seep.odd.block.falseflower.spell;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.*;
import net.seep.odd.block.falseflower.FalseFlowerBlockEntity;

public final class LiftOffEffect implements FalseFlowerSpellEffect {
    @Override
    public void tick(ServerWorld w, BlockPos pos, BlockState state, FalseFlowerBlockEntity be, int R, Box box) {
        Vec3d c = Vec3d.ofCenter(pos);

        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, Entity::isAlive)) {
            if (!FalseFlowerSpellUtil.insideSphere(e.getPos(), c, R)) continue;

            e.addVelocity(0.0, 1.65, 0.0);
            e.velocityDirty = true;

            w.spawnParticles(ParticleTypes.CLOUD,
                    e.getX(), e.getY(), e.getZ(),
                    18, 0.45, 0.10, 0.45, 0.08);

            w.spawnParticles(ParticleTypes.END_ROD,
                    e.getX(), e.getY(), e.getZ(),
                    6, 0.10, 0.10, 0.10, 0.0);
        }

        w.playSound(null, pos, SoundEvents.ENTITY_GHAST_SHOOT, SoundCategory.BLOCKS, 0.8f, 1.4f);
    }
}
