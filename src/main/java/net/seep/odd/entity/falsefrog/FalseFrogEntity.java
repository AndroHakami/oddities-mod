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
import net.minecraft.particle.ParticleTypes;
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
 * - Walks toward target, stabs with 3D tracking tongue (capsule sweep).
 * - Less bouncy while aggro'd; cautious about ledges/cliffs.
 * - Two leaps: normal gap-closer, and HIGH LEAP (reduced to ~3x vertical) when target is above.
 *   Both always play "stance" first, then "leap".
 * - No fall damage. On landing from a leap, makes a smoke shockwave that knocks back nearby
 *   targets and damages the one directly landed on.
 */
public class FalseFrogEntity extends HostileEntity implements GeoEntity {
    public static final float WIDTH  = 2.4f;
    public static final float HEIGHT = 1.7f;

    // Tongue sweep tunables
    public static final double TONGUE_REACH    = 6.8;   // blocks
    private static final double TONGUE_RADIUS  = 0.35;  // capsule radius
    private static final double MOUTH_Y_OFFSET = 1.25;  // mouth height offset

    // Animation lengths (ticks @20TPS)
    private static final int ATTACK_ANIM_TICKS = 24;    // ~1.2s
    private static final int STANCE_ANIM_TICKS = 34;    // ~1.72s
    private static final int LEAP_ANIM_TICKS   = 53;    // ~2.64s
    private static final int LAND_ANIM_TICKS   = 12;    // ~0.62s

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

    // Leap state
    private boolean pendingHighLeap = false; // stance scheduled a high leap
    private boolean midAnyLeap = false;      // true while airborne due to a leap
    private boolean midHighLeap = false;     // true if current leap is the high variant

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

    /* ------------------------------ Attributes ------------------------------ */

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.23)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.1);
    }

    /* ------------------------------ Data tracker ------------------------------ */

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

        // Behavior (grounded/intentional while aggro'd)
        this.goalSelector.add(1, new TongueAttackGoal());
        this.goalSelector.add(2, new CloseInGoal(1.15, TONGUE_REACH * 0.90)); // walk into tongue range
        this.goalSelector.add(3, new HighLeapGoal());                          // stance -> high leap if target is above
        this.goalSelector.add(4, new OrbitAndHopGoal());                       // toned-down side hops
        this.goalSelector.add(5, new LeapStartGoal());                         // stance -> normal leap (less frequent)
        this.goalSelector.add(7, new WanderAndHopGoal());                      // idle only
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 12f));
        this.goalSelector.add(9, new LookAroundGoal(this));
    }

    /** Idle traversal only; DISABLED while aggro'd. */
    class WanderAndHopGoal extends Goal {
        private int cd;
        @Override public boolean canStart() { return getTarget() == null; }
        @Override public void tick() {
            if (--cd <= 0) {
                cd = 30 + random.nextInt(60);
                Vec3d v = NoPenaltyTargeting.find(FalseFrogEntity.this, 8, 3);
                if (v != null) getNavigation().startMovingTo(v.x, v.y, v.z, 1.0);
                if (isOnGround()) hopForwardSafe(0.35, 0.40);
            }
        }
    }

    /** Walk toward the target until within tongue stab range. */
    class CloseInGoal extends Goal {
        private final double speed, stopDist;
        CloseInGoal(double speed, double stopDist){ this.speed=speed; this.stopDist=stopDist; }

        @Override public boolean canStart() {
            LivingEntity t = getTarget(); if (t == null || !t.isAlive()) return false;
            return squaredDistanceTo(t) > stopDist*stopDist && !getNavigation().isFollowingPath();
        }

        @Override public void start() { LivingEntity t = getTarget(); if (t!=null) getNavigation().startMovingTo(t, speed); }

        @Override public void tick() {
            LivingEntity t = getTarget(); if (t==null) return;
            faceEntity3DInstant(t);

            // If the next few steps look like a cliff, stop and wait for a safer option
            if (isForwardDropUnsafe(1.75)) {
                getNavigation().stop();
                return;
            }

            if (squaredDistanceTo(t) > stopDist*stopDist) getNavigation().startMovingTo(t, speed);
            else getNavigation().stop();
        }

        @Override public boolean shouldContinue() {
            LivingEntity t = getTarget();
            return t!=null && t.isAlive() && squaredDistanceTo(t) > stopDist*stopDist && !isForwardDropUnsafe(1.75);
        }
    }

    /** Toned-down orbit/side-hop while close; avoids ledges. */
    class OrbitAndHopGoal extends Goal {
        private int timeLeft, hopCooldown, direction;
        @Override public boolean canStart() {
            if (getAttackAnim()>0 || getStanceAnim()>0 || getLeapAnim()>0 || getLandAnim()>0) return false;
            LivingEntity t = getTarget(); if (t==null || !t.isAlive()) return false;
            double d = distanceTo(t);
            return d > 3.2 && d < TONGUE_REACH + 1.5;
        }
        @Override public void start() {
            timeLeft = 28 + random.nextInt(16);
            hopCooldown = 20 + random.nextInt(15); // less frequent
            direction = random.nextBoolean()?1:-1;
            getNavigation().stop();
        }
        @Override public void tick() {
            LivingEntity t = getTarget(); if (t==null) return;
            faceEntity3DInstant(t);

            Vec3d to = new Vec3d(getX() - t.getX(), 0, getZ() - t.getZ());
            if (to.lengthSquared() < 1.0E-4) return;

            if (--hopCooldown <= 0 && isOnGround()) {
                hopCooldown = 22 + random.nextInt(16);

                Vec3d tangent = new Vec3d(-to.z, 0, to.x).normalize().multiply(direction);
                double radialNudge = (distanceTo(t) > TONGUE_REACH * 0.95) ? -0.10 : 0.05;
                Vec3d radial = to.normalize().multiply(radialNudge);
                Vec3d push = tangent.multiply(0.28).add(radial); // smaller, flatter hop
                if (isProjectedStepUnsafe(push.multiply(2.0))) {
                    // try opposite direction once
                    push = push.multiply(-1);
                    if (isProjectedStepUnsafe(push.multiply(2.0))) {
                        // nowhere safe to hop; skip
                        if (--timeLeft <= 0) stop();
                        return;
                    }
                }
                addVelocity(push.x, 0.34, push.z); velocityDirty = true;
            }

            if (--timeLeft <= 0) stop();
        }
        @Override public boolean shouldContinue(){ return timeLeft>0 && getTarget()!=null && getTarget().isAlive(); }
    }

    /** Normal gap-closing leap (stance -> leap). Made less frequent and avoids bad landings. */
    class LeapStartGoal extends Goal {
        private int cd;
        @Override public boolean canStart() {
            if (cd>0){ cd--; return false; }
            LivingEntity t = getTarget();
            if (t==null || !t.isAlive() || !isOnGround()) return false;

            double d2 = squaredDistanceTo(t);
            if (d2 < (TONGUE_REACH+2.0)*(TONGUE_REACH+2.0)) return false; // close; prefer walk
            if (!isLandingNearSafe(t)) return false;

            return d2 < 196; // up to ~14 blocks
        }
        @Override public void start(){
            pendingHighLeap = false;
            setStanceAnim(STANCE_ANIM_TICKS);  // play stance first
            cd = 100;
        }
        @Override public boolean shouldContinue(){ return false; }
    }

    /** HIGH LEAP: stance -> powerful vertical jump if target is notably above (×3 vertical). */
    class HighLeapGoal extends Goal {
        private int cd;
        @Override public boolean canStart() {
            if (cd > 0) { cd--; return false; }
            LivingEntity t = getTarget();
            if (t==null || !t.isAlive() || !isOnGround()) return false;

            double dy = (t.getEyeY() - getY());
            if (dy < 2.5) return false; // only if target is above us

            double d2 = squaredDistanceTo(t);
            if (d2 > 20*20) return false;     // not across the world; just reach upward
            if (!isLandingNearSafe(t)) return false;

            return true;
        }
        @Override public void start() {
            pendingHighLeap = true;            // stance -> high leap
            setStanceAnim(STANCE_ANIM_TICKS);  // play stance first
            cd = 120;
        }
        @Override public boolean shouldContinue() { return false; }
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
                faceEntity3DInstant(t);
            }
        }
        @Override public boolean shouldContinue(){ return getAttackAnim() > 0; }
    }

    /* ------------------------------- Move control ------------------------------- */

    static class FrogMoveControl extends MoveControl {
        private final FalseFrogEntity frog;
        FrogMoveControl(FalseFrogEntity frog){ super(frog); this.frog=frog; }
        @Override public void tick() {
            // help step over small clutter, but don't force large hops
            if (frog.isOnGround() && frog.horizontalCollision) {
                frog.setJumping(true);
                frog.setVelocity(frog.getVelocity().add(0, 0.02, 0));
            }
            super.tick();
        }
    }

    /* -------------------------------- Tick -------------------------------- */

    @Override
    public void tick() {
        super.tick();

        // Keep head/body aimed at current target (prevents "side-looking")
        LivingEntity autoAim = this.getTarget();
        if (autoAim != null && autoAim.isAlive()) {
            faceEntity3DInstant(autoAim);
        }

        if (!getWorld().isClient) {
            if (stanceTicks>0 && --stanceTicks>=0) dataTracker.set(STANCE_TIME, stanceTicks);
            if (attackTicks>0 && --attackTicks>=0) dataTracker.set(ATTACK_TIME, attackTicks);
            if (leapTicks  >0 && --leapTicks  >=0) dataTracker.set(LEAP_TIME,   leapTicks);
            if (landTicks  >0 && --landTicks  >=0) dataTracker.set(LAND_TIME,   landTicks);

            // After stance ends while still on ground, perform scheduled leap
            if (stanceTicks == 0 && getStanceAnim() == 0 && isOnGround()) {
                LivingEntity t = getTarget();
                if (t != null) {
                    if (pendingHighLeap) doHighLeapTo(t);
                    else doLeapTo(t);
                }
                pendingHighLeap = false;
            }

            // Left ground during stance: mark as leaping
            if (stanceTicks == 0 && getStanceAnim() == 0 && !isOnGround() && getLeapAnim() == 0 && (pendingHighLeap || midAnyLeap)) {
                setLeapAnim(LEAP_ANIM_TICKS);
            }
        }

        // Tongue damage sweep while attacking
        if (getAttackAnim() > 0) sweepTongueAndDamage();

        // Landing from a leap -> land anim + shockwave
        if (!getWorld().isClient && getLeapAnim()>0 && (isOnGround() || this.isTouchingWater())) {
            setLeapAnim(0);
            setLandAnim(LAND_ANIM_TICKS);
            if (midAnyLeap) {
                doLandingShockwave(midHighLeap);
            }
            midAnyLeap = false;
            midHighLeap = false;
        }

        // No idle micro-hops while aggro'd
        if (this.getTarget()==null && this.random.nextFloat()<0.01f && this.isOnGround()) {
            hopForwardSafe(0.20, 0.36);
        }
    }

    /* -------------------- 3D facing + tongue sweep helpers -------------------- */

    private void faceEntity3DInstant(LivingEntity target) {
        Vec3d mouth = getPos().add(0, MOUTH_Y_OFFSET, 0);
        Vec3d aim   = new Vec3d(target.getX() - mouth.x, target.getEyeY() - mouth.y, target.getZ() - mouth.z);
        if (aim.lengthSquared() < 1.0E-6) return;

        float yaw = (float)(MathHelper.atan2(aim.z, aim.x) * (180F/Math.PI)) - 90F;
        float pitch = (float)(-MathHelper.atan2(aim.y, MathHelper.sqrt((float)(aim.x*aim.x + aim.z*aim.z))) * (180F/Math.PI));
        pitch = MathHelper.clamp(pitch, -89.0f, 89.0f);

        this.setYaw(yaw);
        this.bodyYaw = yaw;
        this.headYaw = yaw;
        this.setPitch(pitch);
    }

    private void sweepTongueAndDamage() {
        double t = 1.0 - (getAttackAnim() / (double)ATTACK_ANIM_TICKS);
        double ext = (t < 0.35) ? smooth(t / 0.35) : (t < 0.65 ? 1.0 : 1.0 - smooth((t - 0.65) / 0.35));

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

    /* --------------------------- Leap & safety helpers --------------------------- */

    private void doLeapTo(LivingEntity target) {
        Vec3d delta = new Vec3d(target.getX()-getX(), target.getY()-getY(), target.getZ()-getZ());
        Vec3d horiz = new Vec3d(delta.x, 0, delta.z);
        double horizLen = MathHelper.clamp(horiz.length(), 3.0, 14.0);
        Vec3d dir = horizLen < 1.0E-3 ? new Vec3d(0,0,0) : horiz.normalize();

        double forward = Math.min(0.8, 0.35 + horizLen * 0.03);
        double upward  = 0.60 + horizLen * 0.03;

        setVelocity(getVelocity().multiply(0.2).add(dir.multiply(forward)).add(0, upward, 0));
        setLeapAnim(LEAP_ANIM_TICKS);
        midAnyLeap = true;
        midHighLeap = false;
        this.velocityDirty = true;
    }

    private void doHighLeapTo(LivingEntity target) {
        // High vertical leap (×3 vertical); stance already played.
        Vec3d mouthToTarget = new Vec3d(target.getX() - getX(), target.getEyeY() - getY(), target.getZ() - getZ());
        Vec3d horiz = new Vec3d(mouthToTarget.x, 0, mouthToTarget.z);
        Vec3d dir = horiz.lengthSquared() < 1.0E-6 ? new Vec3d(0,0,0) : horiz.normalize();

        double forward = 0.25 + Math.min(0.3, horiz.length() * 0.02); // mostly up, slight forward
        double upward  = (0.60 + Math.min(0.6, horiz.length() * 0.04)) * 3.0; // ~3x vertical (reduced)

        // Smoke burst at takeoff
        for (int i=0;i<14;i++) {
            double sx = getX() + (random.nextDouble()-0.5) * 1.2;
            double sz = getZ() + (random.nextDouble()-0.5) * 1.2;
            getWorld().addParticle(ParticleTypes.CLOUD, sx, getY()+0.2, sz, 0, 0.02, 0);
        }
        getWorld().playSound(null, getBlockPos(), SoundEvents.BLOCK_SAND_BREAK, SoundCategory.HOSTILE, 0.7f, 0.6f);

        setVelocity(getVelocity().multiply(0.2).add(dir.multiply(forward)).add(0, upward, 0));
        setLeapAnim(LEAP_ANIM_TICKS);
        midAnyLeap = true;
        midHighLeap = true;
        this.velocityDirty = true;
    }

    /** Called when a leap ends and we touch ground; spawns a smoke shockwave + knockback. */
    private void doLandingShockwave(boolean high) {
        double radius = high ? 3.3 : 2.4;
        double kb     = high ? 1.2 : 0.8;
        double yBoost = high ? 0.45 : 0.32;

        // smoke ring
        int pCount = high ? 40 : 28;
        for (int i = 0; i < pCount; i++) {
            double ang = (Math.PI * 2 * i) / pCount;
            double rx = Math.cos(ang) * (radius - 0.4 + random.nextDouble()*0.6);
            double rz = Math.sin(ang) * (radius - 0.4 + random.nextDouble()*0.6);
            getWorld().addParticle(ParticleTypes.CLOUD, getX()+rx, getY()+0.1, getZ()+rz, 0, 0.02, 0);
        }
        getWorld().playSound(null, getBlockPos(), SoundEvents.BLOCK_SAND_FALL, SoundCategory.HOSTILE, 0.9f, high ? 0.7f : 0.9f);

        // knockback + "land-on" damage if very close
        List<Entity> list = getWorld().getOtherEntities(this,
                new Box(getX()-radius, getY()-0.5, getZ()-radius, getX()+radius, getY()+1.5, getZ()+radius),
                e -> e instanceof LivingEntity le && le.isAlive() && le != this && !(le instanceof PlayerEntity p && p.isCreative())
        );

        for (Entity e : list) {
            LivingEntity le = (LivingEntity) e;
            Vec3d away = new Vec3d(le.getX()-getX(), 0, le.getZ()-getZ());
            double d = Math.max(0.001, away.length());
            Vec3d push = away.normalize().multiply(kb * (1.0 - Math.min(1.0, d / radius)));
            le.addVelocity(push.x, yBoost, push.z);
            le.velocityDirty = true;

            // If we basically landed on them (very close), apply a bit of damage
            if (d < 1.2) {
                le.damage(getWorld().getDamageSources().mobAttack(this),
                        (float)(getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) * (high ? 1.25f : 1.0f)));
            }
        }
    }

    /** Avoid leaping/stepping into big drops ahead (simple cliff safety). */
    private boolean isLandingNearSafe(LivingEntity target) {
        BlockPos predictedXZ = BlockPos.ofFloored(target.getX(), target.getY(), target.getZ());
        BlockPos top = getWorld().getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, predictedXZ);
        int drop = (int)Math.floor(this.getY() - top.getY());
        if (drop > 3) return false;
        return getWorld().getBlockState(top.down()).isOpaqueFullCube(getWorld(), top.down());
    }

    private boolean isForwardDropUnsafe(double dist) {
        // Check center + left/right of body width at a point "dist" ahead.
        float yaw = this.getYaw() * ((float)Math.PI/180F);
        Vec3d fwd = new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
        Vec3d left = new Vec3d(-fwd.z, 0, fwd.x);

        double halfW = WIDTH * 0.45;
        return isPointDropUnsafe(getPos().add(fwd.multiply(dist))) ||
                isPointDropUnsafe(getPos().add(fwd.multiply(dist)).add(left.multiply(halfW))) ||
                isPointDropUnsafe(getPos().add(fwd.multiply(dist)).add(left.multiply(-halfW)));
    }

    private boolean isProjectedStepUnsafe(Vec3d horizontalPush) {
        if (horizontalPush.lengthSquared() < 1.0E-4) return false;
        Vec3d aheadCenter = getPos().add(horizontalPush);
        Vec3d norm = horizontalPush.normalize();
        Vec3d perp = new Vec3d(-norm.z, 0, norm.x);
        double halfW = WIDTH * 0.45;
        return isPointDropUnsafe(aheadCenter) ||
                isPointDropUnsafe(aheadCenter.add(perp.multiply(halfW))) ||
                isPointDropUnsafe(aheadCenter.add(perp.multiply(-halfW)));
    }

    private boolean isPointDropUnsafe(Vec3d pos) {
        BlockPos xz = BlockPos.ofFloored(pos.x, this.getY(), pos.z);
        BlockPos top = getWorld().getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, xz);
        return (this.getY() - top.getY()) > 3; // treat >3 block drop as unsafe
    }

    private void hopForwardSafe(double forward, double upward) {
        if (isForwardDropUnsafe(forward * 2.0)) return; // skip hop if cliff ahead
        Vec3d dir = this.getRotationVector().multiply(forward);
        this.addVelocity(dir.x, upward, dir.z);
        this.velocityDirty = true;
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
            // Priority: attack > land > leap > stance > walk > idle
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

    /* ------------------------------ Falling ------------------------------ */

    // No fall damage ever.
    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, net.minecraft.entity.damage.DamageSource damageSource) {
        return false;
    }

    /* ---------- Landing thud -> play LAND if we smack ground from a generic fall ---------- */
    @Override
    protected void fall(double heightDiff, boolean onGround, BlockState state, BlockPos pos) {
        super.fall(heightDiff, onGround, state, pos);
        if (!getWorld().isClient && onGround && heightDiff > 0.6 && getLandAnim() == 0) {
            setLandAnim(LAND_ANIM_TICKS);
            getWorld().playSound(null, pos, SoundEvents.BLOCK_SLIME_BLOCK_FALL, SoundCategory.HOSTILE, 0.6f, 0.5f);
        }
    }
}
