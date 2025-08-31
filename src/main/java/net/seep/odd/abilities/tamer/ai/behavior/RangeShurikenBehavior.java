package net.seep.odd.abilities.tamer.ai.behavior;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.abilities.tamer.projectile.EmeraldShurikenEntity;

import java.util.EnumSet;

/** Ranged kiting/orbiting shuriken thrower. */
public class RangeShurikenBehavior {
    private final double moveSpeed;
    private final double minRange;
    private final double maxRange;
    private final int    cooldownTicks;
    private final double orbitRadius;

    public RangeShurikenBehavior(double moveSpeed, double minRange, double maxRange,
                                 int cooldownTicks, double orbitRadius) {
        this.moveSpeed     = moveSpeed;
        this.minRange      = minRange;
        this.maxRange      = maxRange;
        this.cooldownTicks = cooldownTicks;
        this.orbitRadius   = orbitRadius;
    }

    /** Build a Goal instance usable in a GoalSelector: goals.add(priority, behavior.asGoal(...)) */
    public Goal asGoal(PathAwareEntity mob, ServerPlayerEntity owner) {
        return new Impl(mob);
    }

    private final class Impl extends Goal {
        private final PathAwareEntity mob;
        private int cooldown;
        private int orbitTime;
        private final int orbitDir; // +1 or -1

        Impl(PathAwareEntity mob) {
            this.mob = mob;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
            this.orbitDir = (mob.getUuid().getLeastSignificantBits() & 1L) == 0L ? 1 : -1;
        }

        @Override public boolean canStart() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override public boolean shouldContinue() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override public void start() {
            cooldown = 0;
            orbitTime = 0;
        }

        @Override public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) return;

            mob.getLookControl().lookAt(t, 30f, 30f);

            double d2     = mob.squaredDistanceTo(t);
            double minSq  = minRange * minRange;
            double maxSq  = maxRange * maxRange;
            boolean sees  = mob.getVisibilityCache().canSee(t);

            // position: back off / close in / orbit
            if (d2 < minSq * 0.85) {
                Vec3d away = mob.getPos().subtract(t.getPos()).normalize();
                Vec3d dest = mob.getPos().add(away.multiply(2.5));
                mob.getNavigation().startMovingTo(dest.x, dest.y, dest.z, moveSpeed);
            } else if (d2 > maxSq * 1.15) {
                mob.getNavigation().startMovingTo(t, moveSpeed);
            } else {
                orbitTime++;
                double ang  = (orbitTime * 0.25) * orbitDir;
                Vec3d  ctr  = t.getPos();
                Vec3d  off  = new Vec3d(Math.cos(ang), 0, Math.sin(ang)).multiply(orbitRadius);
                Vec3d  dest = ctr.add(off);
                mob.getNavigation().startMovingTo(dest.x, dest.y, dest.z, moveSpeed);
            }

            // throw
            if (sees && --cooldown <= 0 && d2 >= minSq && d2 <= maxSq) {
                if (mob.getWorld() instanceof ServerWorld sw) {
                    EmeraldShurikenEntity s = new EmeraldShurikenEntity(sw, mob);
                    Vec3d eye = mob.getEyePos();
                    Vec3d to  = t.getEyePos().subtract(eye).normalize();
                    double spd = 1.6;
                    s.setPosition(eye.x, eye.y - 0.1, eye.z);
                    s.setVelocity(to.x * spd, to.y * spd, to.z * spd, 1.0f, 0.0f);
                    s.setOwner(mob);
                    sw.spawnEntity(s);
                }
                cooldown = cooldownTicks;
            }
        }
    }
}