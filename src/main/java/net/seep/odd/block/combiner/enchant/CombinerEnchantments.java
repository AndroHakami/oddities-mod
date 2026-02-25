// src/main/java/net/seep/odd/block/combiner/enchant/CombinerEnchantments.java
package net.seep.odd.block.combiner.enchant;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

public final class CombinerEnchantments {
    private CombinerEnchantments() {}

    public static Enchantment SENTRY, DUNE, COAST, WILD, WARD, EYE, VEX, TIDE,
            SNOUT, RIB, SPIRE, WAYFINDER, SHAPER, SILENCE, RAISER, HOST,
            FLOW, BOLT;

    public static void init() {
        // Keep broad; the Combiner recipe + GearKind decides what it can go on.
        var general = EnchantmentTarget.BREAKABLE;

        // Shield-intended (slots only; actual restriction handled by GearKind/recipes)
        SENTRY = reg("combiner_sentry", new CombinerEnchantment(Enchantment.Rarity.RARE, general,
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND));

        COAST  = reg("combiner_coast",  new CombinerEnchantment(Enchantment.Rarity.RARE, general,
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND));

        // Bow + Crossbow intended (effect triggers later however you want; but enchant can exist on both)
        WILD   = reg("combiner_wild",   new CombinerEnchantment(Enchantment.Rarity.RARE, general,
                EquipmentSlot.MAINHAND));

        // Weapon / boots intents
        VEX    = reg("combiner_vex",    new CombinerEnchantment(Enchantment.Rarity.RARE, EnchantmentTarget.WEAPON,
                EquipmentSlot.MAINHAND));

        SPIRE  = reg("combiner_spire",  new CombinerEnchantment(Enchantment.Rarity.RARE, EnchantmentTarget.ARMOR_FEET,
                EquipmentSlot.FEET));

        // Everything else (trim-mapped)
        DUNE =reg("combiner_dune", new CombinerDuneStrideEnchantment(Enchantment.Rarity.RARE, EnchantmentTarget.ARMOR_FEET, EquipmentSlot.FEET));
        WARD = reg("combiner_ward",
                new CombinerWardBulwarkEnchantment(
                        Enchantment.Rarity.RARE,
                        EnchantmentTarget.ARMOR_CHEST,
                        EquipmentSlot.CHEST
                ));
// inside CombinerEnchantments.init()
        EYE = reg("combiner_eye",
                new CombinerGazeOfTheEndEnchantment(Enchantment.Rarity.RARE, EnchantmentTarget.ARMOR_HEAD, EquipmentSlot.HEAD));
        TIDE = reg("combiner_tide",
                new CombinerWhirlpoolHarpoonEnchantment(
                        Enchantment.Rarity.RARE,
                        EnchantmentTarget.BREAKABLE,
                        EquipmentSlot.MAINHAND
                )
        );
        SNOUT      = reg("combiner_snout",      new CombinerEnchantment(Enchantment.Rarity.RARE, general, EquipmentSlot.values()));
        RIB        = reg("combiner_rib",        new CombinerEnchantment(Enchantment.Rarity.RARE, general, EquipmentSlot.values()));
        WAYFINDER  = reg("combiner_wayfinder",  new CombinerEnchantment(Enchantment.Rarity.RARE, general, EquipmentSlot.values()));
        SHAPER     = reg("combiner_shaper",     new CombinerEnchantment(Enchantment.Rarity.RARE, general, EquipmentSlot.values()));
        SILENCE    = reg("combiner_silence",    new CombinerEnchantment(Enchantment.Rarity.VERY_RARE, general, EquipmentSlot.values()));
        RAISER     = reg("combiner_raiser",     new CombinerEnchantment(Enchantment.Rarity.RARE, general, EquipmentSlot.values()));
        HOST       = reg("combiner_host",       new CombinerEnchantment(Enchantment.Rarity.RARE, general, EquipmentSlot.values()));
        FLOW       = reg("combiner_flow",       new CombinerEnchantment(Enchantment.Rarity.VERY_RARE, general, EquipmentSlot.values()));
        BOLT       = reg("combiner_bolt",       new CombinerEnchantment(Enchantment.Rarity.VERY_RARE, general, EquipmentSlot.values()));
    }

    private static Enchantment reg(String id, Enchantment e) {
        return Registry.register(Registries.ENCHANTMENT, new Identifier(Oddities.MOD_ID, id), e);
    }
}