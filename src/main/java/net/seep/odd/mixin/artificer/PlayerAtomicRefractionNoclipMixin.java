package net.seep.odd.mixin.artificer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.seep.odd.abilities.artificer.mixer.brew.AtomicRefractionEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerEntity.class)
abstract class PlayerAtomicRefractionNoclipMixin {

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;isSpectator()Z")
    )
    private boolean oddities$pretendSpectatorWhileRefraction(PlayerEntity self, Operation<Boolean> original) {
        // Server-side: force spectator logic ONLY while Atomic Refraction is active
        if (self instanceof ServerPlayerEntity sp && AtomicRefractionEffect.isActive(sp)) {
            return true;
        }
        return original.call(self);
    }
}
