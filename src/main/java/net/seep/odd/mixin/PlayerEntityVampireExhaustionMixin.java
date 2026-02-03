package net.seep.odd.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.seep.odd.abilities.power.VampirePower;
import net.seep.odd.abilities.vampire.VampireUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityVampireExhaustionMixin {

    @Inject(method = "addExhaustion", at = @At("HEAD"), cancellable = true)
    private void odd$vampireRedirectExhaustion(float exhaustion, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity)(Object)this;
        if (self.getWorld().isClient) return;
        if (!(self instanceof ServerPlayerEntity sp)) return;

        if (!VampireUtil.isVampire(sp)) return;

        VampirePower.addBloodExhaustion(sp, exhaustion);
        ci.cancel(); // stop vanilla hunger exhaustion
    }
}
