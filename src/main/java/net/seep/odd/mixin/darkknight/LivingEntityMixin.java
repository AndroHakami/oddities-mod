package net.seep.odd.mixin.darkknight;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import net.seep.odd.abilities.darkknight.DarkKnightRuntime;
import net.seep.odd.entity.darkknight.DarkShieldEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "modifyAppliedDamage", at = @At("RETURN"), cancellable = true)
    private void odd$darkKnightRedirectDamage(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.getWorld().isClient() || !(self.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        if (self instanceof DarkShieldEntity) {
            return;
        }

        float finalDamage = cir.getReturnValueF();
        if (finalDamage <= 0.0F) {
            return;
        }

        DarkShieldEntity shield = DarkKnightRuntime.getShieldProtecting(serverWorld, self.getUuid());
        if (shield == null || !shield.isAlive()) {
            return;
        }

        float absorbed = shield.absorbDamage(finalDamage, source);
        if (absorbed <= 0.0F) {
            return;
        }

        cir.setReturnValue(Math.max(0.0F, finalDamage - absorbed));
    }
}
