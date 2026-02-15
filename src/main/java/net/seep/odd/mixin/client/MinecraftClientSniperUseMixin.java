// FILE: src/main/java/net/seep/odd/mixin/client/MinecraftClientSniperUseMixin.java
package net.seep.odd.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.seep.odd.abilities.sniper.item.SniperItem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientSniperUseMixin {

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void odd_cancelUseForSniper(CallbackInfo ci) {
        MinecraftClient mc = (MinecraftClient)(Object)this;
        if (mc.player == null) return;
        if (mc.currentScreen != null) return;

        ItemStack main = mc.player.getMainHandStack();
        ItemStack off  = mc.player.getOffHandStack();
        boolean holding = main.getItem() instanceof SniperItem || off.getItem() instanceof SniperItem;
        if (!holding) return;

        // When RMB held, we want ONLY our scope logic, not vanilla use/block interact.
        if (mc.options.useKey.isPressed()) {
            ci.cancel();
        }
    }
}
