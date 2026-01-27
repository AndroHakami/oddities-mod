// src/main/java/net/seep/odd/mixin/umbra/PlayerUmbraNoclipMixin.java
package net.seep.odd.mixin.umbra;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.player.PlayerEntity;
import net.seep.odd.abilities.astral.OddUmbraPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerEntity.class)
abstract class PlayerUmbraNoclipMixin {

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;isSpectator()Z")
    )
    private boolean oddities$pretendSpectatorWhileUmbra(PlayerEntity self, Operation<Boolean> original) {
        if (self instanceof OddUmbraPhase ph && ph.oddities$isUmbraPhasing()) return true;
        return original.call(self);
    }
}
