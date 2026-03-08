package net.seep.odd.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;

import net.seep.odd.abilities.supercharge.SuperHud;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Item.class)
public abstract class SuperChargeItemUseMixin {

    private static boolean isChargingMainHand(ItemStack stack) {
        if (!SuperHud.isChargingVisual()) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return false;

        // ONLY while charging, ONLY main hand item (so it doesn't mess offhand/other items)
        ItemStack main = mc.player.getStackInHand(Hand.MAIN_HAND);
        return stack == main;
    }

    @Inject(method = "getUseAction", at = @At("HEAD"), cancellable = true)
    private void odd$supercharge_forceBlockPose(ItemStack stack, CallbackInfoReturnable<UseAction> cir) {
        if (!isChargingMainHand(stack)) return;
        cir.setReturnValue(UseAction.BLOCK);
    }

    @Inject(method = "getMaxUseTime", at = @At("HEAD"), cancellable = true)
    private void odd$supercharge_forceLongUseTime(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (!isChargingMainHand(stack)) return;
        cir.setReturnValue(72000);
    }
}