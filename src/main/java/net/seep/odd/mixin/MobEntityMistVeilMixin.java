// FILE: src/main/java/net/seep/odd/mixin/MobEntityMistVeilMixin.java
package net.seep.odd.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.seep.odd.status.ModStatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class MobEntityMistVeilMixin {

    @Inject(method = "setTarget(Lnet/minecraft/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true)
    private void odd$denyTargetingMistyVeil(LivingEntity target, CallbackInfo ci) {
        if (target == null) return;
        if (target.hasStatusEffect(ModStatusEffects.MIST_VEIL)) {
            ci.cancel(); // mob fails to acquire target
        }
    }
}