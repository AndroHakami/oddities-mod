// src/main/java/net/seep/odd/block/combiner/enchant/DriftwoodGuardEnchantment.java
package net.seep.odd.block.combiner.enchant;

import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;

public class DriftwoodGuardEnchantment extends CombinerEnchantment {
    public DriftwoodGuardEnchantment(Rarity rarity) {
        super(rarity, EnchantmentTarget.WEARABLE, EquipmentSlot.OFFHAND);
    }

    @Override
    public boolean isAcceptableItem(ItemStack stack) {
        return stack.getItem() instanceof ShieldItem;
    }

    @Override public int getMaxLevel() { return 1; }
}