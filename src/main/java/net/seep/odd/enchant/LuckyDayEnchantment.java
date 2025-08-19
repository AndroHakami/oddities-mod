package net.seep.odd.enchant;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;

public class LuckyDayEnchantment extends Enchantment {
    public LuckyDayEnchantment() {
        super(Rarity.RARE, EnchantmentTarget.ARMOR_LEGS, new EquipmentSlot[]{EquipmentSlot.LEGS});
    }

    @Override public int getMaxLevel() { return 1; }
    @Override public int getMinPower(int level) { return 15; }

    // We don’t want this from tables/books – only the Forger should add it:
    @Override public boolean isTreasure() { return true; }
    @Override public boolean isAvailableForRandomSelection() { return false; }
    @Override public boolean isAvailableForEnchantedBookOffer() { return false; }
}
