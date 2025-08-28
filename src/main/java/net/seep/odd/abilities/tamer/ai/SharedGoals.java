package net.seep.odd.abilities.tamer.ai;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.abilities.tamer.projectile.EmeraldShurikenEntity;

import java.util.EnumSet;
import java.util.UUID;

public final class SharedGoals {
    private SharedGoals() {}

    /* ===== constants ===== */
    private static final double PROTECT_RADIUS = 18.0;

    /** Follow owner with simple banding. */
    public static final class FollowOwnerGoalGeneric extends Goal {
        private final MobEntity mob;
        private final UUID ownerId;
        private final double speed, minSq, maxSq;
        private ServerPlayerEntity owner;
        private int recalc;

        public FollowOwnerGoalGeneric(MobEntity mob, UUID ownerId, double speed, double min, double max) {
            this.mob = mob;
            this.ownerId = ownerId;
            this.speed = speed;
            this.minSq = min * min;
            this.maxSq = max * max;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        private ServerPlayerEntity findOwner() {
            if (!(mob.getWorld() instanceof ServerWorld sw)) return null;
            var p = sw.getServer().getPlayerManager().getPlayer(ownerId);
            return (p != null && p.isAlive()) ? p : null;
        }

        @Override public boolean canStart() {
            owner = findOwner();
            return owner != null && mob.squaredDistanceTo(owner) > minSq;
        }

        @Override public boolean shouldContinue() {
            if (owner == null || !owner.isAlive()) return false;
            double d2 = mob.squaredDistanceTo(owner);
            return d2 > minSq && d2 > 4.0 && !mob.getNavigation().isIdle();
        }

        @Override public void start() { recalc = 0; }

        @Override public void tick() {
            if (owner == null) return;
            mob.getLookControl().lookAt(owner, 30.0f, 30.0f);
            if (--recalc <= 0) {
                recalc = 10;
                double d2 = mob.squaredDistanceTo(owner);
                if (d2 > maxSq) mob.getNavigation().startMovingTo(owner, speed);
                else if (d2 < minSq) mob.getNavigation().stop();
            }
        }
    }

    /** “Revenge” targeter. */
    public static final class RetaliateIfHurtGoal extends Goal {
        private final MobEntity mob;
        private LivingEntity lastAttacker;
        public RetaliateIfHurtGoal(MobEntity mob) {
            this.mob = mob;
            this.setControls(EnumSet.of(Control.TARGET));
        }
        @Override public boolean canStart() {
            var att = mob.getAttacker();
            if (att != null && att != lastAttacker && att.isAlive()) {
                lastAttacker = att;
                return true;
            }
            return false;
        }
        @Override public void start() {
            if (lastAttacker != null && lastAttacker.isAlive()) mob.setTarget(lastAttacker);
        }
        @Override public boolean shouldContinue() {
            var t = mob.getTarget();
            return t != null && t.isAlive();
        }
    }

    /** Defend owner; never pick self. */
    public static final class ProtectOwnerTargetGoal extends Goal {
        private final MobEntity mob;
        private final UUID ownerId;
        private ServerPlayerEntity owner;
        private LivingEntity candidate;
        private int cooldown;

        public ProtectOwnerTargetGoal(MobEntity mob, UUID ownerId) {
            this.mob = mob;
            this.ownerId = ownerId;
            this.setControls(EnumSet.of(Control.TARGET));
        }

        @Override public boolean canStart() {
            if (!(mob.getWorld() instanceof ServerWorld sw)) return false;
            owner = sw.getServer().getPlayerManager().getPlayer(ownerId);
            if (owner == null || !owner.isAlive()) return false;

            candidate = findBestHostileNearOwner();
            return candidate != null && candidate.isAlive();
        }

        @Override public void start() {
            if (candidate != null && candidate.isAlive()) mob.setTarget(candidate);
            cooldown = 0;
        }

        @Override public boolean shouldContinue() {
            var t = mob.getTarget();
            return t != null && t.isAlive() && t != owner;
        }

        @Override public void tick() {
            if (--cooldown <= 0) {
                cooldown = 20;
                LivingEntity best = findBestHostileNearOwner();
                var current = mob.getTarget();
                if (best != null && best.isAlive() && current != best) mob.setTarget(best);
            }
        }

        private LivingEntity findBestHostileNearOwner() {
            if (owner == null) return null;
            Box area = owner.getBoundingBox().expand(PROTECT_RADIUS);
            HostileEntity best = null;
            double bestScore = Double.MAX_VALUE;

            for (HostileEntity h : mob.getWorld().getEntitiesByClass(HostileEntity.class, area, HostileEntity::isAlive)) {
                if (h == mob) continue;                 // never pick ourself
                if (h.getTarget() == owner) return h;   // priority
                double s = h.squaredDistanceTo(owner);
                if (s < bestScore) { bestScore = s; best = h; }
            }
            return best;
        }
    }

    /** Fixed-damage melee for entities without ATTACK_DAMAGE attribute. */
    public static final class FixedDamageMeleeGoal extends Goal {
        private final PathAwareEntity mob;
        private final double speed;
        private final float damage;
        private final int cooldownTicks;
        private int cooldown;

        public FixedDamageMeleeGoal(PathAwareEntity mob, double speed, float damage, int cooldownTicks) {
            this.mob = mob;
            this.speed = speed;
            this.damage = damage;
            this.cooldownTicks = cooldownTicks;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override public boolean canStart() {
            var t = mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override public boolean shouldContinue() {
            var t = mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override public void start() { cooldown = 0; }

        @Override public void tick() {
            var target = mob.getTarget();
            if (target == null || target == mob) return;

            mob.getLookControl().lookAt(target, 30.0f, 30.0f);
            mob.getNavigation().startMovingTo(target, speed);

            double reachSq = (mob.getWidth() * 2.0F) * (mob.getWidth() * 2.0F) + target.getWidth();
            if (--cooldown <= 0 && mob.squaredDistanceTo(target) <= reachSq && mob.getVisibilityCache().canSee(target)) {
                target.damage(mob.getDamageSources().mobAttack(mob), damage);
                cooldown = cooldownTicks;
            }
        }
    }

    /** Ranged kiting/orbiting shuriken throw. */
    public static final class RangedKiteShurikenGoal extends Goal {
        private final PathAwareEntity mob;
        private final double moveSpeed;
        private final double minRangeSq;
        private final double maxRangeSq;
        private final int cooldownTicks;
        private final double orbitRadius;
        private int cooldown;
        private int orbitTime;
        private final int orbitDir;

        public RangedKiteShurikenGoal(PathAwareEntity mob, double moveSpeed,
                                      double minRange, double maxRange,
                                      int cooldownTicks, double orbitRadius) {
            this.mob = mob;
            this.moveSpeed = moveSpeed;
            this.minRangeSq = minRange * minRange;
            this.maxRangeSq = maxRange * maxRange;
            this.cooldownTicks = cooldownTicks;
            this.orbitRadius = orbitRadius;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
            this.orbitDir = (mob.getUuid().getLeastSignificantBits() & 1L) == 0L ? 1 : -1;
        }

        @Override public boolean canStart() {
            var t = mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override public boolean shouldContinue() {
            var t = mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override public void start() {
            cooldown = 0;
            orbitTime = 0;
        }

        @Override public void tick() {
            var t = mob.getTarget();
            if (t == null || t == mob) return;

            mob.getLookControl().lookAt(t, 30f, 30f);

            double d2 = mob.squaredDistanceTo(t);
            boolean canSee = mob.getVisibilityCache().canSee(t);

            if (d2 < minRangeSq * 0.85) {
                Vec3d away = mob.getPos().subtract(t.getPos()).normalize();
                Vec3d dest = mob.getPos().add(away.multiply(2.5));
                mob.getNavigation().startMovingTo(dest.x, dest.y, dest.z, moveSpeed);
            } else if (d2 > maxRangeSq * 1.15) {
                mob.getNavigation().startMovingTo(t, moveSpeed);
            } else {
                orbitTime++;
                double angle = (orbitTime * 0.25) * orbitDir;
                Vec3d center = t.getPos();
                Vec3d offset = new Vec3d(Math.cos(angle), 0, Math.sin(angle)).multiply(orbitRadius);
                Vec3d dest = center.add(offset);
                mob.getNavigation().startMovingTo(dest.x, dest.y, dest.z, moveSpeed);
            }

            if (canSee && --cooldown <= 0 && d2 >= minRangeSq && d2 <= maxRangeSq) {
                if (mob.getWorld() instanceof ServerWorld sw) {
                    EmeraldShurikenEntity s = new EmeraldShurikenEntity(sw, mob);
                    Vec3d eye = mob.getEyePos();
                    Vec3d to  = t.getEyePos().subtract(eye).normalize();
                    double speed = 1.6;
                    s.setPosition(eye.x, eye.y - 0.1, eye.z);
                    s.setVelocity(to.x * speed, to.y * speed, to.z * speed, 1.0f, 0.0f);
                    s.setOwner(mob);
                    sw.spawnEntity(s);
                }
                cooldown = cooldownTicks;
            }
        }
    }
    public static final class ChargeTackleGoal extends Goal {
        private enum Phase { SEEK, CHARGE, DASH, COOLDOWN }

        private final PathAwareEntity mob;
        private final int chargeTicks;
        private final double dashSpeed;
        private final float damage;
        private final double knockback;
        private final int cooldownTicks;

        private Phase phase = Phase.SEEK;
        private int ticker = 0;            // counts within current phase
        private int dashTimeMax = 12;      // limit dash duration
        private LivingEntity lastTarget = null;

        public ChargeTackleGoal(PathAwareEntity mob, int chargeTicks, double dashSpeed, float damage, double knockback, int cooldownTicks) {
            this.mob = mob;
            this.chargeTicks = Math.max(5, chargeTicks);
            this.dashSpeed = dashSpeed;
            this.damage = damage;
            this.knockback = knockback;
            this.cooldownTicks = Math.max(10, cooldownTicks);
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override public boolean canStart() {
            var t = mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override public boolean shouldContinue() {
            var t = mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override public void start() {
            phase = Phase.SEEK;
            ticker = 0;
            lastTarget = mob.getTarget();
        }

        @Override public void tick() {
            var t = mob.getTarget();
            if (t == null || t == mob) { reset(); return; }

            switch (phase) {
                case SEEK -> {
                    // Move within ~10 blocks then begin charging if we can see the target
                    mob.getLookControl().lookAt(t, 30, 30);
                    double d2 = mob.squaredDistanceTo(t);
                    boolean canSee = mob.getVisibilityCache().canSee(t);

                    if (!canSee || d2 > 100.0) { // >10 blocks
                        mob.getNavigation().startMovingTo(t, 1.15);
                        return;
                    }
                    // close enough and visible: begin charge
                    mob.getNavigation().stop();
                    phase = Phase.CHARGE;
                    ticker = 0;
                }

                case CHARGE -> {
                    // Face target, vibrate a bit, spawn charge particles
                    mob.getLookControl().lookAt(t, 30, 30);
                    ticker++;
                    if (mob.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
                        var p = mob.getPos().add(0, mob.getHeight() * 0.5, 0);
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT, p.x, p.y, p.z, 6, 0.2, 0.2, 0.2, 0.0);
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD, p.x, p.y - 0.2, p.z, 2, 0.05, 0.02, 0.05, 0.0);
                    }
                    if (ticker >= chargeTicks) {
                        phase = Phase.DASH;
                        ticker = 0;
                        // launch toward current target
                        var dir = t.getPos().subtract(mob.getPos()).normalize();
                        mob.getNavigation().stop();
                        mob.setVelocity(dir.x * dashSpeed, 0.05, dir.z * dashSpeed);
                        mob.velocityModified = true;
                    }
                }

                case DASH -> {
                    ticker++;
                    // keep facing & push a bit forward for a short burst
                    mob.getLookControl().lookAt(t, 30, 30);
                    var vel = mob.getVelocity();
                    mob.setVelocity(vel.x * 0.98, vel.y, vel.z * 0.98);

                    // hit check: AABB overlap or very close
                    if (mob.getBoundingBox().expand(0.2).intersects(t.getBoundingBox()) ||
                            mob.squaredDistanceTo(t) < 1.1) {

                        var src = mob.getDamageSources().mobAttack(mob);
                        t.damage(src, damage);
                        t.takeKnockback(knockback, -mob.getVelocity().x, -mob.getVelocity().z);

                        if (mob.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
                            var p = t.getPos().add(0, t.getHeight() * 0.6, 0);
                            sw.spawnParticles(net.minecraft.particle.ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 6, 0.2, 0.2, 0.2, 0.0);
                            sw.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT, p.x, p.y, p.z, 8, 0.1, 0.1, 0.1, 0.0);
                        }

                        phase = Phase.COOLDOWN;
                        ticker = 0;
                        break;
                    }

                    if (ticker >= dashTimeMax) {
                        phase = Phase.COOLDOWN;
                        ticker = 0;
                    }
                }

                case COOLDOWN -> {
                    ticker++;
                    if (ticker >= cooldownTicks) {
                        phase = Phase.SEEK;
                        ticker = 0;
                    }
                }
            }
        }

        @Override public void stop() {
            mob.getNavigation().stop();
        }

        private void reset() {
            phase = Phase.SEEK;
            ticker = 0;
        }
    }
}
