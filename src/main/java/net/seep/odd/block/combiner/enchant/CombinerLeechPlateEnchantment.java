// src/main/java/net/seep/odd/block/combiner/enchant/CombinerLeechPlateEnchantment.java
package net.seep.odd.block.combiner.enchant;

import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;

public class CombinerLeechPlateEnchantment extends CombinerEnchantment {
    public CombinerLeechPlateEnchantment(Rarity rarity, EnchantmentTarget target, EquipmentSlot... slots) {
        super(rarity, target, slots);
    }

    @Override
    public boolean isAcceptableItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof ArmorItem a)) return false;
        return a.getSlotType() == EquipmentSlot.CHEST;
    }

    @Override public int getMaxLevel() { return 1; }
}