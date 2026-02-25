// src/main/java/net/seep/odd/block/combiner/enchant/CombinerSentryPulseEnchantment.java
package net.seep.odd.block.combiner.enchant;

import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ShieldItem;

/**
 * SENTRY trim -> "Sentry Pulse" (shield-only).
 * Still behaves like your normal CombinerEnchantment (tablet-only etc),
 * but refuses non-shields.
 */
public class CombinerSentryPulseEnchantment extends CombinerEnchantment {

    public CombinerSentryPulseEnchantment(Rarity rarity, EnchantmentTarget target, net.minecraft.entity.EquipmentSlot... slots) {
        super(rarity, target, slots);
    }

    @Override
    public boolean isAcceptableItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof ShieldItem || stack.isOf(Items.SHIELD);
    }

    @Override
    public int getMaxLevel() {
        return 2;
    }
}