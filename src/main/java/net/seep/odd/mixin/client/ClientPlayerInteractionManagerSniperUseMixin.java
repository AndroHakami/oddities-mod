// FILE: src/main/java/net/seep/odd/mixin/client/ClientPlayerInteractionManagerSniperUseMixin.java
package net.seep.odd.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.seep.odd.abilities.sniper.item.SniperItem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerSniperUseMixin {

    private static boolean holdingSniper(ClientPlayerEntity p) {
        ItemStack main = p.getMainHandStack();
        ItemStack off  = p.getOffHandStack();
        return main.getItem() instanceof SniperItem || off.getItem() instanceof SniperItem;
    }

    private static boolean shouldCancel(MinecraftClient mc) {
        return mc != null
                && mc.player != null
                && mc.currentScreen == null
                && mc.options.useKey.isPressed()
                && holdingSniper(mc.player);
    }

    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    private void odd_cancelSniperItemUse(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (shouldCancel(MinecraftClient.getInstance())) {
            cir.setReturnValue(ActionResult.PASS); // do nothing, no hand-use spam
        }
    }

    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void odd_cancelSniperBlockUse(ClientPlayerEntity player, Hand hand, BlockHitResult hit,
                                          CallbackInfoReturnable<ActionResult> cir) {
        if (shouldCancel(MinecraftClient.getInstance())) {
            cir.setReturnValue(ActionResult.PASS); // prevents chests/buttons/etc while scoping
        }
    }
}
