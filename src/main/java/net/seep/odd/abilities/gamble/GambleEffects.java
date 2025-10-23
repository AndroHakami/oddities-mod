package net.seep.odd.abilities.gamble;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.random.Random;

import java.util.List;
import java.util.function.Function;

/** Curated lists of buffs & debuffs for the revolver’s special rounds. Tweak to taste. */
public final class GambleEffects {
    private GambleEffects() {}

    private static final List<Function<Random, StatusEffectInstance>> DEBUFFS = List.of(
            r -> new StatusEffectInstance(StatusEffects.SLOWNESS,   20 * (6 + r.nextInt(6)),  r.nextBetween(0,1), false, true, true),
            r -> new StatusEffectInstance(StatusEffects.WEAKNESS,   20 * (6 + r.nextInt(6)),  r.nextBetween(0,1), false, true, true),
            r -> new StatusEffectInstance(StatusEffects.POISON,     20 * (5 + r.nextInt(5)),  r.nextBetween(0,1), false, true, true),
            r -> new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 20 * (6 + r.nextInt(6)), 0, false, true, true),
            r -> new StatusEffectInstance(StatusEffects.NAUSEA,     20 * (5 + r.nextInt(4)),  0, false, true, true),
            r -> new StatusEffectInstance(StatusEffects.BLINDNESS,  20 * (4 + r.nextInt(4)),  0, false, true, true)
    );

    private static final List<Function<Random, StatusEffectInstance>> BUFFS = List.of(
            r -> new StatusEffectInstance(StatusEffects.REGENERATION, 20 * (5 + r.nextInt(5)), r.nextBetween(0,1), false, true, true),
            r -> new StatusEffectInstance(StatusEffects.STRENGTH,     20 * (6 + r.nextInt(6)), r.nextBetween(0,1), false, true, true),
            r -> new StatusEffectInstance(StatusEffects.SPEED,        20 * (6 + r.nextInt(6)), r.nextBetween(0,1), false, true, true),
            r -> new StatusEffectInstance(StatusEffects.RESISTANCE,   20 * (5 + r.nextInt(5)), r.nextBetween(0,1), false, true, true),
            r -> new StatusEffectInstance(StatusEffects.ABSORPTION,   20 * (6 + r.nextInt(6)), r.nextBetween(0,2), false, true, true),
            r -> new StatusEffectInstance(StatusEffects.HASTE,        20 * (6 + r.nextInt(6)), r.nextBetween(0,1), false, true, true)
    );

    public static StatusEffectInstance randomDebuff(Random r) {
        return DEBUFFS.get(r.nextInt(DEBUFFS.size())).apply(r);
    }
    public static StatusEffectInstance randomBuff(Random r) {
        return BUFFS.get(r.nextInt(BUFFS.size())).apply(r);
    }

    /** 1-in-5 global fizzle roll (returns true if the effect should be skipped entirely). */
    public static boolean fizzle(Random r) { return r.nextInt(5) == 0; }
}
