package net.seep.odd.mixin;

import net.minecraft.entity.Entity;
import net.seep.odd.entity.bosswitch.BossGolemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "stopRiding", at = @At("HEAD"), cancellable = true)
    private void odd$preventBossGolemDismount(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        Entity vehicle = self.getVehicle();

        if (vehicle instanceof BossGolemEntity golem && golem.shouldBlockPassengerDismount(self)) {
            ci.cancel();
        }
    }
}