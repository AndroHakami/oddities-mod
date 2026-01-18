// src/main/java/net/seep/odd/status/effect/FrogSkinStatusEffect.java
package net.seep.odd.status.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public final class FrogSkinStatusEffect extends StatusEffect {

    public FrogSkinStatusEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0xFF66CC);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (entity.getWorld().isClient) return;

        // Regen 1, Resistance 1 (hidden vanilla)
        FrogStatusUtil.hiddenRegenResist(entity, 25, 0, 0);
    }
}
