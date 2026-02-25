// src/main/java/net/seep/odd/block/combiner/enchant/CombinerDuneStrideEnchantment.java
package net.seep.odd.block.combiner.enchant;

import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class CombinerDuneStrideEnchantment extends CombinerEnchantment {

    public CombinerDuneStrideEnchantment(Rarity rarity, EnchantmentTarget target, EquipmentSlot... slots) {
        super(rarity, target, slots);
    }

    @Override
    public boolean isAcceptableItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item it = stack.getItem();
        return (it instanceof ArmorItem a) && a.getSlotType() == EquipmentSlot.FEET;
    }

    @Override
    public int getMaxLevel() {
        return 2; // lvl1=Speed I, lvl2=Speed II (you can change this)
    }
}