package net.seep.odd.abilities.tamer.ai.behavior;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.abilities.tamer.entity.VillagerEvoEntity;

import java.util.EnumSet;

/** Timed head-butt barrage: windup -> repeated hits until total duration. */
public class HeadButterBehavior {
    private final int   windupTicks;
    private final int   intervalTicks;
    private final int   totalTicks;
    private final float damagePerHit;

    public HeadButterBehavior(int windupTicks, int intervalTicks, int totalTicks, float damagePerHit) {
        this.windupTicks   = windupTicks;
        this.intervalTicks = intervalTicks;
        this.totalTicks    = totalTicks;
        this.damagePerHit  = damagePerHit;
    }

    public Goal asGoal(PathAwareEntity mob, ServerPlayerEntity owner) {
        return new Impl(mob);
    }

    private final class Impl extends Goal {
        private final PathAwareEntity mob;
        private int localTick;
        private int nextHit;

        Impl(PathAwareEntity mob) {
            this.mob = mob;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override public boolean canStart() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override public boolean shouldContinue() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive() && localTick < totalTicks;
        }

        @Override public void start() {
            localTick = 0;
            nextHit   = windupTicks;
            mob.getNavigation().stop();

            if (mob instanceof VillagerEvoEntity evo) {
                evo.triggerAttackAnimation(totalTicks); // drive "attack" anim while goal runs
            }
        }

        @Override public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) return;

            mob.getLookControl().lookAt(t, 30f, 30f);

            // scoot closer if we’re not quite there
            double desiredSq = 3.0 * 3.0;
            if (mob.squaredDistanceTo(t) > desiredSq) {
                mob.getNavigation().startMovingTo(t, 1.15);
            } else {
                mob.getNavigation().stop();
            }

            localTick++;
            while (localTick >= nextHit && localTick <= totalTicks) {
                doHit(t);
                nextHit += intervalTicks;
            }
        }

        private void doHit(LivingEntity target) {
            if (!(mob.getWorld() instanceof ServerWorld sw)) return;

            // Damage
            target.damage(mob.getDamageSources().mobAttack(mob), damagePerHit);

            // Knock
            Vec3d dir = new Vec3d(
                    -MathHelper.sin(mob.getYaw() * 0.017453292F),
                    0.0,
                    MathHelper.cos(mob.getYaw() * 0.017453292F)
            ).normalize();
            target.addVelocity(dir.x * 0.25, 0.10, dir.z * 0.25);
            target.velocityModified = true;

            // Particles
            Vec3d p = target.getPos().add(0, target.getStandingEyeHeight() * 0.6, 0);
            for (int i = 0; i < 6; i++) {
                double ox = (mob.getRandom().nextDouble() - 0.5) * 0.6;
                double oy = (mob.getRandom().nextDouble() - 0.5) * 0.4;
                double oz = (mob.getRandom().nextDouble() - 0.5) * 0.6;
                sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, p.x + ox, p.y + oy, p.z + oz, 1, 0, 0, 0, 0);
                sw.spawnParticles(ParticleTypes.CRIT,           p.x + ox, p.y + oy, p.z + oz, 1, 0, 0, 0, 0);
            }
        }
    }
}
