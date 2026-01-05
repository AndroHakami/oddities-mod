// src/main/java/net/seep/odd/mixin/client/GameRendererGlitchZoomMixin.java
package net.seep.odd.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;

import net.seep.odd.abilities.power.GlitchPower;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class GameRendererGlitchZoomMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void odd$glitchZoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        double mult = GlitchPower.Client.zoomMultiplier();
        if (mult != 1.0) {
            cir.setReturnValue(cir.getReturnValueD() * mult);
        }
    }
}
