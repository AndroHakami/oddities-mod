// src/main/java/net/seep/odd/status/effect/DragonBurritoStatusEffect.java
package net.seep.odd.status.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public class DragonBurritoStatusEffect extends StatusEffect {
    public DragonBurritoStatusEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0xFF3A00);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        // no fall damage while buff is active
        entity.fallDistance = 0.0f;
    }
}