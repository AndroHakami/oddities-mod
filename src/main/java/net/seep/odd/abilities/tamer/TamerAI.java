// net/seep/odd/abilities/tamer/TamerAI.java
package net.seep.odd.abilities.tamer;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.mixin.MobEntityAccessor;

import java.util.EnumSet;
import java.util.UUID;

public final class TamerAI {
    private TamerAI() {}

    private static final double FOLLOW_SPEED   = 1.20;
    private static final double FOLLOW_MIN     = 2.5;
    private static final double FOLLOW_MAX     = 10.0;
    private static final double PROTECT_RADIUS = 18.0;

    /** Call right after constructing/positioning the mob and BEFORE spawnEntity(...). */
    public static void install(MobEntity mob, ServerPlayerEntity owner) {
        mob.setPersistent();
        mob.setTarget(null);
        mob.setAttacking(false);

        // Give passive mobs a tiny attack stat if they have one (avoid crashes)
        var atk = mob.getAttributes().hasAttribute(EntityAttributes.GENERIC_ATTACK_DAMAGE)
                ? mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE)
                : null;
        if (atk != null && atk.getBaseValue() < 2.0) atk.setBaseValue(2.0);

        // 1) Wipe brain + action goals
        mob.clearGoalsAndTasks(); // clears GoalSelector AND Brain()

        // 2) Wipe target goals via accessor
        var goals   = ((MobEntityAccessor)(Object)mob).odd$getGoalSelector();
        var targets = ((MobEntityAccessor)(Object)mob).odd$getTargetSelector();
        for (var e : new java.util.ArrayList<>(targets.getGoals())) {
            targets.remove(e.getGoal());
        }

        // ---------- ACTION GOALS ----------
        goals.add(0, new SwimGoal(mob));

        if (mob instanceof net.minecraft.entity.passive.VillagerEntity) {
            // Villager: prefer ranged (emerald shuriken), melee only if cornered
            goals.add(2, new VillagerShurikenGoal(mob, owner.getUuid(), 1.10, 3.0, 18.0, 24));
            goals.add(3, new FollowOwnerGoalGeneric(mob, owner.getUuid(), FOLLOW_SPEED, FOLLOW_MIN, FOLLOW_MAX));
            goals.add(4, new SimpleMeleeGoal(mob, 1.05, 2.0f, 25)); // tiny fallback
        } else if (mob instanceof PathAwareEntity paw) {
            // Generic tamed behaviour
            if (atk != null) goals.add(2, new MeleeAttackGoal(paw, 1.25, true));
            else goals.add(2, new SimpleMeleeGoal(mob, 1.10, 2.0f, 20));
            goals.add(3, new FollowOwnerGoalGeneric(mob, owner.getUuid(), FOLLOW_SPEED, FOLLOW_MIN, FOLLOW_MAX));
            goals.add(6, new WanderAroundFarGoal(paw, 1.0));
        }

        goals.add(7, new LookAtEntityGoal(mob, ServerPlayerEntity.class, 10.0f));
        goals.add(8, new LookAroundGoal(mob));

        // ---------- TARGET GOALS ----------
        targets.add(1, new RetaliateIfHurtGoal(mob));                   // retaliate if hit
        targets.add(2, new ProtectOwnerTargetGoal(mob, owner.getUuid())); // protect owner
    }

    /* ------------ helper goals (no .target.* classes needed) ------------ */

    static final class FollowOwnerGoalGeneric extends Goal {
        private final MobEntity mob;
        private final UUID ownerId;
        private final double speed, minSq, maxSq;
        private ServerPlayerEntity owner;
        private int recalc;

        FollowOwnerGoalGeneric(MobEntity mob, UUID ownerId, double speed, double min, double max) {
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

    /** Retaliate against whoever hurt this mob last. */
    static final class RetaliateIfHurtGoal extends Goal {
        private final MobEntity mob;
        private LivingEntity lastAttacker;

        RetaliateIfHurtGoal(MobEntity mob) { this.mob = mob; }

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

    /** Pick/maintain a hostile near the owner (never the owner). */
    static final class ProtectOwnerTargetGoal extends Goal {
        private final MobEntity mob;
        private final UUID ownerId;
        private ServerPlayerEntity owner;
        private LivingEntity candidate;
        private int cooldown;

        ProtectOwnerTargetGoal(MobEntity mob, UUID ownerId) {
            this.mob = mob;
            this.ownerId = ownerId;
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
                // Highest priority: anything actively targeting the owner
                if (h.getTarget() == owner) return h;

                // Otherwise pick the closest hostile to the owner
                double s = h.squaredDistanceTo(owner);
                if (s < bestScore) {
                    bestScore = s;
                    best = h;
                }
            }
            return best;
        }
    }

    /* --------- attribute-less melee fallback to avoid villager crash --------- */
    static final class SimpleMeleeGoal extends Goal {
        private final MobEntity mob;
        private final double speed;
        private final float reach;       // squared distance to trigger swing
        private final int swingCooldown;
        private int cd;

        SimpleMeleeGoal(MobEntity mob, double speed, float reach, int cooldown) {
            this.mob = mob;
            this.speed = speed;
            this.reach = reach;
            this.swingCooldown = cooldown;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override public boolean canStart() { return mob.getTarget() != null; }
        @Override public boolean shouldContinue() {
            var t = mob.getTarget();
            return t != null && t.isAlive();
        }
        @Override public void start() { cd = 5; }

        @Override public void tick() {
            var t = mob.getTarget();
            if (t == null) return;

            mob.getLookControl().lookAt(t, 30, 30);
            mob.getNavigation().startMovingTo(t, speed);

            double d2 = mob.squaredDistanceTo(t);
            if (cd > 0) cd--;
            if (d2 <= (double)(reach * reach) && cd <= 0) {
                cd = swingCooldown;
                mob.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                mob.tryAttack(t); // uses whatever damage the mob can produce (0 is fine; effects still apply)
            }
        }
    }
    static final class VillagerShurikenGoal extends Goal {
        private final MobEntity mob;
        private final java.util.UUID ownerId;
        private final double moveSpeed;
        private final double minSq, maxSq;
        private final int cooldownTicks;

        private int throwCd;
        private int replanTicks = 0;
        private int strafeDir = 1; // 1=clockwise, -1=counter

        private ServerPlayerEntity owner;

        VillagerShurikenGoal(MobEntity mob, java.util.UUID ownerId, double moveSpeed, double minRange, double maxRange, int cooldownTicks) {
            this.mob = mob;
            this.ownerId = ownerId;
            this.moveSpeed = moveSpeed;
            this.minSq = minRange * minRange;
            this.maxSq = maxRange * maxRange;
            this.cooldownTicks = cooldownTicks;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        private ServerPlayerEntity findOwner() {
            if (!(mob.getWorld() instanceof ServerWorld sw)) return null;
            var p = sw.getServer().getPlayerManager().getPlayer(ownerId);
            return (p != null && p.isAlive()) ? p : null;
        }

        private LivingEntity findHostileNearOwner() {
            if (owner == null) return null;
            var box = owner.getBoundingBox().expand(PROTECT_RADIUS);
            HostileEntity best = null;
            double bestD2 = Double.MAX_VALUE;
            for (HostileEntity h : mob.getWorld().getEntitiesByClass(HostileEntity.class, box, HostileEntity::isAlive)) {
                double d2 = h.squaredDistanceTo(owner);
                if (d2 < bestD2) { bestD2 = d2; best = h; }
            }
            return best;
        }

        @Override public boolean canStart() {
            owner = findOwner();
            if (owner == null) return false;
            var t = mob.getTarget();
            if (t == null || !t.isAlive()) {
                t = findHostileNearOwner();
                if (t != null) mob.setTarget(t);
            }
            return t != null && t.isAlive();
        }

        @Override public boolean shouldContinue() {
            var t = mob.getTarget();
            return owner != null && owner.isAlive() && t != null && t.isAlive();
        }

        @Override public void start() {
            throwCd = 8;
            replanTicks = 0;
            strafeDir = mob.getRandom().nextBoolean() ? 1 : -1;
        }

        @Override public void tick() {
            var t = mob.getTarget();
            if (t == null || !t.isAlive()) {
                t = findHostileNearOwner();
                if (t != null) mob.setTarget(t);
                else return;
            }

            boolean canSee = mob.getVisibilityCache().canSee(t);
            double d2 = mob.squaredDistanceTo(t);

            // Movement plan
            if (d2 > maxSq) {
                // too far: close in
                mob.getNavigation().startMovingTo(t, moveSpeed);
            } else {
                // inside preferred band: strafe/circle
                if (--replanTicks <= 0 || mob.getNavigation().isIdle()) {
                    replanTicks = 10 + mob.getRandom().nextInt(10);

                    // radial & tangent on XZ plane
                    Vec3d to = mob.getPos().subtract(t.getPos());
                    double dist = Math.max(1e-3, Math.sqrt(d2));
                    Vec3d radial = new Vec3d(to.x / dist, 0, to.z / dist);
                    Vec3d tangent = new Vec3d(-radial.z, 0, radial.x).multiply(strafeDir);

                    // sometimes flip direction to feel alive
                    if (mob.getRandom().nextFloat() < 0.12f) strafeDir *= -1;

                    // stay near the ring and sidestep along tangent
                    double rMin = Math.sqrt(minSq) + 0.5;
                    double rMax = Math.sqrt(maxSq) - 0.5;
                    double rNow = MathHelper.clamp(dist, rMin, rMax);

                    Vec3d targetPos = t.getPos()
                            .add(radial.multiply((rNow - dist) * 0.75)) // nudge toward ring if off
                            .add(tangent.multiply(1.2 + mob.getRandom().nextDouble() * 1.5)); // side run

                    mob.getNavigation().startMovingTo(targetPos.x, targetPos.y, targetPos.z, moveSpeed);
                }
            }

            // Face target
            mob.getLookControl().lookAt(t, 30.0f, 30.0f);

            // Fire
            if (throwCd > 0) throwCd--;
            if (throwCd <= 0 && canSee && d2 >= minSq && d2 <= maxSq) {
                throwCd = cooldownTicks;
                throwShuriken(t);
            }
        }

        private void throwShuriken(LivingEntity target) {
            if (!(mob.getWorld() instanceof ServerWorld sw)) return;
            var proj = new net.seep.odd.abilities.tamer.projectile.EmeraldShurikenEntity(sw, mob);
            proj.refreshPositionAndAngles(mob.getX(), mob.getEyeY() - 0.1, mob.getZ(), mob.getYaw(), mob.getPitch());

            Vec3d dir = target.getEyePos().subtract(proj.getPos()).normalize();
            proj.setVelocity(dir.x, dir.y, dir.z, 1.7f, 0.9f);

            sw.spawnEntity(proj);
            mob.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        }
    }
}
