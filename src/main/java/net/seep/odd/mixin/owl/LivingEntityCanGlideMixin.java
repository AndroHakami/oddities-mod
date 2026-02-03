package net.seep.odd.mixin.owl;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.seep.odd.abilities.power.OwlPower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityCanGlideMixin {

    /**
     * 1.20.1: vanilla enforces elytra requirements inside tickFallFlying().
     * If we cancel vanilla here for Owl players, they can keep the FALL_FLYING state
     * without needing a chest Elytra item.
     *
     * Using the descriptor form "tickFallFlying()V" avoids mapping name weirdness.
     */
    @Inject(method = "tickFallFlying()V", at = @At("HEAD"), cancellable = true)
    private void odd$owlTickFallFlying(CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!(self instanceof PlayerEntity p)) return;

        if (!OwlPower.hasOwlAnySide(p)) return;

        // Only bypass vanilla checks while actually fall-flying.
        // Your OwlPower server tick is what starts/stops fall flying.
        if (p.isFallFlying()) {
            ci.cancel();
        }
    }
}
