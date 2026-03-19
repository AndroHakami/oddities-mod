package net.seep.odd.block.custom;

import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.seep.odd.fluid.ModFluids;
import net.seep.odd.particles.OddParticles;

public class PoisonFluidBlock extends FluidBlock {
    public PoisonFluidBlock(FlowableFluid fluid, Settings settings) {
        super(fluid, settings);
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        super.onEntityCollision(state, world, pos, entity);

        if (world.isClient) return;
        if (!(entity instanceof LivingEntity living)) return;
        if (!living.isAlive()) return;

        StatusEffectInstance current = living.getStatusEffect(StatusEffects.POISON);
        if (current == null || current.getDuration() <= 24) {
            living.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.POISON,
                    40,
                    0,
                    false,
                    true,
                    true
            ));
        }
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        super.randomDisplayTick(state, world, pos, random);

        if (random.nextInt(2) != 0) return;
        if (ModFluids.STILL_POISON.matchesType(world.getFluidState(pos.up()).getFluid())) return;

        double surfaceY = pos.getY() + world.getFluidState(pos).getHeight(world, pos);
        int count = random.nextInt(5) == 0 ? 2 : 1;

        for (int i = 0; i < count; i++) {
            double x = pos.getX() + 0.15 + random.nextDouble() * 0.70;
            double y = surfaceY + 0.02 + random.nextDouble() * 0.04;
            double z = pos.getZ() + 0.15 + random.nextDouble() * 0.70;

            double vx = (random.nextDouble() - 0.5) * 0.012;
            double vy = 0.008 + random.nextDouble() * 0.018;
            double vz = (random.nextDouble() - 0.5) * 0.012;

            world.addParticle(OddParticles.POISON, x, y, z, vx, vy, vz);
        }
    }
}