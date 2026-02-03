package net.seep.odd.mixin;

import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.seep.odd.abilities.vampire.VampireUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HungerManager.class)
public abstract class HungerManagerMixin {

    @Shadow private int foodLevel;
    @Shadow private float saturationLevel;
    @Shadow private float exhaustion;

    @Inject(method = "update", at = @At("HEAD"))
    private void odd$vampireLockHunger(PlayerEntity player, CallbackInfo ci) {
        if (!VampireUtil.isVampire(player)) return;

        // Remove hunger mechanics for vampires.
        this.foodLevel = 20;
        this.saturationLevel = 5.0f;
        this.exhaustion = 0.0f;
    }

    @Redirect(
            method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;heal(F)V")
    )
    private void odd$vampireBlockNaturalRegenInSun(PlayerEntity player, float amount) {
        if (VampireUtil.isVampire(player) && VampireUtil.isInDirectSunlight(player)) {
            // Block ONLY natural regen (potions, beacons, etc. still work)
            return;
        }
        player.heal(amount);
    }
}
