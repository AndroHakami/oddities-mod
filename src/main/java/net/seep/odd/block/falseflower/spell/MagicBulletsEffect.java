// src/main/java/net/seep/odd/block/falseflower/spell/MagicBulletsEffect.java
package net.seep.odd.block.falseflower.spell;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.seep.odd.block.falseflower.FalseFlowerBlockEntity;

public final class MagicBulletsEffect implements FalseFlowerSpellEffect {
    @Override
    public void tick(ServerWorld w, BlockPos pos, BlockState state, FalseFlowerBlockEntity be, int R, Box box) {
        Vec3d c = Vec3d.ofCenter(pos);

        if (w.getTime() % 6 != 0) return;

        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, Entity::isAlive)) {
            if (!FalseFlowerSpellUtil.insideSphere(e.getPos(), c, R)) continue;

            FalseFlowerSpellUtil.damageMagicSafe(w, e, 1.0f);

            Vec3d from = e.getPos().add(
                    (w.random.nextDouble() - 0.5) * 4.0,
                    1.2 + w.random.nextDouble() * 1.2,
                    (w.random.nextDouble() - 0.5) * 4.0
            );
            Vec3d to = e.getPos().add(0, 0.9, 0);
            Vec3d v = to.subtract(from).normalize().multiply(0.35);

            w.spawnParticles(ParticleTypes.CRIMSON_SPORE,
                    from.x, from.y, from.z,
                    2, v.x, v.y, v.z, 0.0);

            w.spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                    to.x, to.y, to.z,
                    1, 0, 0, 0, 0.0);
        }
    }
}
