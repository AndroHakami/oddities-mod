// src/main/java/net/seep/odd/status/effect/FrogLeapStatusEffect.java
package net.seep.odd.status.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public final class FrogLeapStatusEffect extends StatusEffect {

    public FrogLeapStatusEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0x2EF46A);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (entity.getWorld().isClient) return;

        // Jump Boost 2, Speed 2 (hidden vanilla), “bit” of low gravity
        FrogStatusUtil.hiddenSpeedJump(entity, 25, 1, 1);
        FrogStatusUtil.lowGravity(entity, 0.86);
    }
}
