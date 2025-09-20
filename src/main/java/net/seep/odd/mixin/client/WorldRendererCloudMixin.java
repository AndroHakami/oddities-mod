package net.seep.odd.mixin.client;

import net.minecraft.client.render.WorldRenderer;
import net.seep.odd.sky.CelestialEventClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererCloudMixin {
    @Inject(
            method =
                    "renderClouds(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FDDD)V",
            at = @At("HEAD"), cancellable = true
    )
    private void odd$maybeCancelClouds(CallbackInfo ci) {
        if (CelestialEventClient.hideClouds() && CelestialEventClient.isHueActive()) {
            ci.cancel();
        }
    }
}
