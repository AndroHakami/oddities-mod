package net.seep.odd.mixin.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.seep.odd.item.ModItems;
import net.seep.odd.item.outerblaster.OuterBlasterItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererOuterBlasterPoseMixin {

    @Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
    private static void odd$outerBlasterThirdPersonPose(
            AbstractClientPlayerEntity player,
            Hand hand,
            CallbackInfoReturnable<BipedEntityModel.ArmPose> cir
    ) {
        ItemStack stack = player.getStackInHand(hand);
        if (!stack.isOf(ModItems.OUTER_BLASTER)) return;

        // keep crossbow hold only while not overheated
        if (OuterBlasterItem.isOverheated(stack)) {
            return;
        }

        cir.setReturnValue(BipedEntityModel.ArmPose.CROSSBOW_HOLD);
    }
}