package net.seep.odd.mixin.darkknight;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.seep.odd.abilities.power.DarkKnightPower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "attack", at = @At("TAIL"))
    private void odd$darkKnightCleave(Entity target, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (!(self instanceof ServerPlayerEntity serverPlayer) || self.getWorld().isClient()) {
            return;
        }

        DarkKnightPower.tryConsumeCleaveAndHit(serverPlayer, target);
    }
}
