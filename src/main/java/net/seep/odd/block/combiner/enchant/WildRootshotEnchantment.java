// src/main/java/net/seep/odd/block/combiner/enchant/WildRootshotEnchantment.java
package net.seep.odd.block.combiner.enchant;

import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;

public class WildRootshotEnchantment extends CombinerEnchantment {
    public WildRootshotEnchantment(Rarity rarity) {
        super(rarity, EnchantmentTarget.CROSSBOW, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND);
    }

    @Override
    public boolean isAcceptableItem(ItemStack stack) {
        return stack.getItem() instanceof CrossbowItem;
    }

    @Override public int getMaxLevel() { return 1; }
}