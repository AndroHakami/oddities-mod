package net.seep.odd.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import net.seep.odd.abilities.power.SuperChargePower;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ArmorItem.class)
public abstract class SuperChargeArmorItemMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void odd$blockEquipIfSupercharged(World world, PlayerEntity user, Hand hand,
                                              CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        ItemStack stack = user.getStackInHand(hand);
        if (SuperChargePower.isSupercharged(stack)) {
            // fail on both sides so the client doesn't predict-equip
            cir.setReturnValue(TypedActionResult.fail(stack));
        }
    }
}