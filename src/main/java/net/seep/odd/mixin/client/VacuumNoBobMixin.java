// src/main/java/net/seep/odd/mixin/client/VacuumNoBobMixin.java
package net.seep.odd.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.item.ItemStack;
import net.seep.odd.abilities.artificer.item.ArtificerVacuumItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(HeldItemRenderer.class)
public abstract class VacuumNoBobMixin {

    /*
     * Method signature (Yarn 1.20.1):
     * renderFirstPersonItem(
     *   AbstractClientPlayerEntity player, float tickDelta, float pitch,
     *   Hand hand, float swingProgress, ItemStack stack, float equipProgress,
     *   MatrixStack matrices, VertexConsumerProvider vcp, int light
     * )V
     *
     * Among the parameters, the float "ordinals" are:
     *   ordinal 0 = tickDelta
     *   ordinal 1 = pitch
     *   ordinal 2 = swingProgress   <-- we zero this one
     *   ordinal 3 = equipProgress   <-- and this one
     */

    // Zero SWING while using the Vacuum
    @ModifyVariable(
            method =
                    "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FF" +
                            "Lnet/minecraft/util/Hand;F" +
                            "Lnet/minecraft/item/ItemStack;F" +
                            "Lnet/minecraft/client/util/math/MatrixStack;" +
                            "Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"),
            ordinal = 2,            // swingProgress
            argsOnly = true
    )
    private float odd$muteSwing(float swingProgress) {
        return shouldMute() ? 0.0F : swingProgress;
    }

    // Zero EQUIP while using the Vacuum
    @ModifyVariable(
            method =
                    "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FF" +
                            "Lnet/minecraft/util/Hand;F" +
                            "Lnet/minecraft/item/ItemStack;F" +
                            "Lnet/minecraft/client/util/math/MatrixStack;" +
                            "Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"),
            ordinal = 3,            // equipProgress
            argsOnly = true
    )
    private float odd$muteEquip(float equipProgress) {
        return shouldMute() ? 0.0F : equipProgress;
    }

    /** True only while the local player is actively using the Vacuum item. */
    private static boolean shouldMute() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return false;
        if (!mc.player.isUsingItem()) return false;
        ItemStack active = mc.player.getActiveItem();
        return !active.isEmpty() && active.getItem() instanceof ArtificerVacuumItem;
    }
}
