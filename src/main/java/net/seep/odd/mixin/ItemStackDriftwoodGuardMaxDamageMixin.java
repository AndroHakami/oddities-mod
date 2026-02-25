// src/main/java/net/seep/odd/mixin/ItemStackDriftwoodGuardMaxDamageMixin.java
package net.seep.odd.mixin;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class ItemStackDriftwoodGuardMaxDamageMixin {

    private static final Identifier COAST_ID = new Identifier(Oddities.MOD_ID, "combiner_coast");

    @Inject(method = "getMaxDamage()I", at = @At("RETURN"), cancellable = true)
    private void odd$driftwoodGuardHalveMax(CallbackInfoReturnable<Integer> cir) {
        ItemStack self = (ItemStack)(Object)this;

        if (!(self.getItem() instanceof ShieldItem)) return;

        Enchantment coast = Registries.ENCHANTMENT.getOrEmpty(COAST_ID).orElse(null);
        if (coast == null) return;

        if (EnchantmentHelper.getLevel(coast, self) <= 0) return;

        int original = cir.getReturnValueI();
        if (original <= 1) return;

        cir.setReturnValue(Math.max(1, original / 2));
    }
}