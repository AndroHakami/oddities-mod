// FILE: src/main/java/net/seep/odd/mixin/client/HeldItemRendererSniperHideMixin.java
package net.seep.odd.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.seep.odd.abilities.sniper.client.SniperClientState;
import net.seep.odd.abilities.sniper.item.SniperItem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererSniperHideMixin {

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void odd_hideSniperWhenScoped(net.minecraft.client.network.AbstractClientPlayerEntity player,
                                          float tickDelta, float pitch,
                                          net.minecraft.util.Hand hand, float swingProgress,
                                          ItemStack stack, float equipProgress,
                                          MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                          CallbackInfo ci) {
        if (!(stack.getItem() instanceof SniperItem)) return;

        // Only hide in first-person when fully zoomed
        if (SniperClientState.isFullyScoped(tickDelta)) {
            ci.cancel();
        }
    }
}
