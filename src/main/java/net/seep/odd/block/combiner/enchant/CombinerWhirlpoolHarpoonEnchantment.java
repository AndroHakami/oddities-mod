// src/main/java/net/seep/odd/block/combiner/enchant/CombinerWhirlpoolHarpoonEnchantment.java
package net.seep.odd.block.combiner.enchant;

import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TridentItem;

/** TIDE trim -> Whirlpool Harpoon (trident-only). */
public class CombinerWhirlpoolHarpoonEnchantment extends CombinerEnchantment {
    public CombinerWhirlpoolHarpoonEnchantment(Rarity rarity, EnchantmentTarget target, EquipmentSlot... slots) {
        super(rarity, target, slots);
    }

    @Override
    public boolean isAcceptableItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof TridentItem;
    }

    @Override public int getMaxLevel() { return 1; }
}