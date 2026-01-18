package net.seep.odd.status;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * Divine Protection:
 * - Lasts "forever" (we apply a huge duration)
 * - Removed by milk (vanilla behavior)
 * - Monsters (Sightseer, and future cultist mobs) ignore protected players
 */
public final class DivineProtectionStatusEffect extends StatusEffect {
    public DivineProtectionStatusEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0xEAD9FF);
    }
}
