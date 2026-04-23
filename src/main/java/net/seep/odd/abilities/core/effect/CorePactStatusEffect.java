package net.seep.odd.abilities.core.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.seep.odd.abilities.core.CoreLinkManager;

public final class CorePactStatusEffect extends StatusEffect {
    public CorePactStatusEffect() {
        super(StatusEffectCategory.NEUTRAL, 0x8E5BFF);
    }

    @Override
    public void onRemoved(LivingEntity entity, AttributeContainer attributes, int amplifier) {
        super.onRemoved(entity, attributes, amplifier);
        CoreLinkManager.clear(entity);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return false;
    }
}
