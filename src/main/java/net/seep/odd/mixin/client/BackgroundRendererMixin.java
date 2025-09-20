package net.seep.odd.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.sky.CelestialEventClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Yarn 1.20.1+build.10
 *
 * Hooks BackgroundRenderer#getFogModifier (vanilla sets the fog color just after this) and
 * then re-applies our tinted fog at the end of BackgroundRenderer#render as a safety net.
 */
@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {

    /**
     * Called during BackgroundRenderer#render(...) before vanilla sets the fog color.
     * Signature for Yarn 1.20.1+build.10:
     *   static BackgroundRenderer.StatusEffectFogModifier getFogModifier(Camera, float, ClientWorld, int, float)
     */



    /**
     * Safety net: after BackgroundRenderer#render finishes, re-apply our tinted color so
     * anything that wrote a different fog color during the method is overridden.
     * Signature for Yarn 1.20.1+build.10:
     *   static void render(Camera, float, ClientWorld, int, float)
     */
    @Inject(
            method = "render(Lnet/minecraft/client/render/Camera;FLnet/minecraft/client/world/ClientWorld;IF)V",
            at = @At("TAIL")
    )
    private static void odd$reapplyTintAtEnd(
            Camera camera, float tickDelta, ClientWorld world, int skyMode, float viewDistance,
            CallbackInfo ci
    ) {
        Vec3d base = null;

    }
}
