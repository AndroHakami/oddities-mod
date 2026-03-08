// FILE: src/main/java/net/seep/odd/status/effect/MistyHoverStatusEffect.java
package net.seep.odd.status.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public final class MistyHoverStatusEffect extends StatusEffect {
    public MistyHoverStatusEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0x0E2A52);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return false;
    }
}