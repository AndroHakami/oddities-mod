package net.seep.odd.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.seep.odd.Oddities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapTextureManager.class)
public class AtheneumLightmapTintMixin {

    private static final RegistryKey<World> ATHENEUM =
            RegistryKey.of(RegistryKeys.WORLD, new Identifier(Oddities.MOD_ID, "atheneum"));

    @Inject(method = "update", at = @At("TAIL"))
    private void odd_tintLightmap(float tickDelta, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || !mc.world.getRegistryKey().equals(ATHENEUM)) return;

        NativeImage img = ((LightmapTextureManagerAccessor) this).odd_getImage();
        if (img == null) return;

        // 16x16 lightmap
        for (int y = 0; y < 16; y++) {
            float sky = y / 15.0f; // stronger tint where skylight is higher

            // dreamy teal/blue tint (subtle)
            float mr = 0.94f;
            float mg = 0.98f;
            float mb = 1.08f;

            // extra "sky glow" lift
            float addR = 6.0f  * sky;
            float addG = 10.0f * sky;
            float addB = 18.0f * sky;

            for (int x = 0; x < 16; x++) {
                int c = img.getColor(x, y);

                // NativeImage is ABGR in practice
                int a = (c >>> 24) & 255;
                int b = (c >>> 16) & 255;
                int g = (c >>> 8) & 255;
                int r = (c) & 255;

                float rf = r * mr + addR;
                float gf = g * mg + addG;
                float bf = b * mb + addB;

                r = (int)Math.max(0, Math.min(255, rf));
                g = (int)Math.max(0, Math.min(255, gf));
                b = (int)Math.max(0, Math.min(255, bf));

                int out = (a << 24) | (b << 16) | (g << 8) | r;
                img.setColor(x, y, out);
            }
        }
    }
}
