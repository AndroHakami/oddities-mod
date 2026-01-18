package net.seep.odd.abilities.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.conquer.effect.CorruptionStatusEffect;

public class ModEffects {
    public static final StatusEffect CORRUPTION = Registry.register(
            Registries.STATUS_EFFECT,
            new Identifier(Oddities.MOD_ID, "corruption"),
            new CorruptionStatusEffect()
    );

}
