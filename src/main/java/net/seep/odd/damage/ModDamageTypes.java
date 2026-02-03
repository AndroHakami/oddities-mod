package net.seep.odd.damage;

import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

public final class ModDamageTypes {
    private ModDamageTypes() {}

    public static final RegistryKey<DamageType> NECRO_BOLT =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Oddities.MOD_ID, "necro_bolt"));
}
