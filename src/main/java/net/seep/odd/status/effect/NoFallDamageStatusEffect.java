// src/main/java/net/seep/odd/status/effect/NoFallDamageStatusEffect.java
package net.seep.odd.status.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public class NoFallDamageStatusEffect extends StatusEffect {
    public NoFallDamageStatusEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0x88FFFF);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true; // every tick
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        entity.fallDistance = 0.0f; // ✅ prevents fall damage
    }
}