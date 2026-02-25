// src/main/java/net/seep/odd/block/combiner/enchant/CombinerWardBulwarkEnchantment.java
package net.seep.odd.block.combiner.enchant;

import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class CombinerWardBulwarkEnchantment extends CombinerEnchantment {

    public CombinerWardBulwarkEnchantment(Rarity rarity, EnchantmentTarget target, EquipmentSlot... slots) {
        super(rarity, target, slots);
    }

    @Override
    public boolean isAcceptableItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item it = stack.getItem();
        return (it instanceof ArmorItem a) && a.getSlotType() == EquipmentSlot.CHEST;
    }

    @Override
    public int getMaxLevel() {
        return 2; // lvl2 can raise the cap a bit (see handler)
    }
}