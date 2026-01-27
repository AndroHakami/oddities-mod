// src/main/java/net/seep/odd/mixin/umbra/PlayerEntityAirSwimTravelMixin.java
package net.seep.odd.mixin.umbra;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import net.seep.odd.abilities.astral.OddAirSwim;
import net.seep.odd.abilities.astral.OddLivingJumpingAccess;
import net.seep.odd.abilities.astral.OddUmbraBoostInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityAirSwimTravelMixin {

    // ===== BOOST BEHAVIOR (unchanged) =====
    @Unique private static final double NORMAL_SPEED           = 2.72;
    @Unique private static final double BOOST_TOP_SPEED        = 6.74;
    @Unique private static final double BOOST_ACCEL_PER_TICK   = 0.58;
    @Unique private static final double BOOST_DECEL_PER_TICK   = 0.32;

    // ===== EXIT MOMENTUM (YOU CONTROL THESE) =====
    @Unique private static final int EXIT_MOMENTUM_TICKS = 1;
    @Unique private static final double EXIT_HORIZ_DAMP  = 0.495;

    // NEW: how long you must be "idle" in air-swim before cached momentum is cleared.
    // (prevents old sideways momentum from coming back on exit)
    @Unique private static final int IDLE_CLEAR_TICKS = 6;

    // NEW: cached momentum is only valid if you had intent within the last N ticks.
    // This preserves momentum across the “toggle tick” where velocity gets zeroed.
    @Unique private static final int INTENT_STALE_TICKS = 2;

    // Per-player speed ramp while air-swimming
    @Unique private double oddities$currentSpeed = NORMAL_SPEED;

    // Track airswim transitions + momentum cache
    @Unique private boolean oddities$wasAirSwim = false;

    @Unique private int oddities$exitTicks = 0;
    @Unique private Vec3d oddities$exitVel = Vec3d.ZERO;

    // Cache only when you were actively trying to move recently
    @Unique private Vec3d oddities$lastIntentVel = Vec3d.ZERO;
    @Unique private int oddities$lastIntentTick = -999999;
    @Unique private int oddities$idleTicks = 0;

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void oddities$airSwimTravel(Vec3d input, CallbackInfo ci) {
        PlayerEntity p = (PlayerEntity)(Object)this;

        boolean airSwimNow = (p instanceof OddAirSwim a) && a.oddities$isAirSwim();

        // If in real fluids, do not interfere; also clear exit state.
        if (p.isSubmergedInWater() || p.isTouchingWater() || p.isInLava()) {
            oddities$exitTicks = 0;
            oddities$exitVel = Vec3d.ZERO;
            oddities$lastIntentVel = Vec3d.ZERO;
            oddities$lastIntentTick = -999999;
            oddities$idleTicks = 0;
            oddities$currentSpeed = NORMAL_SPEED;
            oddities$wasAirSwim = airSwimNow;
            return;
        }

        // Detect exit: true -> false
        if (!airSwimNow && oddities$wasAirSwim) {
            oddities$exitTicks = EXIT_MOMENTUM_TICKS;

            Vec3d cur = p.getVelocity();

            // Prefer the current velocity if it exists.
            if (cur.lengthSquared() > 1.0e-6) {
                oddities$exitVel = cur;
            } else {
                // Only use cached velocity if intent was VERY recent.
                if ((p.age - oddities$lastIntentTick) <= INTENT_STALE_TICKS) {
                    oddities$exitVel = oddities$lastIntentVel;
                } else {
                    oddities$exitVel = Vec3d.ZERO;
                }
            }

            // If still basically zero, don't run the exit window.
            if (oddities$exitVel.lengthSquared() <= 1.0e-6) {
                oddities$exitTicks = 0;
                oddities$exitVel = Vec3d.ZERO;
            }
        }
        oddities$wasAirSwim = airSwimNow;

        // =========================
        // EXIT MOMENTUM WINDOW
        // =========================
        if (!airSwimNow && oddities$exitTicks > 0) {
            if (p.isOnGround()) {
                oddities$exitTicks = 0;
                oddities$exitVel = Vec3d.ZERO;
                oddities$currentSpeed = NORMAL_SPEED;
                return;
            }

            oddities$exitTicks--;

            Vec3d v = oddities$exitVel;

            // Gentle horizontal damping (momentum knob)
            v = new Vec3d(v.x * EXIT_HORIZ_DAMP, v.y, v.z * EXIT_HORIZ_DAMP);

            // If gravity is ON, apply vanilla-ish gravity because we're cancelling travel.
            if (!p.hasNoGravity()) {
                v = v.add(0.0, -0.08, 0.0);
                v = new Vec3d(v.x, v.y * 0.98, v.z);
            }

            p.setVelocity(v);
            oddities$exitVel = v;

            p.move(MovementType.SELF, p.getVelocity());
            p.velocityModified = true;

            if (oddities$exitTicks <= 0) {
                oddities$currentSpeed = NORMAL_SPEED;
                oddities$exitVel = Vec3d.ZERO;
            }

            ci.cancel();
            return;
        }

        // If not air-swimming and no exit window, vanilla runs.
        if (!airSwimNow) {
            oddities$currentSpeed = NORMAL_SPEED;
            return;
        }

        // =========================
        // AIR SWIM ACTIVE (your existing feel)
        // =========================

        // Visuals
        p.setSwimming(true);
        p.setPose(EntityPose.SWIMMING);

        // Buoyant (not creative flight)
        p.setNoGravity(true);
        p.fallDistance = 0;

        boolean jumpHeld = ((OddLivingJumpingAccess)p).oddities$getJumping();

        // ===== KEEP YOUR VARIABLES EXACTLY (unchanged) =====
        final double verticalSpd = 0.26;
        final double lerp        = 0.6;
        final double idleDamp    = 0.0;
        final double driftDown   = 0.000;

        double strafe  = input.x;
        double forward = input.z;

        Vec3d wish = new Vec3d(strafe, 0.0, forward);
        if (wish.lengthSquared() > 1.0) wish = wish.normalize();

        float yaw = p.getYaw();
        float rad = yaw * ((float)Math.PI / 180F);
        double sin = MathHelper.sin(rad);
        double cos = MathHelper.cos(rad);

        double wx = wish.x * cos - wish.z * sin;
        double wz = wish.z * cos + wish.x * sin;

        double vy = 0.0;
        if (jumpHeld)        vy += verticalSpd;
        if (p.isSneaking())  vy -= verticalSpd;
        if (!jumpHeld && !p.isSneaking()) vy -= driftDown;

        // Intent = you are actually trying to move (or move vertically)
        boolean hasIntent = (wish.lengthSquared() > 1.0e-6) || (Math.abs(vy) > 1.0e-6);

        // If you've been idle a bit, clear cached momentum so exit won't push you sideways.
        if (!hasIntent) {
            oddities$idleTicks++;
            if (oddities$idleTicks >= IDLE_CLEAR_TICKS) {
                oddities$lastIntentVel = Vec3d.ZERO;
                oddities$lastIntentTick = -999999;
            }
        } else {
            oddities$idleTicks = 0;
        }

        // True “CTRL held” (synced from client)
        boolean ctrlHeld = (p instanceof OddUmbraBoostInput bi) && bi.oddities$isUmbraBoostHeld(p.age);

        if (ctrlHeld) {
            oddities$currentSpeed = oddities$moveToward(oddities$currentSpeed, BOOST_TOP_SPEED, BOOST_ACCEL_PER_TICK);
        } else {
            if (oddities$currentSpeed < NORMAL_SPEED) oddities$currentSpeed = NORMAL_SPEED;
            oddities$currentSpeed = oddities$moveToward(oddities$currentSpeed, NORMAL_SPEED, BOOST_DECEL_PER_TICK);
        }

        Vec3d desired = new Vec3d(wx, vy, wz).multiply(oddities$currentSpeed);

        Vec3d cur = p.getVelocity();
        if (!hasIntent) {
            p.setVelocity(cur.x * idleDamp, cur.y * idleDamp, cur.z * idleDamp);
        } else {
            p.setVelocity(cur.lerp(desired, lerp));
        }

        // Cache momentum ONLY when you have intent, so it reflects “what you were doing now”.
        Vec3d after = p.getVelocity();
        if (hasIntent && after.lengthSquared() > 1.0e-6) {
            oddities$lastIntentVel = after;
            oddities$lastIntentTick = p.age;
        }

        p.move(MovementType.SELF, p.getVelocity());
        p.velocityModified = true;

        ci.cancel();
    }

    @Unique
    private static double oddities$moveToward(double current, double target, double maxDelta) {
        if (current < target) return Math.min(target, current + maxDelta);
        if (current > target) return Math.max(target, current - maxDelta);
        return current;
    }
}
