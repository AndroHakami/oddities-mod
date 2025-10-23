package net.seep.odd.status;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

/** Central registry for Oddities status effects. Call ModStatusEffects.init() during common init. */
public final class ModStatusEffects {
    private ModStatusEffects() {}

    public static StatusEffect GRAVITY_SUSPEND;

    public static void init() {
        GRAVITY_SUSPEND = Registry.register(Registries.STATUS_EFFECT,
                new Identifier(Oddities.MOD_ID, "gravity_suspend"),
                new GravityStatusEffect());
    }
}
