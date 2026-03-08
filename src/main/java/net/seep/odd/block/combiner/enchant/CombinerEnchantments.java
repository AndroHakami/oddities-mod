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
        SENTRY = reg("combiner_sentry",
                new CombinerEnchantment(Enchantment.Rarity.RARE, general, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND));

        COAST  = reg("combiner_coast",
                new CombinerEnchantment(Enchantment.Rarity.RARE, general, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND));

        // Bow + Crossbow intended (enchant can exist on both; effect can choose crossbow-only later)
        WILD   = reg("combiner_wild",
                new CombinerEnchantment(Enchantment.Rarity.RARE, general, EquipmentSlot.MAINHAND));

        // Weapon / boots intents
        VEX    = reg("combiner_vex",
                new CombinerEnchantment(Enchantment.Rarity.RARE, EnchantmentTarget.WEAPON, EquipmentSlot.MAINHAND));

        SPIRE  = reg("combiner_spire",
                new CombinerEnchantment(Enchantment.Rarity.RARE, EnchantmentTarget.ARMOR_FEET, EquipmentSlot.FEET));

        // Trim-mapped custom classes
        DUNE = reg("combiner_dune",
                new CombinerDuneStrideEnchantment(Enchantment.Rarity.RARE, EnchantmentTarget.ARMOR_FEET, EquipmentSlot.FEET));

        WARD = reg("combiner_ward",
                new CombinerWardBulwarkEnchantment(Enchantment.Rarity.RARE, EnchantmentTarget.ARMOR_CHEST, EquipmentSlot.CHEST));

        EYE = reg("combiner_eye",
                new CombinerGazeOfTheEndEnchantment(Enchantment.Rarity.RARE, EnchantmentTarget.ARMOR_HEAD, EquipmentSlot.HEAD));

        TIDE = reg("combiner_tide",
                new CombinerWhirlpoolHarpoonEnchantment(Enchantment.Rarity.RARE, EnchantmentTarget.BREAKABLE, EquipmentSlot.MAINHAND));

        /* =========================
           Starting from SNOUT: do it “like before”
           (i.e., dedicated enchant classes)
           ========================= */

        SNOUT = reg("combiner_snout",
                new CombinerLootScrambleEnchantment(Enchantment.Rarity.RARE, EnchantmentTarget.ARMOR_LEGS, EquipmentSlot.LEGS));

        RIB = reg("combiner_rib",
                new CombinerLeechPlateEnchantment(Enchantment.Rarity.RARE, EnchantmentTarget.ARMOR_CHEST, EquipmentSlot.CHEST));

        WAYFINDER = reg("combiner_wayfinder",
                new CombinerWayfinderRicochetEnchantment(Enchantment.Rarity.RARE, EnchantmentTarget.BOW, EquipmentSlot.MAINHAND));

        SHAPER = reg("combiner_shaper",
                new CombinerShaperGuardEnchantment(Enchantment.Rarity.RARE, EnchantmentTarget.WEAPON, EquipmentSlot.MAINHAND));

        SILENCE = reg("combiner_silence",
                new CombinerMuteBladeEnchantment(Enchantment.Rarity.VERY_RARE, EnchantmentTarget.WEAPON, EquipmentSlot.MAINHAND));

        RAISER = reg("combiner_raiser",
                new CombinerTectonicPistonEnchantment(Enchantment.Rarity.RARE, EnchantmentTarget.ARMOR_LEGS, EquipmentSlot.LEGS));

        HOST = reg("combiner_host",
                new CombinerHostSwapEnchantment(Enchantment.Rarity.RARE, EnchantmentTarget.ARMOR_HEAD, EquipmentSlot.HEAD));

        // (kept as broad placeholders unless you have custom classes for them)

    }

    private static Enchantment reg(String id, Enchantment e) {
        return Registry.register(Registries.ENCHANTMENT, new Identifier(Oddities.MOD_ID, id), e);
    }
}