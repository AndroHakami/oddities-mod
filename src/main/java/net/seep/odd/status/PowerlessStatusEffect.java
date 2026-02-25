package net.seep.odd.status;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public final class PowerlessStatusEffect extends StatusEffect {
    public PowerlessStatusEffect() {
        super(StatusEffectCategory.HARMFUL, 0x3A3A3A);
    }
}
