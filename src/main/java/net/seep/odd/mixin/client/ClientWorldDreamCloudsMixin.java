package net.seep.odd.mixin.client;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.sky.client.OverworldDreamClouds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.class)
public abstract class ClientWorldDreamCloudsMixin {
    @Inject(method = "getCloudsColor", at = @At("RETURN"), cancellable = true)
    private void odd$dreamCloudTint(float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        ClientWorld world = (ClientWorld) (Object) this;
        cir.setReturnValue(OverworldDreamClouds.apply(world, cir.getReturnValue(), tickDelta));
    }
}