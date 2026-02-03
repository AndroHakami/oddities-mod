package net.seep.odd.mixin.climber;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.seep.odd.abilities.climber.entity.ClimberRopeAnchorEntity;
import net.seep.odd.abilities.climber.entity.ClimberRopeShotEntity;
import net.seep.odd.abilities.power.ClimberPower;
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

        boolean shouldBypass = false;

        // Server: reliable “has climber” + engaged checks
        if (!self.getWorld().isClient && self instanceof ServerPlayerEntity sp) {
            if (!ClimberPower.hasClimber(sp)) return;
            shouldBypass = sp.horizontalCollision || ClimberPower.isPrimaryEngaged(sp);
        } else {
            // Client: apply only for local player while roped or pushing into a wall
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null || self != mc.player) return;

            // roped / hook flying check (cheap radius scan — only while mining)
            shouldBypass = isRopedOrHookFlyingClient(self) || self.horizontalCollision;
        }

        if (!shouldBypass) return;

        // Vanilla airborne penalty is /5 (0.2x). Multiply by 5 to restore normal speed.
        float f = cir.getReturnValue();
        cir.setReturnValue(f * 5.0f);
    }

    private static boolean isRopedOrHookFlyingClient(PlayerEntity p) {
        Box box = p.getBoundingBox().expand(40.0); // rope max 30, so 40 is safe
        var id = p.getUuid();

        // Anchor exists?
        boolean anchor = !p.getWorld().getOtherEntities(p, box, e ->
                e instanceof ClimberRopeAnchorEntity a && id.equals(a.getOwnerUuid())
        ).isEmpty();
        if (anchor) return true;

        // Hook shot exists (primary or secondary)
        return !p.getWorld().getOtherEntities(p, box, e ->
                e instanceof ClimberRopeShotEntity s && id.equals(s.getOwnerUuid())
        ).isEmpty();
    }
}
