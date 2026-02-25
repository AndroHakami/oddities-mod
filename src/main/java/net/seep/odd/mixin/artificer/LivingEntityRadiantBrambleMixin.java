package net.seep.odd.mixin.artificer;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.seep.odd.abilities.artificer.mixer.brew.RadiantBrambleEffect;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class LivingEntityRadiantBrambleMixin {

    @Inject(method = "damage", at = @At("RETURN"))
    private void odd$brambleOnDamaged(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return; // only if damage actually happened

        LivingEntity self = (LivingEntity)(Object)this;
        if (self.getWorld() == null || self.getWorld().isClient) return;

        if (self instanceof ServerPlayerEntity sp) {
            if (RadiantBrambleEffect.isActive(sp)) {
                RadiantBrambleEffect.onVictimDamaged(sp, source);
            }
        }
    }
}
