package net.seep.odd.mixin.client;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Camera;
import net.seep.odd.abilities.overdrive.client.OverdriveScreenFX;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class OverdriveFovMixin {
    @Inject(method = "getFov(Lnet/minecraft/client/render/Camera;FZ)D",
            at = @At("RETURN"), cancellable = true)
    private void odd$overdriveZoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        double base = cir.getReturnValue();
        cir.setReturnValue(OverdriveScreenFX.adjustFov(base));
    }
}