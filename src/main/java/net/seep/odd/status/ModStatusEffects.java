package net.seep.odd.status;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.conquer.effect.CorruptionStatusEffect;
import net.seep.odd.status.effect.FrogLeapStatusEffect;
import net.seep.odd.status.effect.FrogSkinStatusEffect;
import net.seep.odd.status.effect.FrogTongueStatusEffect;
import net.seep.odd.status.effect.FroggyTimeStatusEffect;


/** Central registry for Oddities status effects. Call ModStatusEffects.init() during common init. */
public final class ModStatusEffects {
    private ModStatusEffects() {}

    public static StatusEffect GRAVITY_SUSPEND;
    public static StatusEffect CORRUPTION;
    public static StatusEffect FROG_LEAP;
    public static StatusEffect FROG_TONGUE;
    public static StatusEffect FROG_SKIN;
    public static StatusEffect FROGGY_TIME;
    public static StatusEffect DIVINE_PROTECTION;


    public static void init() {
        GRAVITY_SUSPEND = Registry.register(Registries.STATUS_EFFECT,
                new Identifier(Oddities.MOD_ID, "gravity_suspend"),
                new GravityStatusEffect());
        CORRUPTION = Registry.register(
                Registries.STATUS_EFFECT,
                new Identifier(Oddities.MOD_ID, "corruption"),
                new CorruptionStatusEffect()
        );
        FROG_LEAP = Registry.register(
                Registries.STATUS_EFFECT,
                new Identifier(Oddities.MOD_ID, "frog_leap"),
                new FrogLeapStatusEffect()
        );
        FROG_TONGUE = Registry.register(
                Registries.STATUS_EFFECT,
                new Identifier(Oddities.MOD_ID, "frog_tongue"),
                new FrogTongueStatusEffect()
        );
        FROG_SKIN = Registry.register(
                Registries.STATUS_EFFECT,
                new Identifier(Oddities.MOD_ID, "frog_skin"),
                new FrogSkinStatusEffect()
        );
        FROGGY_TIME = Registry.register(
                Registries.STATUS_EFFECT,
                new Identifier(Oddities.MOD_ID, "froggy_time"),
                new FroggyTimeStatusEffect()
        );
        DIVINE_PROTECTION = Registry.register(
                Registries.STATUS_EFFECT,
                new Identifier(Oddities.MOD_ID, "divine_protection"),
                new DivineProtectionStatusEffect()
        );

    }
}
