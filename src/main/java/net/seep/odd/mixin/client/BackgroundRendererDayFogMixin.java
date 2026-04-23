package net.seep.odd.mixin.client;

import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.sky.day.BiomeDayProfileClientStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererDayFogMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private static void odd$applyBiomeDayFog(Camera camera,
                                             float tickDelta,
                                             ClientWorld world,
                                             int viewDistance,
                                             float skyDarkness,
                                             CallbackInfo ci) {
        if (world == null) return;

        float dayAmount = BiomeDayProfileClientStore.getDayAmount(world, tickDelta);
        if (dayAmount <= 0.001f) return;

        BiomeDayProfileClientStore.DayBiomeBlend blend = BiomeDayProfileClientStore.sample(world, camera.getPos());
        if (blend.weight() <= 0.001f) return;

        float weight = blend.weight() * dayAmount;
        Vec3d fog = blend.fog();

        float r = BackgroundRendererAccessor.odd$getRed();
        float g = BackgroundRendererAccessor.odd$getGreen();
        float b = BackgroundRendererAccessor.odd$getBlue();

        BackgroundRendererAccessor.odd$setRed((float) (r + (fog.x - r) * weight));
        BackgroundRendererAccessor.odd$setGreen((float) (g + (fog.y - g) * weight));
        BackgroundRendererAccessor.odd$setBlue((float) (b + (fog.z - b) * weight));
    }
}