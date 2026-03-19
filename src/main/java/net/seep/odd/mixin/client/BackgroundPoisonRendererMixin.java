package net.seep.odd.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FogShape;
import net.minecraft.client.world.ClientWorld;
import net.seep.odd.client.PoisonVisionClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BackgroundRenderer.class)
public abstract class BackgroundPoisonRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private static void odd$poisonFogColor(Camera camera, float tickDelta, ClientWorld world, int viewDistance, float skyDarkness, CallbackInfo ci) {
        if (!PoisonVisionClient.isCameraSubmergedInPoison(camera, world)) return;

        RenderSystem.clearColor(0.14f, 0.28f, 0.08f, 0.0f);
    }

    @Inject(method = "applyFog", at = @At("TAIL"))
    private static void odd$poisonFog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance, boolean thickFog, float tickDelta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        if (!PoisonVisionClient.isCameraSubmergedInPoison(camera, client.world)) return;

        RenderSystem.setShaderFogStart(0.25f);
        RenderSystem.setShaderFogEnd(Math.min(viewDistance, 3.5f));
        RenderSystem.setShaderFogShape(FogShape.CYLINDER);
        RenderSystem.setShaderFogColor(0.14f, 0.28f, 0.08f);
    }
}