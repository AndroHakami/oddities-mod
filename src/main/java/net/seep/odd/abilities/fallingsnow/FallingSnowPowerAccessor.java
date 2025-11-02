package net.seep.odd.abilities.fallingsnow;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public final class FallingSnowPowerAccessor {
    private FallingSnowPowerAccessor(){}

    // mirror the values in FallingSnowPower
    public static float BIG_DAMAGE() { return 10.0f; }              // 5 hearts
    public static double BIG_KB()   { return 1.1; }                 // knockback factor
    public static StatusEffectInstance bigSlowness() {
        return new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 1, false, true, true);
    }
}
