// FILE: src/main/java/net/seep/odd/mixin/client/TrumpetAxeBipedEntityModelMixin.java
package net.seep.odd.mixin.client;

import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;

import net.seep.odd.item.custom.client.TrumpetAxeHornPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BipedEntityModel.class)
public abstract class TrumpetAxeBipedEntityModelMixin<T extends LivingEntity> {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void odd$applyTrumpetAxeHornPose(T livingEntity, float limbAngle, float limbDistance, float animationProgress,
                                             float headYaw, float headPitch, CallbackInfo ci) {
        TrumpetAxeHornPose.apply((BipedEntityModel<? extends LivingEntity>) (Object) this, livingEntity);
    }
}