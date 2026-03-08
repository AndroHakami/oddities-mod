// FILE: src/main/java/net/seep/odd/status/effect/MistVeilBrokenStatusEffect.java
package net.seep.odd.status.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public final class MistVeilBrokenStatusEffect extends StatusEffect {
    public MistVeilBrokenStatusEffect() {
        super(StatusEffectCategory.HARMFUL, 0x202020);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return false;
    }
}