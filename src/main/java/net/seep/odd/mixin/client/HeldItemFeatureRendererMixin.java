package net.seep.odd.mixin.client;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.seep.odd.abilities.spotted.SpottedClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemFeatureRenderer.class)
public class HeldItemFeatureRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void odd$hideHeldItems(MatrixStack matrices, VertexConsumerProvider consumers, int light,
                                   LivingEntity entity, float limbAngle, float limbDistance,
                                   float tickDelta, float customAngle, float headYaw, float headPitch,
                                   CallbackInfo ci) {
        if (SpottedClient.isSpottedInvisible(entity)) ci.cancel();
    }
}
