package net.seep.odd.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.seep.odd.abilities.power.VampirePower;
import net.seep.odd.abilities.vampire.VampireUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityVampireNoHealMixin {

    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void odd$vampireNoHealAtZeroBlood(float amount, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (self.getWorld().isClient) return;
        if (!(self instanceof ServerPlayerEntity sp)) return;
        if (!VampireUtil.isVampire(sp)) return;

        if (VampirePower.getBlood(sp) <= 0.001f) {
            // ✅ no healing of any kind when blood is empty
            ci.cancel();
        }
    }
}
