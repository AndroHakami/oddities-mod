// src/main/java/net/seep/odd/status/effect/FriesTimerStatusEffect.java
package net.seep.odd.status.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public class FriesTimerStatusEffect extends StatusEffect {
    public FriesTimerStatusEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0xFFD35A);
    }

    @Override
    public void onRemoved(LivingEntity entity, AttributeContainer attributes, int amplifier) {
        super.onRemoved(entity, attributes, amplifier);

        if (entity.getWorld().isClient) return;

        // after speed ends -> slowness 1 for 2 mins
        entity.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS,
                20 * 60 * 2,
                0,
                true, true, true
        ));
    }
}