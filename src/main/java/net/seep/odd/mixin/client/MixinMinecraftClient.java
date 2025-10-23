package net.seep.odd.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.seep.odd.abilities.gamble.item.GambleRevolverItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MixinMinecraftClient {

    private static boolean odd$isHoldingRevolver(MinecraftClient mc) {
        if (mc.player == null) return false;
        ItemStack main = mc.player.getMainHandStack();
        ItemStack off  = mc.player.getOffHandStack();
        return main.getItem() instanceof GambleRevolverItem
                || off.getItem()  instanceof GambleRevolverItem;
    }

    /** Cancel vanilla attack path (prevents hand swing / block hit start / entity hit start) */
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void odd$cancelDoAttack(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient mc = (MinecraftClient)(Object)this;
        if (odd$isHoldingRevolver(mc)) {
            // tell Minecraft “attack handled” -> no vanilla swing/attack
            cir.setReturnValue(false);
        }
    }

    /** Cancel vanilla item use (prevents right-click bob/hide while reloading/using) */
    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void odd$cancelDoItemUse(CallbackInfo ci) {
        MinecraftClient mc = (MinecraftClient)(Object)this;
        if (odd$isHoldingRevolver(mc)) {
            ci.cancel();
        }
    }
}
