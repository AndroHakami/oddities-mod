package net.seep.odd.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.seep.odd.abilities.vampire.VampireUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityVampireNoNutritionMixin {

    @Unique private int odd$preFood = -1;
    @Unique private float odd$preSat = -1f;

    @Inject(method = "canConsume(Z)Z", at = @At("HEAD"), cancellable = true)
    private void odd$vampireAlwaysCanConsume(boolean ignoreHunger, CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity self = (PlayerEntity)(Object)this;
        if (VampireUtil.isVampire(self)) {
            // ✅ vampires can always eat
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = "eatFood(Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;",
            at = @At("HEAD")
    )
    private void odd$vampireStoreHunger(World world, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        PlayerEntity self = (PlayerEntity)(Object)this;
        if (!VampireUtil.isVampire(self)) return;

        HungerManager hm = self.getHungerManager();
        odd$preFood = hm.getFoodLevel();
        odd$preSat = hm.getSaturationLevel();
    }

    @Inject(
            method = "eatFood(Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;",
            at = @At("RETURN")
    )
    private void odd$vampireRestoreHunger(World world, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        PlayerEntity self = (PlayerEntity)(Object)this;
        if (!VampireUtil.isVampire(self)) return;

        if (odd$preFood >= 0) {
            HungerManager hm = self.getHungerManager();
            hm.setFoodLevel(odd$preFood);
            hm.setSaturationLevel(Math.max(0f, odd$preSat));
        }
    }
}
