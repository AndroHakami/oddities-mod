// FILE: src/main/java/net/seep/odd/mixin/climber/PlayerEntityClimberMiningSpeedMixin.java
package net.seep.odd.mixin.climber;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.seep.odd.abilities.climber.entity.ClimberRopeAnchorEntity;
import net.seep.odd.abilities.climber.entity.ClimberRopeShotEntity;
import net.seep.odd.abilities.power.ClimberPower;
import net.seep.odd.status.ModStatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityClimberMiningSpeedMixin {

    @Inject(method = "getBlockBreakingSpeed", at = @At("RETURN"), cancellable = true)
    private void odd$noAirborneMiningPenalty(BlockState state, CallbackInfoReturnable<Float> cir) {
        PlayerEntity self = (PlayerEntity)(Object)this;

        // Only fix the penalty case (airborne)
        if (self.isOnGround()) return;

        // POWERLESS: do not bypass
        if (self.hasStatusEffect(ModStatusEffects.POWERLESS)) return;

        boolean shouldBypass;

        // Server: authoritative check
        if (!self.getWorld().isClient && self instanceof ServerPlayerEntity sp) {
            if (!ClimberPower.hasClimber(sp)) return;

            // Rope movement should still keep normal mining speed even if passive wall climb is toggled off.
            shouldBypass = ClimberPower.isPrimaryEngaged(sp) || (ClimberPower.canUsePassiveClimb(sp) && ClimberPower.isTouchingWall(sp));
        } else {
            // Client: allow if rope nearby OR server-synced passive climb permission.
            boolean roped = isRopedOrHookFlying(self);
            if (!roped && !ClimberPower.hasClimberAnySide(self)) return;
            shouldBypass = self.horizontalCollision || roped;
        }

        if (!shouldBypass) return;

        // Vanilla airborne penalty is /5 (0.2x). Multiply by 5 to restore normal speed.
        float f = cir.getReturnValue();
        cir.setReturnValue(f * 5.0f);
    }

    private static boolean isRopedOrHookFlying(PlayerEntity p) {
        Box box = p.getBoundingBox().expand(40.0);
        var id = p.getUuid();

        return !p.getWorld().getOtherEntities(p, box, e ->
                (e instanceof ClimberRopeAnchorEntity a && id.equals(a.getOwnerUuid())) ||
                        (e instanceof ClimberRopeShotEntity s && id.equals(s.getOwnerUuid()))
        ).isEmpty();
    }
}