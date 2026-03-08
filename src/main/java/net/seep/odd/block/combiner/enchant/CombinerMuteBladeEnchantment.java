// src/main/java/net/seep/odd/block/combiner/enchant/CombinerMuteBladeEnchantment.java
package net.seep.odd.block.combiner.enchant;

import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;

public class CombinerMuteBladeEnchantment extends CombinerEnchantment {
    public CombinerMuteBladeEnchantment(Rarity rarity, EnchantmentTarget target, EquipmentSlot... slots) {
        super(rarity, target, slots);
    }

    @Override
    public boolean isAcceptableItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof SwordItem;
    }

    @Override public int getMaxLevel() { return 1; }
}