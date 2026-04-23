package net.seep.odd.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.abilities.power.RatPower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityPassengerOffsetMixin {

    /**
     * After vanilla positions a passenger, nudge rat passengers farther out onto the host's shoulder.
     * Runs on both sides so local render and remote tracking stay visually aligned.
     */
    @Inject(method = "updatePassengerPosition", at = @At("TAIL"))
    private void odd$offsetRatPassenger(Entity passenger, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;

        if (!(self instanceof PlayerEntity host)) return;
        if (!(passenger instanceof PlayerEntity rat)) return;
        if (!RatPower.isRatPassenger(rat)) return;

        float yawRad = (float) Math.toRadians(host.getYaw());

        Vec3d forward = new Vec3d(-MathHelper.sin(yawRad), 0.0, MathHelper.cos(yawRad));
        Vec3d right   = new Vec3d( MathHelper.cos(yawRad), 0.0, MathHelper.sin(yawRad));

        double side = 0.36;
        double forwardOff = 0.03;
        double up = host.getStandingEyeHeight() - 0.70;

        Vec3d pos = host.getPos()
                .add(0.0, up, 0.0)
                .add(right.multiply(side))
                .add(forward.multiply(forwardOff));

        rat.setPosition(pos.x, pos.y, pos.z);
        rat.fallDistance = 0.0f;
    }
}
