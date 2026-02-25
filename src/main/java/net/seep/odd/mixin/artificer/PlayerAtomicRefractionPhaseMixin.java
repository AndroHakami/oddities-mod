package net.seep.odd.mixin.artificer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.player.PlayerEntity;
import net.seep.odd.abilities.artificer.mixer.brew.AtomicRefractionEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Makes Atomic Refraction actually phase by pretending the player is a spectator
 * ONLY for the call(s) inside PlayerEntity.tick().
 *
 * This avoids global spectator logic and only affects the tick-time noclip handling.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerAtomicRefractionPhaseMixin {

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;isSpectator()Z")
    )
    private boolean oddities$pretendSpectatorWhileRefraction(PlayerEntity self, Operation<Boolean> original) {
        if (AtomicRefractionEffect.isActive(self)) return true;
        return original.call(self);
    }
}
