// src/main/java/net/seep/odd/mixin/IronGolemEntityMixin.java
package net.seep.odd.mixin;

import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IronGolemEntity.class)
public abstract class IronGolemEntityMixin {

    @Unique private int odd$cureTicks = 0;

    // (keep whatever API you already use to set odd$cureTicks)
    // public void odd$startCure(int ticks) { odd$cureTicks = ticks; }

    @Inject(method = "tickMovement", at = @At("TAIL"))
    private void odd$tickMovementTail(CallbackInfo ci) {
        IronGolemEntity self = (IronGolemEntity) (Object) this;

        if (odd$cureTicks <= 0) return;
        odd$cureTicks--;

        if (self.getWorld() instanceof ServerWorld sw) {
            if ((odd$cureTicks % 10) == 0) {
                sw.spawnParticles(ParticleTypes.ENCHANTED_HIT,
                        self.getX(), self.getBodyY(0.7), self.getZ(),
                        10, 0.45, 0.55, 0.45, 0.01);
            }
        }

        // if (odd$cureTicks == 0) { ...finish cure... }
    }
}
