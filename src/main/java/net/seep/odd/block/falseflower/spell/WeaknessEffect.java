// src/main/java/net/seep/odd/block/falseflower/spell/WeaknessEffect.java
package net.seep.odd.block.falseflower.spell;

import net.minecraft.block.BlockState;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.seep.odd.block.falseflower.FalseFlowerBlockEntity;

public final class WeaknessEffect implements FalseFlowerSpellEffect {
    @Override
    public void tick(ServerWorld w, BlockPos pos, BlockState state, FalseFlowerBlockEntity be, int R, Box box) {
        Vec3d c = Vec3d.ofCenter(pos);

        for (ServerPlayerEntity sp : w.getPlayers()) {
            if (!FalseFlowerSpellUtil.insideSphere(sp.getPos(), c, R)) continue;

            sp.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 1, true, true));

            if (w.getTime() % 10 == 0) {
                w.spawnParticles(ParticleTypes.ENTITY_EFFECT,
                        sp.getX(), sp.getY() + 0.9, sp.getZ(),
                        2, 0.25, 0.25, 0.25, 0.0);
            }
        }
    }
}
