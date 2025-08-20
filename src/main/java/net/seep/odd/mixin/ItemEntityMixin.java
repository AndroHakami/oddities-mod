package net.seep.odd.mixin;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    /**
     * Block item pickup if the player is in Astral form.
     * Cancels before vanilla performs the inventory transfer.
     */
    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void odd$blockPickupIfAstral(PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity sp) {
            if (net.seep.odd.abilities.astral.AstralInventory.isAstral(sp)) {
                ci.cancel(); // no pickup; item stays on ground
            }
        }
    }
}
