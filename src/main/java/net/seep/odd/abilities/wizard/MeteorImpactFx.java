// FILE: src/main/java/net/seep/odd/abilities/wizard/MeteorImpactFx.java
package net.seep.odd.abilities.wizard;

import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

public final class MeteorImpactFx {
    private MeteorImpactFx() {}

    public static void apply(ServerWorld world, BlockPos center, float radius, float damage) {
        // damage in a bigger radius (x3 radius, x2 damage already passed in)
        Box box = new Box(center).expand(radius);
        for (var e : world.getEntitiesByClass(LivingEntity.class, box, le -> le.isAlive())) {
            double d2 = e.squaredDistanceTo(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5);
            if (d2 <= (radius * radius)) {
                float falloff = 1.0f - (float)MathHelper.sqrt((float)d2) / radius;
                float finalDmg = Math.max(0.0f, damage * falloff);
                e.damage(world.getDamageSources().explosion(null, null), finalDmg);
                e.setOnFireFor(3);
            }
        }

        // particles feel “big”
        world.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION_EMITTER,
                center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5,
                1, 0, 0, 0, 0);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME,
                center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
                200, radius * 0.35, radius * 0.25, radius * 0.35, 0.03);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.LAVA,
                center.getX() + 0.5, center.getY() + 0.7, center.getZ() + 0.5,
                60, radius * 0.25, radius * 0.15, radius * 0.25, 0.02);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE,
                center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5,
                120, radius * 0.4, radius * 0.25, radius * 0.4, 0.02);

        // crater blocks: magma/netherrack + fire on top
        int r = Math.max(2, MathHelper.ceil(radius * 0.65f)); // feels chunky without nuking too far
        int r2 = r * r;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int dist2 = dx*dx + dz*dz;
                if (dist2 > r2) continue;

                // find surface (walk down a bit)
                int y = center.getY();
                BlockPos.Mutable m = new BlockPos.Mutable(center.getX() + dx, y, center.getZ() + dz);
                for (int i = 0; i < 10; i++) {
                    BlockState st = world.getBlockState(m);
                    if (!st.isAir() && st.getBlock() != Blocks.FIRE && st.getBlock() != Blocks.SOUL_FIRE) break;
                    m.move(0, -1, 0);
                }

                BlockPos surface = m.toImmutable();
                BlockState cur = world.getBlockState(surface);
                if (cur.isOf(Blocks.BEDROCK)) continue;

                float t = 1.0f - (float)MathHelper.sqrt(dist2) / (float)r; // 0 edge -> 1 center
                float roll = world.random.nextFloat();

                // center: mostly magma, edges: more netherrack + scorch
                BlockState place =
                        (roll < (0.65f * t + 0.15f)) ? Blocks.MAGMA_BLOCK.getDefaultState() :
                                (roll < 0.9f) ? Blocks.NETHERRACK.getDefaultState() :
                                        Blocks.BASALT.getDefaultState();

                // only replace soft stuff
                if (cur.isAir() || cur.getBlock() == Blocks.GRASS || cur.getBlock() == Blocks.DIRT || cur.getBlock() == Blocks.SAND || cur.getBlock() == Blocks.GRAVEL) {
                    world.setBlockState(surface, place, Block.NOTIFY_LISTENERS);
                }

                // fire above if possible
                BlockPos above = surface.up();
                if (world.getBlockState(above).isAir() && world.random.nextFloat() < (0.55f * t + 0.10f)) {
                    BlockState fire = AbstractFireBlock.getState(world, above);
                    if (fire.canPlaceAt(world, above)) {
                        world.setBlockState(above, fire, Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }
    }
}
