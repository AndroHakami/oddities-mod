package net.seep.odd.enchant;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;

public class ItalianStompersEnchantment extends Enchantment {
    public ItalianStompersEnchantment() {
        super(Rarity.RARE, EnchantmentTarget.ARMOR_FEET, new EquipmentSlot[]{EquipmentSlot.FEET});
    }

    @Override public int getMinLevel() { return 1; }
    @Override public int getMaxLevel() { return 1; }

    // Power values are used by enchanting tables; irrelevant here but must be valid.
    @Override public int getMinPower(int level) { return 1; }
    @Override public int getMaxPower(int level) { return 1; }

    @Override public boolean isTreasure() { return true; } // only via your anvil, not random tables
    @Override public boolean isCursed() { return false; }
}
