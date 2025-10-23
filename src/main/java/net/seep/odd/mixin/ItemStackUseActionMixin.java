package net.seep.odd.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.util.UseAction;
import net.seep.odd.abilities.power.SuperChargePower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackUseActionMixin {

    @Inject(method = "getUseAction", at = @At("HEAD"), cancellable = true)
    private void odd$superchargedUseIsSpear(CallbackInfoReturnable<UseAction> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (SuperChargePower.isSupercharged(self)) {
            cir.setReturnValue(UseAction.SPEAR); // trident-style animation
        }
    }

    @Inject(method = "getMaxUseTime", at = @At("HEAD"), cancellable = true)
    private void odd$superchargedLongUse(CallbackInfoReturnable<Integer> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (SuperChargePower.isSupercharged(self)) {
            cir.setReturnValue(72000); // like bows/tridents
        }
    }
}
