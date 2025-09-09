// net/seep/odd/mixin/client/OverdriveCameraShakeMixin.java
package net.seep.odd.mixin.client;

import net.minecraft.client.render.Camera;
import net.seep.odd.abilities.overdrive.client.OverdriveScreenFX;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class OverdriveCameraShakeMixin {
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void odd$applyOverdriveShake(net.minecraft.world.BlockView area,
                                         net.minecraft.entity.Entity entity,
                                         boolean thirdPerson, boolean inverseView,
                                         float tickDelta, CallbackInfo ci) {
        Camera self = (Camera)(Object)this;

        OverdriveScreenFX.Shake s = OverdriveScreenFX.computeShake(self, tickDelta);
        if (s == null) return;

        float yaw = self.getYaw();
        float pitch = self.getPitch();
        this.setRotation(yaw + s.dyaw, OverdriveScreenFX.clampPitch(pitch + s.dpitch));
    }
}
