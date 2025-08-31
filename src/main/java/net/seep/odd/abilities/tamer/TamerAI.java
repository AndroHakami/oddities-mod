package net.seep.odd.abilities.tamer;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.seep.odd.mixin.MobEntityAccessor;
import net.seep.odd.abilities.tamer.ai.SpeciesGoals;

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

        // If the mob HAS an attack attribute, gently bump tiny values so it can do some damage.
        var atk = mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        boolean hasAttackAttribute = (atk != null);
        if (hasAttackAttribute && atk.getBaseValue() < 2.0) {
            atk.setBaseValue(2.0);
        }

        // selectors via accessor (protected)
        var goals   = ((MobEntityAccessor)(Object)mob).odd$getGoalSelector();
        var targets = ((MobEntityAccessor)(Object)mob).odd$getTargetSelector();

        // wipe existing goals
        mob.clearGoals(g -> true);
        for (var e : new java.util.ArrayList<>(targets.getGoals())) {
            targets.remove(e.getGoal());
        }

        // -------- Action goals (priority ladder) --------
        goals.add(0, new SwimGoal(mob));

        boolean speciesHandled = SpeciesGoals.applyFor(mob, owner, goals, targets); // << species-specific moves

        // If species didn’t provide primary combat, give a sane default
        if (!speciesHandled && mob instanceof PathAwareEntity paw) {
            if (hasAttackAttribute) {
                goals.add(2, new MeleeAttackGoal(paw, 1.25, true));
            } else {
                goals.add(2, new FixedDamageMeleeGoal(paw, 1.20, 3.0f, 18));
            }
        }

        // Follow + wander are always useful
        if (mob instanceof PathAwareEntity paw) {
            goals.add(3, new FollowOwnerGoalGeneric(mob, owner.getUuid(), FOLLOW_SPEED, FOLLOW_MIN, FOLLOW_MAX));
            goals.add(6, new WanderAroundFarGoal(paw, 1.0));
        }
        goals.add(7, new LookAtEntityGoal(mob, ServerPlayerEntity.class, 10.0f));
        goals.add(8, new LookAroundGoal(mob));

        // -------- Target goals (defense/retaliation) --------
        targets.add(1, new RetaliateIfHurtGoal(mob));
        targets.add(2, new ProtectOwnerTargetGoal(mob, owner.getUuid()));
    }

    /* ================= helper goals ================= */

    /** Follow the owner with simple distance banding. */
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

    /** “Revenge” targeting. */
    static final class RetaliateIfHurtGoal extends Goal {
        private final MobEntity mob;
        private LivingEntity lastAttacker;
        RetaliateIfHurtGoal(MobEntity mob) {
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

    /** Protect the owner by choosing a hostile near/attacking the owner. */
    static final class ProtectOwnerTargetGoal extends Goal {
        private final MobEntity mob;
        private final UUID ownerId;
        private ServerPlayerEntity owner;
        private LivingEntity candidate;
        private int cooldown;

        ProtectOwnerTargetGoal(MobEntity mob, UUID ownerId) {
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
                if (h.getTarget() == owner) return h; // priority
                double s = h.squaredDistanceTo(owner);
                if (s < bestScore) { bestScore = s; best = h; }
            }
            return best;
        }
    }

    /**
     * Melee that does NOT rely on GENERIC_ATTACK_DAMAGE (for villagers & other passives that lack it).
     * Deals a fixed amount with a small cooldown.
     */
    static final class FixedDamageMeleeGoal extends Goal {
        private final PathAwareEntity mob;
        private final double speed;
        private final float damage;
        private final int cooldownTicks;
        private int cooldown;

        FixedDamageMeleeGoal(PathAwareEntity mob, double speed, float damage, int cooldownTicks) {
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
            if (target == null) return;

            mob.getLookControl().lookAt(target, 30.0f, 30.0f);
            mob.getNavigation().startMovingTo(target, speed);

            double reachSq = (mob.getWidth() * 2.0F) * (mob.getWidth() * 2.0F) + target.getWidth();

            if (--cooldown <= 0 && mob.squaredDistanceTo(target) <= reachSq && mob.getVisibilityCache().canSee(target)) {
                target.damage(mob.getDamageSources().mobAttack(mob), damage);
                cooldown = cooldownTicks;
            }
        }
    }
}
