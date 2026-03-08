// FILE: src/main/java/net/seep/odd/mixin/owl/LivingEntityCanGlideMixin.java
package net.seep.odd.mixin.owl;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.seep.odd.abilities.power.OwlPower;
import net.seep.odd.abilities.owl.net.OwlNetworking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityCanGlideMixin {

    /**
     * Only bypass vanilla elytra checks WHILE Owl flight is actually active.
     * This prevents:
     * - “flight toggle off but still gliding”
     * - breaking real elytra behavior
     */
    @Inject(method = "tickFallFlying()V", at = @At("HEAD"), cancellable = true)
    private void odd$owlTickFallFlying(CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!(self instanceof PlayerEntity p)) return;

        if (!OwlPower.hasOwlAnySide(p)) return;

        // Only bypass when server says Owl flight is active (client mirrored)
        if (!OwlNetworking.isFlying()) return;

        if (p.isFallFlying()) {
            ci.cancel();
        }
    }
}