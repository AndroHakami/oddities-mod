// src/main/java/net/seep/odd/status/effect/FrogStatusUtil.java
package net.seep.odd.status.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Vec3d;

final class FrogStatusUtil {
    private FrogStatusUtil() {}

    static void hiddenSpeedJump(LivingEntity e, int dur, int speedAmp, int jumpAmp) {
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, dur, speedAmp, false, false, false));
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, dur, jumpAmp, false, false, false));
    }

    static void hiddenRegenResist(LivingEntity e, int dur, int regenAmp, int resistAmp) {
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, dur, regenAmp, false, false, false));
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, dur, resistAmp, false, false, false));
    }

    static void lowGravity(LivingEntity e, double fallFactor) {
        if (e.isOnGround() || e.isClimbing() || e.isTouchingWater() || e.hasVehicle()) return;
        Vec3d v = e.getVelocity();
        if (v.y < 0.0D) {
            e.setVelocity(v.x, v.y * fallFactor, v.z);
            e.velocityDirty = true;
        }
    }
}
