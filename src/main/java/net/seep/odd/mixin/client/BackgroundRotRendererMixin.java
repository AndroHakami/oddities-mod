package net.seep.odd.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forces a constant fog color in odd:rotten_roots and avoids Nether's red modulation.
 * MC 1.20.1: BackgroundRenderer#render(Camera,float,ClientWorld,int,float)
 */
@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRotRendererMixin {
    private static final Identifier ROTTEN_ROOTS_ID = new Identifier(Oddities.MOD_ID, "rotten_roots");
    private static final int FOG_RGB = 0xD7DFD2; // 14147538

    @Inject(method = "render", at = @At("TAIL"))
    private static void odd$fixRottenRootsFog(Camera camera, float tickDelta,
                                              ClientWorld world, int viewDistance,
                                              float skyDarkness, CallbackInfo ci) {
        if (world != null && world.getRegistryKey().getValue().equals(ROTTEN_ROOTS_ID)) {
            float r = ((FOG_RGB >> 16) & 0xFF) / 255f;
            float g = ((FOG_RGB >>  8) & 0xFF) / 255f;
            float b = ( FOG_RGB        & 0xFF) / 255f;
            // Override whatever vanilla/the Nether did
            RenderSystem.setShaderFogColor(r, g, b);
        }
    }
}
