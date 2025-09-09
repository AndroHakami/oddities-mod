package net.seep.odd.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FogShape;

import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clamp fog very short while in odd:void (runs after vanilla so it wins). */
@Mixin(BackgroundRenderer.class)
public class VoidFogMixin {
    private static final Identifier VOID_DIM = new Identifier("odd", "the_void");
    // tweak here:
    private static final float FOG_NEAR = 0.0F;
    private static final float FOG_FAR  = 18.0F;

    @Inject(method = "applyFog", at = @At("TAIL"))
    private static void odd$applyFog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance,
                                     boolean thickFog, float tickDelta, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        if (!mc.world.getRegistryKey().getValue().equals(VOID_DIM)) return;

        // Optional: uncomment next line once to confirm mixin is active in logs
        // System.out.println("[Oddities] Void fog applied (" + fogType + ")");

        // Clamp both terrain & sky fog at the very end so nothing overrides it.
        RenderSystem.setShaderFogStart(FOG_NEAR);
        RenderSystem.setShaderFogEnd(FOG_FAR);
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
    }
}
