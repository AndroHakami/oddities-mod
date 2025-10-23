package net.seep.odd.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.seep.odd.abilities.rat.food.FoodEatenCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bridge vanilla eating -> FoodEatenCallback (players only). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEatFoodMixin {
    @Inject(method = "eatFood", at = @At("RETURN"))
    private void odd$afterEat(World world, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        if (((Object)this) instanceof PlayerEntity player) {
            FoodComponent comp = stack.getItem().getFoodComponent();
            if (comp != null && !world.isClient) {
                FoodEatenCallback.EVENT.invoker().onEaten(
                        (net.minecraft.server.network.ServerPlayerEntity) player,
                        stack,
                        comp.getHunger(),
                        comp.getSaturationModifier()
                );
            }
        }
    }
}
