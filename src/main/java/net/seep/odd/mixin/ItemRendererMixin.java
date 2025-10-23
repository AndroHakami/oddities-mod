// File: src/main/java/net/seep/odd/mixin/ItemRendererMixin.java
package net.seep.odd.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.power.SuperChargePower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    // keep around if you later decide to draw a pass here
    private static final Identifier OVERLAY =
            new Identifier(Oddities.MOD_ID, "textures/overlay/supercharge_overlay.png");

    /**
     * Yarn 1.20.1: the renderItem overload that actually exists & is called everywhere:
     * renderItem(ItemStack, ModelTransformationMode, boolean, MatrixStack, VertexConsumerProvider, int, int, BakedModel)
     */
    @Inject(
            method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at = @At("TAIL")
    )
    private void odd$overlay(ItemStack stack, ModelTransformationMode mode, boolean leftHanded,
                             MatrixStack matrices, VertexConsumerProvider vcp, int light, int overlay,
                             BakedModel model, CallbackInfo ci) {
        if (!SuperChargePower.isSupercharged(stack)) return;
        // (Optional) draw an extra overlay pass here if you want to do it via code.
        // I still recommend the model-predicate JSON approach for maximum compatibility.
    }
}
