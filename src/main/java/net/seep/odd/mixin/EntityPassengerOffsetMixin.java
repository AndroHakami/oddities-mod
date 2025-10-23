package net.seep.odd.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Pehkui (optional-but-available) to detect "tiny" passengers client+server
import virtuoel.pehkui.api.ScaleTypes;

@Mixin(Entity.class)
public abstract class EntityPassengerOffsetMixin {

    /**
     * After vanilla positions a passenger, nudge tiny player passengers (our rat) onto the right shoulder.
     * Runs on both sides so Iris/renderer sees the correct pose.
     */
    @Inject(method = "updatePassengerPosition", at = @At("TAIL"))
    private void odd$offsetRatPassenger(Entity passenger, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;

        // Only for "player carrying player"
        if (!(self instanceof PlayerEntity host)) return;
        if (!(passenger instanceof PlayerEntity rat)) return;

        // Heuristic: treat as rat when BASE scale is small (works client + server).
        float baseScale = 1.0f;
        try {
            baseScale = ScaleTypes.BASE.getScaleData(rat).getScale();
        } catch (Throwable ignored) {}

        if (baseScale > 0.75f) return; // not tiny => don’t offset

        // Shoulder offsets relative to host yaw
        float yawRad = (float) Math.toRadians(host.getYaw());
        double rightX = -MathHelper.sin(yawRad);
        double rightZ =  MathHelper.cos(yawRad);
        double fwdX   = -rightZ;
        double fwdZ   =  rightX;

        // Tunables (match what we used with the seat)
        double side = 0.25;       // rightwards
        double forward = 0.10;    // a hair forward
        double up = host.getStandingEyeHeight() - 0.25;

        Vec3d base = host.getPos();
        double x = base.x + rightX * side + fwdX * forward;
        double y = host.getY() + up;
        double z = base.z + rightZ * side + fwdZ * forward;

        // Final placement for the rat
        rat.setPosition(x, y, z);
        rat.fallDistance = 0.0f;
    }
}
