// src/main/java/net/seep/odd/status/ModStatusEffects.java
package net.seep.odd.status;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.conquer.effect.CorruptionStatusEffect;
import net.seep.odd.status.effect.*;

public final class ModStatusEffects {
    private ModStatusEffects() {}

    /* =========================
       Core effects
       ========================= */

    public static final StatusEffect GRAVITY_SUSPEND = reg("gravity_suspend", new GravityStatusEffect());
    public static final StatusEffect CORRUPTION      = reg("corruption", new CorruptionStatusEffect());
    public static final StatusEffect FROG_LEAP       = reg("frog_leap", new FrogLeapStatusEffect());
    public static final StatusEffect FROG_TONGUE     = reg("frog_tongue", new FrogTongueStatusEffect());
    public static final StatusEffect FROG_SKIN       = reg("frog_skin", new FrogSkinStatusEffect());
    public static final StatusEffect FROGGY_TIME     = reg("froggy_time", new FroggyTimeStatusEffect());
    public static final StatusEffect DIVINE_PROTECTION = reg("divine_protection", new DivineProtectionStatusEffect());
    public static final StatusEffect POWERLESS         = reg("powerless", new PowerlessStatusEffect());

    /* =========================
       Chef foods
       ========================= */

    public static final StatusEffect CREEPER_KEBAB_CHARGE = reg("creeper_kebab_charge", new CreeperKebabChargeStatusEffect());
    public static final StatusEffect DEEPDARK_SONIC       = reg("deepdark_sonic", new DeepdarkSonicStatusEffect());
    public static final StatusEffect OUTER_ICECREAM       = reg("outer_icecream", new OuterIcecreamStatusEffect());
    public static final StatusEffect FRIES_TIMER          = reg("fries_timer", new FriesTimerStatusEffect());
    public static final StatusEffect NO_FALL_DAMAGE       = reg("no_fall_damage", new NoFallDamageStatusEffect());
    public static final StatusEffect DRAGON_BURRITO       = reg("dragon_burrito", new DragonBurritoStatusEffect());

    /** Keep this so your existing init calls still work (forces class load). */
    public static void init() {
        // no-op: fields register themselves
    }

    private static StatusEffect reg(String id, StatusEffect effect) {
        return Registry.register(
                Registries.STATUS_EFFECT,
                new Identifier(Oddities.MOD_ID, id),
                effect
        );
    }
}