// src/main/java/net/seep/odd/block/falseflower/spell/BubbleEffect.java
package net.seep.odd.block.falseflower.spell;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.seep.odd.block.falseflower.FalseFlowerBlockEntity;

public final class BubbleEffect implements FalseFlowerSpellEffect {
    @Override
    public void tick(ServerWorld w, BlockPos pos, BlockState state, FalseFlowerBlockEntity be, int R, Box box) {
        Vec3d c = Vec3d.ofCenter(pos);

        // barrier checks slightly larger than aura box
        Box big = new Box(pos).expand(R + 2, R + 2, R + 2);
        double shell = 0.60;

        // 1) stop entities crossing
        for (Entity e : w.getOtherEntities(null, big, Entity::isAlive)) {
            if (e instanceof ProjectileEntity) continue;

            Vec3d p = e.getPos();
            double d = p.distanceTo(c);

            if (d > (R - shell) && d < (R + shell)) {
                Vec3d n = p.subtract(c);
                if (n.lengthSquared() < 0.0001) n = new Vec3d(0, 1, 0);
                Vec3d dir = n.normalize();
                boolean inside = d <= R;

                Vec3d target = inside ? c.add(dir.multiply(R - 0.85)) : c.add(dir.multiply(R + 0.85));
                e.setVelocity(e.getVelocity().multiply(0.30));
                e.velocityDirty = true;
                e.requestTeleport(target.x, target.y, target.z);
            }
        }

        // 2) stop projectiles crossing
        for (ProjectileEntity pr : w.getEntitiesByClass(ProjectileEntity.class, big, Entity::isAlive)) {
            double d = pr.getPos().distanceTo(c);
            if (d > (R - 0.45) && d < (R + 0.45)) {
                w.spawnParticles(ParticleTypes.BUBBLE_POP,
                        pr.getX(), pr.getY(), pr.getZ(),
                        6, 0.15, 0.15, 0.15, 0.02);
                pr.discard();
            }
        }

        // 3) visual shell sparkles / bubbles
        if (w.getTime() % 2 == 0) {
            double ang = (w.getTime() % 360) * (Math.PI / 180.0);
            double x = c.x + Math.cos(ang) * R;
            double z = c.z + Math.sin(ang) * R;
            double y = c.y + (w.random.nextDouble() - 0.5) * 2.2;

            w.spawnParticles(ParticleTypes.BUBBLE_POP, x, y, z, 1, 0, 0, 0, 0.0);
        }
    }
}
