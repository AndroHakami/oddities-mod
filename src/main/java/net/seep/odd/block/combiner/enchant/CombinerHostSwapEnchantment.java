// src/main/java/net/seep/odd/block/combiner/enchant/CombinerHostSwapEnchantment.java
package net.seep.odd.block.combiner.enchant;

import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;

public class CombinerHostSwapEnchantment extends CombinerEnchantment {
    public CombinerHostSwapEnchantment(Rarity rarity, EnchantmentTarget target, EquipmentSlot... slots) {
        super(rarity, target, slots);
    }

    @Override
    public boolean isAcceptableItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof ArmorItem a)) return false;
        return a.getSlotType() == EquipmentSlot.HEAD;
    }

    @Override public int getMaxLevel() { return 1; }
}