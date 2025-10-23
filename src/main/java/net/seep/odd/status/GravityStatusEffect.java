package net.seep.odd.status;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.server.world.ServerWorld;

// If your particle constant lives elsewhere, adjust this import/name.
// Expecting something like: public static final DefaultParticleType UNGRAVITY;
import net.seep.odd.particles.OddParticles;

/**
 * While active: entity has noGravity (levitates in place unless pushed).
 * On apply: small burst of "ungravity" particles.
 * While ticking: gentle ambient "ungravity" particles.
 * On removal: gravity restored.
 */
public class GravityStatusEffect extends StatusEffect {
    public GravityStatusEffect() {
        super(StatusEffectCategory.NEUTRAL, 0x7FD5FF); // soft cyan in HUD
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        // tick every tick
        return true;
    }

    @Override
    public void onApplied(LivingEntity entity, AttributeContainer attributes, int amplifier) {
        super.onApplied(entity, attributes, amplifier);
        if (!entity.getWorld().isClient) {
            entity.setNoGravity(true);

            // spawn a quick "on apply" burst around torso height
            ServerWorld sw = (ServerWorld) entity.getWorld();
            double cx = entity.getX();
            double cy = entity.getY() + entity.getHeight() * 0.5;
            double cz = entity.getZ();
            // count, dx, dy, dz spread, speed
            sw.spawnParticles(OddParticles.ZERO_GRAVITY, cx, cy, cz,
                    12, 0.45, 0.55, 0.45, 0.0);
        }
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (!entity.getWorld().isClient) {
            // hard-lock gravity off; no velocity damp—let them float if bumped
            entity.setNoGravity(true);
            entity.fallDistance = 0f;

            // gentle ambient particles while effect is active (server-spawned)
            // throttled to every other tick for perf
            if ((entity.age & 1) == 0) {
                ServerWorld sw = (ServerWorld) entity.getWorld();
                double cx = entity.getX();
                double cy = entity.getY() + entity.getHeight() * 0.5;
                double cz = entity.getZ();

                sw.spawnParticles(OddParticles.ZERO_GRAVITY, cx, cy, cz,
                        1,                  // count
                        0.25, 0.35, 0.25,   // spread (x,y,z)
                        0.0);               // speed
            }
        }
    }

    @Override
    public void onRemoved(LivingEntity entity, AttributeContainer attributes, int amplifier) {
        if (!entity.getWorld().isClient) {
            entity.setNoGravity(false);
        }
        super.onRemoved(entity, attributes, amplifier);
    }
}
