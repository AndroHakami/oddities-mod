package net.seep.odd.mixin.client;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.sky.CelestialEventClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void odd$tintSkyColor(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        cir.setReturnValue(CelestialEventClient.applySkyHue(cir.getReturnValue()));
    }
}
