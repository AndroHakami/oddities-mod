package net.seep.odd.block.custom;

import net.minecraft.block.BlockState;
import net.minecraft.block.LeveledCauldronBlock;
import net.minecraft.block.cauldron.CauldronBehavior;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;

public class PoisonCauldronBlock extends LeveledCauldronBlock {
    public PoisonCauldronBlock(Settings settings, Map<net.minecraft.item.Item, CauldronBehavior> behaviorMap) {
        super(settings, precipitation -> false, behaviorMap);
    }

    @Override
    protected boolean canBeFilledByDripstone(net.minecraft.fluid.Fluid fluid) {
        return false;
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        super.onEntityCollision(state, world, pos, entity);

        if (world.isClient()) return;
        if (!(entity instanceof LivingEntity living)) return;

        if (isEntityTouchingFluid(state, pos, living)) {
            // swap this for your custom poison effect if you want
            living.addStatusEffect(new StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.POISON,
                    60,
                    0,
                    false,
                    true,
                    true
            ));
        }
    }
}