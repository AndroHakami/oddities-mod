package net.seep.odd.abilities.tamer.ai.behavior;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.abilities.tamer.entity.VillagerEvoEntity;

import java.util.EnumSet;

/**
 * VillagerEvo special: rush close, "grab", then head-butt every 0.2s after a 0.96s warmup.
 * Priority system: higher weight => shorter cooldown => used more often.
 */
public final class HeadButterBehavior implements CompanionBehavior {

    private final int priorityWeight;   // >=1; higher means more frequent
    private final int baseCooldown;     // baseline between barrages

    public HeadButterBehavior() {
        this(1);
    }

    public HeadButterBehavior(int priorityWeight) {
        this(priorityWeight, 50);
    }

    public HeadButterBehavior(int priorityWeight, int baseCooldownTicks) {
        this.priorityWeight = Math.max(1, priorityWeight);
        this.baseCooldown = Math.max(10, baseCooldownTicks);
    }

    @Override
    public void apply(MobEntity mob, ServerPlayerEntity owner, GoalSelector goals, GoalSelector targets) {
        if (!(mob instanceof PathAwareEntity paw)) return;

        // Fast approach so the grab feels snappy
        goals.add(2, new ApproachWithinGoal(paw, 1.45, 2.2));

        // Animation-synced barrage (weight scales cooldown)
        goals.add(3, new HeadbuttBarrageGoal(mob, scaledCooldown()));
    }

    private int scaledCooldown() {
        // simple bias: divide baseline by weight (cap at 8 ticks so it never spams)
        return Math.max(8, baseCooldown / priorityWeight);
    }

    /** Runs toward target until within stopRange. */
    private static final class ApproachWithinGoal extends Goal {
        private final PathAwareEntity mob;
        private final double speed;
        private final double stopRangeSq;

        ApproachWithinGoal(PathAwareEntity mob, double speed, double stopRange) {
            this.mob = mob;
            this.speed = speed;
            this.stopRangeSq = stopRange * stopRange;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override public boolean canStart() {
            var t = mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override public boolean shouldContinue() {
            var t = mob.getTarget();
            if (t == null || !t.isAlive()) return false;
            return mob.squaredDistanceTo(t) > stopRangeSq && !mob.getNavigation().isIdle();
        }

        @Override public void tick() {
            var t = mob.getTarget();
            if (t == null) return;
            mob.getLookControl().lookAt(t, 30f, 30f);
            if (mob.squaredDistanceTo(t) > stopRangeSq) {
                mob.getNavigation().startMovingTo(t, speed);
            } else {
                mob.getNavigation().stop();
            }
        }
    }

    /**
     * Grabs target, waits ~0.96s, then hits every 0.2s for 4s.
     * Plays "attack" animation if the mob supports it (VillagerEvoEntity).
     */
    private static final class HeadbuttBarrageGoal extends Goal {
        private final MobEntity mob;

        // timings (ticks @ 20 TPS)
        private static final int WARMUP_TICKS     = 19;  // ≈0.96s
        private static final int HIT_EVERY_TICKS  = 4;   // 0.20s
        private static final int PUMMEL_DURATION  = 80;  // 4.0s
        private static final int TOTAL_TICKS      = WARMUP_TICKS + PUMMEL_DURATION;

        private final int cooldownBetweenBarrages;

        private int tick;          // since start()
        private int nextHitAt;     // next scheduled hit
        private int cooldown;      // idle cooldown
        private boolean grabbing;

        HeadbuttBarrageGoal(MobEntity mob, int cooldownBetweenBarrages) {
            this.mob = mob;
            this.cooldownBetweenBarrages = cooldownBetweenBarrages;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override public boolean canStart() {
            if (cooldown > 0) { cooldown--; return false; }
            var t = mob.getTarget();
            return t != null && t.isAlive() && mob.squaredDistanceTo(t) <= 3.2 * 3.2;
        }

        @Override public boolean shouldContinue() {
            var t = mob.getTarget();
            return t != null && t.isAlive() && tick < TOTAL_TICKS;
        }

        @Override public void start() {
            tick = 0;
            grabbing = true;
            nextHitAt = WARMUP_TICKS;
            mob.getNavigation().stop();

            if (mob instanceof VillagerEvoEntity evo) {
                evo.triggerAttackAnimation(TOTAL_TICKS); // drive GeckoLib "attack" clip
            }
        }

        @Override public void stop() {
            cooldown = cooldownBetweenBarrages;
            grabbing = false;
        }

        @Override public void tick() {
            var target = mob.getTarget();
            if (target == null) return;

            tick++;
            mob.getLookControl().lookAt(target, 30f, 30f);

            // While grabbing (warmup + pummel), gently hold the target in front
            if (grabbing) pullTowardFront(target, 0.45);

            // Warmup sparkles
            if (tick <= WARMUP_TICKS && mob.getWorld() instanceof ServerWorld sw) {
                var p = target.getPos().add(0, target.getStandingEyeHeight() * 0.6, 0);
                sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, p.x, p.y, p.z, 6, 0.15, 0.15, 0.15, 0.0);
            }

            // Scheduled hits after warmup
            while (tick >= nextHitAt && nextHitAt <= TOTAL_TICKS) {
                doHit(target);
                nextHitAt += HIT_EVERY_TICKS;
            }
        }

        private void doHit(LivingEntity target) {
            if (!(mob.getWorld() instanceof ServerWorld sw)) return;

            float damage = 3.5f; // tune
            target.damage(mob.getDamageSources().mobAttack(mob), damage);

            // forward knock based on yaw
            Vec3d dir = new Vec3d(
                    -MathHelper.sin(mob.getYaw() * 0.017453292F),
                    0.0,
                    MathHelper.cos(mob.getYaw() * 0.017453292F)
            ).normalize();
            target.addVelocity(dir.x * 0.25, 0.10, dir.z * 0.25);
            target.velocityModified = true;

            // green sparkles + crit hit marks
            Vec3d p = target.getPos().add(0, target.getStandingEyeHeight() * 0.6, 0);
            sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, p.x, p.y, p.z, 6, 0.15, 0.10, 0.15, 0.0);
            sw.spawnParticles(ParticleTypes.CRIT,           p.x, p.y, p.z, 6, 0.10, 0.05, 0.10, 0.0);
        }

        private void pullTowardFront(LivingEntity target, double strength) {
            Vec3d fwd = mob.getRotationVec(1.0f).normalize();
            Vec3d anchor = mob.getPos().add(fwd.multiply(0.6)).add(0, mob.getHeight() * 0.6, 0);
            Vec3d delta = anchor.subtract(target.getPos());
            target.setVelocity(delta.multiply(strength));
            target.velocityModified = true;
        }
    }
}