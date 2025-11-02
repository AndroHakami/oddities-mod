package net.seep.odd.entity.falsefrog;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.*;
import java.util.function.Predicate;

/**
 * False Frog — Hostile leaper with mid-air tongue stab.
 * Now supports full 3D aiming (yaw + pitch) during the attack.
 */
public class FalseFrogEntity extends HostileEntity implements GeoEntity {
    public static final float WIDTH  = 2.4f;
    public static final float HEIGHT = 1.7f;

    // Tunables
    public static final double TONGUE_REACH   = 6.8;   // max reach (blocks)
    private static final double TONGUE_RADIUS = 0.35;  // sweep thickness (radius)
    private static final double MOUTH_Y_OFFSET = 1.25; // mouth height from feet

    // Anim lengths (ticks @20TPS)
    private static final int ATTACK_ANIM_TICKS = 24;   // ~1.2s
    private static final int STANCE_ANIM_TICKS = 34;
    private static final int LEAP_ANIM_TICKS   = 53;
    private static final int LAND_ANIM_TICKS   = 12;

    // Client-synced anim timers
    private static final TrackedData<Integer> ATTACK_TIME =
            DataTracker.registerData(FalseFrogEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> STANCE_TIME =
            DataTracker.registerData(FalseFrogEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> LEAP_TIME =
            DataTracker.registerData(FalseFrogEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> LAND_TIME =
            DataTracker.registerData(FalseFrogEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private int attackTicks;
    private int stanceTicks;
    private int leapTicks;
    private int landTicks;

    // De-dupe hits per swing
    private final Set<UUID> hitThisSwing = new HashSet<>();

    // GeckoLib
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation IDLE   = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK   = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation STANCE = RawAnimation.begin().thenPlay("stance");
    private static final RawAnimation LEAP   = RawAnimation.begin().thenPlay("leap");
    private static final RawAnimation LAND   = RawAnimation.begin().thenPlay("land");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");

    public FalseFrogEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.setStepHeight(1.25f);
        this.moveControl = new FrogMoveControl(this);
        this.experiencePoints = 10;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.23)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.1);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(ATTACK_TIME, 0);
        this.dataTracker.startTracking(STANCE_TIME, 0);
        this.dataTracker.startTracking(LEAP_TIME, 0);
        this.dataTracker.startTracking(LAND_TIME, 0);
    }

    private void setAttackAnim(int t) { attackTicks = t; dataTracker.set(ATTACK_TIME, t); }
    private void setStanceAnim(int t) { stanceTicks = t; dataTracker.set(STANCE_TIME, t); }
    private void setLeapAnim(int t)   { leapTicks   = t; dataTracker.set(LEAP_TIME, t); }
    private void setLandAnim(int t)   { landTicks   = t; dataTracker.set(LAND_TIME, t); }

    private int getAttackAnim() { return dataTracker.get(ATTACK_TIME); }
    private int getStanceAnim() { return dataTracker.get(STANCE_TIME); }
    private int getLeapAnim()   { return dataTracker.get(LEAP_TIME); }
    private int getLandAnim()   { return dataTracker.get(LAND_TIME); }

    /* -------------------------------- Goals -------------------------------- */

    @Override
    protected void initGoals() {
        // Targets
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true, p -> p.isAlive() && !p.isSpectator()));
        this.goalSelector.add(0, new SwimGoal(this));

        // Behavior
        this.goalSelector.add(1, new TongueAttackGoal());               // stab in reach (3D aiming while animating)
        this.goalSelector.add(2, new CloseInGoal(1.15, TONGUE_REACH * 0.88)); // move into tongue range
        this.goalSelector.add(3, new OrbitAndHopGoal());                // lateral hops around the target
        this.goalSelector.add(4, new LeapStartGoal());                  // safe gap-closer (no big drops)
        this.goalSelector.add(7, new WanderAndHopGoal());               // idle only
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 12f));
        this.goalSelector.add(9, new LookAroundGoal(this));
    }

    /** Idle traversal only. */
    class WanderAndHopGoal extends Goal {
        private int cd;
        @Override public boolean canStart() { return getTarget() == null; }
        @Override public void tick() {
            if (--cd <= 0) {
                cd = 20 + random.nextInt(60);
                Vec3d v = NoPenaltyTargeting.find(FalseFrogEntity.this, 8, 3);
                if (v != null) getNavigation().startMovingTo(v.x, v.y, v.z, 1.0);
                if (isOnGround()) hopForward(0.40, 0.42);
            }
        }
    }

    /** Move toward target until within tongue stab range. */
    class CloseInGoal extends Goal {
        private final double speed, stopDist;
        CloseInGoal(double speed, double stopDist){ this.speed = speed; this.stopDist = stopDist; }
        @Override public boolean canStart() {
            LivingEntity t = getTarget(); if (t == null || !t.isAlive()) return false;
            return squaredDistanceTo(t) > stopDist*stopDist && !getNavigation().isFollowingPath();
        }
        @Override public void start() { LivingEntity t = getTarget(); if (t!=null) getNavigation().startMovingTo(t, speed); }
        @Override public void tick() {
            LivingEntity t = getTarget(); if (t==null) return;
            // gentle tracking while closing
            getLookControl().lookAt(t, 30f, 40f);
            if (squaredDistanceTo(t) > stopDist*stopDist) getNavigation().startMovingTo(t, speed);
            else getNavigation().stop();
        }
        @Override public boolean shouldContinue() {
            LivingEntity t = getTarget();
            return t!=null && t.isAlive() && squaredDistanceTo(t) > stopDist*stopDist;
        }
    }

    /** Orbit (strafe) around the target between stabs. */
    class OrbitAndHopGoal extends Goal {
        private int timeLeft, hopCooldown, direction;
        @Override public boolean canStart() {
            if (getAttackAnim()>0 || getStanceAnim()>0 || getLeapAnim()>0 || getLandAnim()>0) return false;
            LivingEntity t = getTarget(); if (t==null || !t.isAlive()) return false;
            double d = distanceTo(t); return d > 3.2 && d < TONGUE_REACH + 1.5;
        }
        @Override public void start() {
            timeLeft = 35 + random.nextInt(20);
            hopCooldown = 0;
            direction = random.nextBoolean()?1:-1;
            getNavigation().stop();
        }
        @Override public void tick() {
            LivingEntity t = getTarget(); if (t==null) return;
            // still track while orbiting
            faceEntity3DInstant(t);
            Vec3d to = new Vec3d(getX() - t.getX(), 0, getZ() - t.getZ());
            if (to.lengthSquared() < 1.0E-4) return;
            Vec3d tangent = new Vec3d(-to.z, 0, to.x).normalize().multiply(direction);
            if (--hopCooldown <= 0 && isOnGround()) {
                hopCooldown = 14 + random.nextInt(8);
                double radialNudge = (distanceTo(t) > TONGUE_REACH * 0.95) ? -0.12 : 0.06;
                Vec3d radial = to.normalize().multiply(radialNudge);
                Vec3d push = tangent.multiply(0.35).add(radial).add(0, 0.42, 0);
                addVelocity(push.x, push.y, push.z); velocityDirty = true;
            }
            if (--timeLeft <= 0) stop();
        }
        @Override public boolean shouldContinue(){ return timeLeft>0 && getTarget()!=null && getTarget().isAlive(); }
    }

    /** Occasional safe leap toward the target (avoid plunging off ledges). */
    class LeapStartGoal extends Goal {
        private int cd;
        @Override public boolean canStart() {
            if (cd>0){ cd--; return false; }
            LivingEntity t = getTarget();
            if (t==null || !t.isAlive() || !isOnGround()) return false;
            double d2 = squaredDistanceTo(t);
            if (d2 < (TONGUE_REACH+2.0)*(TONGUE_REACH+2.0)) return false;
            if (!isLandingNearSafe(t)) return false;
            return d2 < 196; // up to ~14 blocks
        }
        @Override public void start(){ setStanceAnim(STANCE_ANIM_TICKS); cd = 60; }
        @Override public boolean shouldContinue(){ return false; }
    }

    /** Stab when in reach. Keeps aiming at the target every tick in full 3D. */
    class TongueAttackGoal extends Goal {
        private LivingEntity tgt; private int cd;
        @Override public boolean canStart() {
            if (cd>0){ cd--; return false; }
            tgt = getTarget(); if (tgt==null || !tgt.isAlive()) return false;
            return canSee(tgt) && distanceTo(tgt) <= TONGUE_REACH + 0.5;
        }
        @Override public void start() {
            getNavigation().stop();
            // small forward nudge
            Vec3d dir = new Vec3d(tgt.getX()-getX(), tgt.getEyeY()-getEyeY(), tgt.getZ()-getZ()).normalize();
            setVelocity(getVelocity().add(dir.multiply(0.12)));
            setAttackAnim(ATTACK_ANIM_TICKS);
            hitThisSwing.clear();
            cd = 12;
        }
        @Override public void tick() {
            LivingEntity t = getTarget();
            if (t != null && t.isAlive()) {
                // Full 3D snap-aim so tongue can reach above/below targets cleanly
                faceEntity3DInstant(t);
            }
        }
        @Override public boolean shouldContinue(){ return getAttackAnim() > 0; }
    }

    /* ------------------------------- Tick ------------------------------- */

    @Override
    public void tick() {
        super.tick();

        if (!getWorld().isClient) {
            if (stanceTicks>0 && --stanceTicks>=0) dataTracker.set(STANCE_TIME, stanceTicks);
            if (attackTicks>0 && --attackTicks>=0) dataTracker.set(ATTACK_TIME, attackTicks);
            if (leapTicks  >0 && --leapTicks  >=0) dataTracker.set(LEAP_TIME,   leapTicks);
            if (landTicks  >0 && --landTicks  >=0) dataTracker.set(LAND_TIME,   landTicks);

            if (stanceTicks==0 && getStanceAnim()==0 && !isOnGround()) {
                setLeapAnim(LEAP_ANIM_TICKS);
            } else if (stanceTicks==0 && getStanceAnim()==0 && isOnGround()) {
                LivingEntity t = getTarget(); if (t!=null) doLeapTo(t);
            }
        }

        // While the attack plays, sweep the tongue capsule for precise hits
        if (getAttackAnim() > 0) sweepTongueAndDamage();

        if (!getWorld().isClient && getLeapAnim()>0 && (isOnGround() || this.isTouchingWater())) {
            setLeapAnim(0);
            setLandAnim(LAND_ANIM_TICKS);
        }

        // No idle micro-hops while aggro'd
        if (this.getTarget()==null && this.random.nextFloat()<0.01f && this.isOnGround()) {
            hopForward(0.20, 0.38);
        }
    }

    /* -------------------- 3D facing + tongue sweep helpers -------------------- */

    /** Instantly rotates yaw & pitch to face the target from the mouth position. */
    private void faceEntity3DInstant(LivingEntity target) {
        Vec3d mouth = getPos().add(0, MOUTH_Y_OFFSET, 0);
        Vec3d aim   = new Vec3d(target.getX() - mouth.x, target.getEyeY() - mouth.y, target.getZ() - mouth.z);
        if (aim.lengthSquared() < 1.0E-6) return;

        float yaw = (float)(MathHelper.atan2(aim.z, aim.x) * (180F/Math.PI)) - 90F;
        float pitch = (float)(-MathHelper.atan2(aim.y, MathHelper.sqrt((float)(aim.x*aim.x + aim.z*aim.z))) * (180F/Math.PI));

        // clamp pitch to sane range
        pitch = MathHelper.clamp(pitch, -89.0f, 89.0f);

        // apply
        this.setYaw(yaw);
        this.bodyYaw = yaw;
        this.headYaw = yaw;   // used by EntityModelData.netHeadYaw()
        this.setPitch(pitch); // used by EntityModelData.headPitch()
    }

    /** Tongue sweep capsule follows the actual aim direction each tick. */
    private void sweepTongueAndDamage() {
        // progress 0..1 through the attack animation
        double t = 1.0 - (getAttackAnim() / (double)ATTACK_ANIM_TICKS);

        // Extension curve: ease-out extend, hold, ease-in retract
        double ext;
        if (t < 0.35) ext = smooth(t / 0.35);
        else if (t < 0.65) ext = 1.0;
        else ext = 1.0 - smooth((t - 0.65) / 0.35);

        Vec3d mouth = getPos().add(0, MOUTH_Y_OFFSET, 0);

        Vec3d dir;
        LivingEntity tgt = getTarget();
        if (tgt != null && tgt.isAlive()) {
            dir = new Vec3d(tgt.getX() - mouth.x, tgt.getEyeY() - mouth.y, tgt.getZ() - mouth.z).normalize();
        } else {
            dir = this.getRotationVector();
        }

        double length = TONGUE_REACH * ext;
        if (length <= 0.05) return;

        Vec3d tip = mouth.add(dir.multiply(length));
        damageEntitiesAlongCapsule(mouth, tip, TONGUE_RADIUS);
    }

    private static double smooth(double x) {
        x = MathHelper.clamp(x, 0.0, 1.0);
        return x * x * (3.0 - 2.0 * x);
    }

    private void damageEntitiesAlongCapsule(Vec3d a, Vec3d b, double r) {
        Box sweep = new Box(a, b).expand(r + 0.2);
        Predicate<Entity> pred = e -> e instanceof LivingEntity le
                && le != this && le.isAlive()
                && !(le instanceof PlayerEntity p && p.isCreative());

        for (Entity e : getWorld().getOtherEntities(this, sweep, pred)) {
            LivingEntity le = (LivingEntity) e;
            Vec3d p = le.getPos().add(0, le.getStandingEyeHeight() * 0.5, 0);
            double entityR = Math.max(le.getWidth(), le.getHeight()) * 0.25;
            if (distPointToSegment(p, a, b) <= (r + entityR)) {
                if (hitThisSwing.add(le.getUuid())) {
                    le.damage(getWorld().getDamageSources().mobAttack(this),
                            (float)getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE));
                    getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 0.65f, 0.65f);
                }
            }
        }
    }

    private static double distPointToSegment(Vec3d p, Vec3d a, Vec3d b) {
        Vec3d ab = b.subtract(a);
        double ab2 = ab.lengthSquared();
        if (ab2 < 1.0E-6) return p.distanceTo(a);
        double t = p.subtract(a).dotProduct(ab) / ab2;
        t = MathHelper.clamp(t, 0.0, 1.0);
        Vec3d proj = a.add(ab.multiply(t));
        return p.distanceTo(proj);
    }

    /* --------------------------- Movement helpers --------------------------- */

    private void doLeapTo(LivingEntity target) {
        Vec3d delta = new Vec3d(target.getX()-getX(), target.getY()-getY(), target.getZ()-getZ());
        Vec3d horiz = new Vec3d(delta.x, 0, delta.z);
        double horizLen = MathHelper.clamp(horiz.length(), 3.0, 14.0);
        Vec3d dir = horizLen < 1.0E-3 ? new Vec3d(0,0,0) : horiz.normalize();

        double forward = Math.min(0.8, 0.35 + horizLen * 0.03);
        double upward  = 0.60 + horizLen * 0.03;

        setVelocity(getVelocity().multiply(0.2).add(dir.multiply(forward)).add(0, upward, 0));
        setLeapAnim(LEAP_ANIM_TICKS);
        this.velocityDirty = true;
    }

    private boolean isLandingNearSafe(LivingEntity target) {
        BlockPos predictedXZ = BlockPos.ofFloored(target.getX(), target.getY(), target.getZ());
        BlockPos top = getWorld().getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, predictedXZ);
        int drop = (int)Math.floor(this.getY() - top.getY());
        if (drop > 3) return false;
        return getWorld().getBlockState(top.down()).isOpaqueFullCube(getWorld(), top.down());
    }

    private void hopForward(double forward, double upward) {
        Vec3d dir = this.getRotationVector().multiply(forward);
        this.addVelocity(dir.x, upward, dir.z);
        this.velocityDirty = true;
    }

    /* ------------------------------ GeckoLib ------------------------------ */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "falsefrog.controller", 0, state -> {
            if (getAttackAnim() > 0) return state.setAndContinue(ATTACK);
            if (getLandAnim()   > 0) return state.setAndContinue(LAND);
            if (getLeapAnim()   > 0) return state.setAndContinue(LEAP);
            if (getStanceAnim() > 0) return state.setAndContinue(STANCE);

            boolean walking = (this.getNavigation().isFollowingPath() && this.isOnGround())
                    || this.getVelocity().horizontalLengthSquared() > 0.006;
            return walking ? state.setAndContinue(WALK) : state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override
    protected void fall(double heightDiff, boolean onGround, BlockState state, BlockPos pos) {
        super.fall(heightDiff, onGround, state, pos);
        if (!getWorld().isClient && onGround && heightDiff > 0.6 && getLandAnim() == 0) {
            setLandAnim(LAND_ANIM_TICKS);
            getWorld().playSound(null, pos, SoundEvents.BLOCK_SLIME_BLOCK_FALL, SoundCategory.HOSTILE, 0.6f, 0.5f);
        }
    }

    /* --------------------------- Move control --------------------------- */
    static class FrogMoveControl extends MoveControl {
        private final FalseFrogEntity frog;
        FrogMoveControl(FalseFrogEntity frog){ super(frog); this.frog=frog; }
        @Override public void tick() {
            if (frog.isOnGround() && frog.horizontalCollision) {
                frog.setJumping(true);
                frog.setVelocity(frog.getVelocity().add(0, 0.02, 0));
            }
            super.tick();
        }
    }
}
