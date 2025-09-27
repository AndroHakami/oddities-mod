package net.seep.odd.mixin.spectral;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerEntity.class)
abstract class PlayerSpectralNoclipMixin {
    // When phased, return true for this specific isSpectator() call inside tick()
    // so vanilla skips block collisions / suffocation pushes. No gamemode swap, no HUD changes.
    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;isSpectator()Z")
    )
    private boolean odd$pretendSpectatorWhilePhasing(PlayerEntity self, Operation<Boolean> original) {
        if (net.seep.odd.abilities.spectral.SpectralPhaseHooks.isPhasing(self)) return true;
        return original.call(self);
    }
}
