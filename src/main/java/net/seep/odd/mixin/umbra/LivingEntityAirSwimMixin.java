// src/main/java/net/seep/odd/mixin/umbra/LivingEntityAirSwimMixin.java
package net.seep.odd.mixin.umbra;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.seep.odd.abilities.astral.OddAirSwim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityAirSwimMixin {

    @Inject(method = "tickMovement", at = @At("TAIL"))
    private void oddities$forceAirSwimmingPose(CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;

        if (self instanceof PlayerEntity p && p instanceof OddAirSwim air && air.oddities$isAirSwim()) {
            p.setSwimming(true);
            p.setPose(EntityPose.SWIMMING);

            // optional polish: prevents “standing sprint” conflicts while swimming
            p.setSprinting(false);
            p.fallDistance = 0;
        }
    }
}
