// src/main/java/net/seep/odd/block/falseflower/spell/BlackholeEffect.java
package net.seep.odd.block.falseflower.spell;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.seep.odd.block.falseflower.FalseFlowerBlockEntity;

public final class BlackholeEffect implements FalseFlowerSpellEffect {
    @Override
    public void tick(ServerWorld w, BlockPos pos, BlockState state, FalseFlowerBlockEntity be, int R, Box box) {
        Vec3d c = Vec3d.ofCenter(pos);
        long time = w.getTime();

        // center "blackhole" + purple outline vibe
        if (time % 2 == 0) {
            w.spawnParticles(ParticleTypes.DRAGON_BREATH, c.x, c.y + 0.15, c.z, 2, 0.12, 0.06, 0.12, 0.0);
            w.spawnParticles(ParticleTypes.PORTAL,       c.x, c.y + 0.15, c.z, 3, 0.22, 0.12, 0.22, 0.02);
        }

        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, Entity::isAlive)) {
            if (!FalseFlowerSpellUtil.insideSphere(e.getPos(), c, R)) continue;

            Vec3d to = c.subtract(e.getPos());
            double d = Math.max(0.6, to.length());
            Vec3d pull = to.normalize().multiply(0.22 / d);

            e.addVelocity(pull.x, pull.y * 0.6, pull.z);
            e.velocityDirty = true;

            // particles “fly into” center
            if (time % 3 == 0) {
                Vec3d p = e.getPos().add(0, 0.9, 0);
                Vec3d v = c.subtract(p).normalize().multiply(0.12);
                w.spawnParticles(ParticleTypes.PORTAL, p.x, p.y, p.z, 1, v.x, v.y, v.z, 0.0);
            }
        }
    }
}
