// src/main/java/net/seep/odd/block/combiner/enchant/CombinerEnchantment.java
package net.seep.odd.block.combiner.enchant;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;

public class CombinerEnchantment extends Enchantment {
    public CombinerEnchantment(Rarity rarity, EnchantmentTarget target, EquipmentSlot... slots) {
        super(rarity, target, slots);
    }

    @Override public boolean isTreasure() { return true; }
    @Override public boolean isAvailableForRandomSelection() { return false; }
    @Override public boolean isAvailableForEnchantedBookOffer() { return false; }
    @Override public boolean isAcceptableItem(net.minecraft.item.ItemStack stack) { return super.isAcceptableItem(stack); }
}