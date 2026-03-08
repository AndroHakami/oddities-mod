// src/main/java/net/seep/odd/block/combiner/enchant/CombinerWayfinderRicochetEnchantment.java
package net.seep.odd.block.combiner.enchant;

import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;

public class CombinerWayfinderRicochetEnchantment extends CombinerEnchantment {
    public CombinerWayfinderRicochetEnchantment(Rarity rarity, EnchantmentTarget target, EquipmentSlot... slots) {
        super(rarity, target, slots);
    }

    @Override
    public boolean isAcceptableItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof BowItem;
    }

    @Override public int getMaxLevel() { return 1; }
}