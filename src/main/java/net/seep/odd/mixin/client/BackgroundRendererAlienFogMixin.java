package net.seep.odd.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.world.World;
import net.seep.odd.event.alien.client.AlienInvasionClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererAlienFogMixin {

    @Inject(method = "applyFog", at = @At("TAIL"))
    private static void odd$alienFog(Camera camera, BackgroundRenderer.FogType fogType,
                                     float viewDistance, boolean thickFog, float tickDelta, CallbackInfo ci) {

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        if (mc.world.getRegistryKey() != World.OVERWORLD) return;
        if (!AlienInvasionClientState.active()) return;

        // dark “space air” fog (very slight green tint)
        RenderSystem.setShaderFogColor(0.01f, 0.012f, 0.018f, 1.0f);

        // tighter fog distances for “no atmosphere” / invasion vibe
        float end = Math.max(28.0f, viewDistance * 0.95f);
        RenderSystem.setShaderFogStart(0.0f);
        RenderSystem.setShaderFogEnd(end);
    }
}