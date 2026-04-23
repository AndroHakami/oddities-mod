package net.seep.odd.entity.ufo;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.Box;
import net.seep.odd.sound.ModSounds;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.List;

public final class UfoSaucerEntity extends PathAwareEntity implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation ANIM_IDLE   = RawAnimation.begin().thenLoop("ufo_idle");
    private static final RawAnimation ANIM_FLY    = RawAnimation.begin().thenPlay("ufo_fly");
    private static final RawAnimation ANIM_ATTACK = RawAnimation.begin().thenPlay("ufo_attack");

    // General combat movement: fast, airborne, always staying above the fight.
    private static final double CRUISE_SPEED = 1.02;
    private static final double DASH_SPEED   = 2.10;
    private static final double ASCENT_SPEED = 1.20;
    private static final int    REPATH_TICKS = 1;

    private static final double ORBIT_R_NEAR = 7.0;
    private static final double ORBIT_R_FAR  = 11.5;
    private static final double ORBIT_W      = 0.36;

    private static final int    AGGRO_RADIUS = 96;

    // Keep it airborne during normal combat instead of dipping too low.
    private static final double MIN_OVERHEAD = 7.5;
    private static final double MIN_ABOVE_GROUND = 8.0;
    private static final double COMBAT_ALT_NEAR = 10.0;
    private static final double COMBAT_ALT_FAR  = 14.5;

    // Abduction mode: goes higher but becomes much more direct and much faster.
    private static final double ABDUCT_OVERHEAD = 15.0;
    private static final double ABDUCT_APPROACH_SPEED = 2.50;
    private static final double ABDUCT_LOCK_SPEED     = 2.25;
    private static final double ABDUCT_ASCENT_SPEED   = 1.80;
    private static final double ABDUCT_TRIGGER_HORIZ  = 7.5;

    private static final double SUCTION_HOVER_R = 1.35;
    private static final double SUCTION_ORBIT_W = 0.30;

    private static final float  LASER_DAMAGE = 5.0f;
    private static final int    LASER_RANGE  = 28;
    private static final double MAX_ATTACK_HEIGHT_ABOVE_TARGET = 22.0;
    private static final int    LASER_COOLDOWN_TICKS = 95;
    private static final float  LASER_RANDOM_CHANCE  = 0.18f;
    private static final int    ATTACK_ANIM_TICKS    = 30;
    private static final int    LASER_VISUAL_TICKS_TOTAL = 8;
    private static final int    LASER_FIRE_DELAY_TICKS   = 8; // 0.4s

    private enum AbductPhase { NONE, APPROACH_OVERHEAD, SUCTION_LOCK, ASCENT, DIVE }
    private static final int ABDUCT_MAX_DURATION    = 600;
    private static final int SUCTION_LOCK_MIN_TICKS = 12;

    // Much taller beam so it can grab from high up.
    private static final double BEAM_HEIGHT        = 48.0;
    private static final double BEAM_RADIUS_TOP    = 1.6;
    private static final double BEAM_RADIUS_BOTTOM = 7.0;

    // Stronger pull so the long beam actually feels threatening.
    private static final double PULL_GAIN_STRONG   = 1.25;
    private static final double PULL_GAIN_MILD     = 0.78;
    private static final double PULL_DAMPING       = 0.04;
    private static final double PULL_MAX_STRONG    = 1.35;
    private static final double PULL_MAX_MILD      = 0.90;
    private static final double PULL_MIN_UP_STRONG = 0.80;
    private static final double PULL_MIN_UP_MILD   = 0.48;

    private static final int LEVITATION_TICKS_MILD   = 10;
    private static final int LEVITATION_TICKS_STRONG = 14;
    private static final int LEVITATION_AMP_MILD     = 0;
    private static final int LEVITATION_AMP_STRONG   = 1;

    private static final double ANCHOR_OFFSET_DOWN = 2.2;
    private static final int ASCENT_ALT_ABOVE_GROUND = 300;

    private static final int DIVE_TICKS = 50;
    private static final double DIVE_SPEED = 1.10;

    private static final double HOVER_BOB_AMPL = 0.06;
    private static final double HOVER_BOB_FREQ = 0.08;

    private static final int    ALT_HOLD_TICKS_CHASE   = 5;
    private static final int    ALT_HOLD_TICKS_SUCTION = 4;
    private static final double ALT_CHANGE_EPS         = 0.55;

    private static final TrackedData<Integer> ATTACK_TIME =
            DataTracker.registerData(UfoSaucerEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final TrackedData<Boolean> TRACTOR_ACTIVE =
            DataTracker.registerData(UfoSaucerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final TrackedData<Integer> LASER_VISUAL_TICKS =
            DataTracker.registerData(UfoSaucerEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final TrackedData<Float> LASER_TARGET_X =
            DataTracker.registerData(UfoSaucerEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> LASER_TARGET_Y =
            DataTracker.registerData(UfoSaucerEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> LASER_TARGET_Z =
            DataTracker.registerData(UfoSaucerEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private int attackTicks;
    private int laserCooldown;
    private boolean boosting;

    private AbductPhase abductPhase = AbductPhase.NONE;
    private int abductTicks;
    private int suctionTicks;
    private double ascentTargetY;
    @Nullable private PlayerEntity abductTarget;

    private int diveTicks;

    private double requestedY = Double.NaN;
    private int altHoldChase = 0;
    private int altHoldSuction = 0;

    @Nullable private PlayerEntity pendingLaserTarget;
    private int pendingLaserTicks = 0;

    public UfoSaucerEntity(EntityType<? extends UfoSaucerEntity> type, World world) {
        super(type, world);
        this.moveControl = new FlightMoveControl(this, 45, true);
        this.setNoGravity(true);
        this.ignoreCameraFrustum = true;
        this.setPersistent();
        this.experiencePoints = 10;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 30.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.55D)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 1.50D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, AGGRO_RADIUS)
                .add(EntityAttributes.GENERIC_ARMOR, 4.0D);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(ATTACK_TIME, 0);
        this.dataTracker.startTracking(TRACTOR_ACTIVE, false);
        this.dataTracker.startTracking(LASER_VISUAL_TICKS, 0);
        this.dataTracker.startTracking(LASER_TARGET_X, 0.0f);
        this.dataTracker.startTracking(LASER_TARGET_Y, 0.0f);
        this.dataTracker.startTracking(LASER_TARGET_Z, 0.0f);
    }

    private void setAttackAnim(int ticks) {
        this.attackTicks = ticks;
        this.dataTracker.set(ATTACK_TIME, ticks);
    }

    private int getAttackAnim() {
        return this.dataTracker.get(ATTACK_TIME);
    }

    public boolean isTractorBeamActive() {
        return this.dataTracker.get(TRACTOR_ACTIVE);
    }

    private void setTractorBeamActive(boolean active) {
        if (!this.getWorld().isClient) {
            this.dataTracker.set(TRACTOR_ACTIVE, active);
        }
    }

    public boolean hasLaserVisual() {
        return this.dataTracker.get(LASER_VISUAL_TICKS) > 0;
    }

    public float getLaserVisualAge01() {
        return MathHelper.clamp(this.dataTracker.get(LASER_VISUAL_TICKS) / (float) LASER_VISUAL_TICKS_TOTAL, 0.0f, 1.0f);
    }

    public Vec3d getLaserVisualTarget() {
        return new Vec3d(
                this.dataTracker.get(LASER_TARGET_X),
                this.dataTracker.get(LASER_TARGET_Y),
                this.dataTracker.get(LASER_TARGET_Z)
        );
    }

    private void triggerLaserVisual(Vec3d target) {
        if (this.getWorld().isClient) return;
        this.dataTracker.set(LASER_TARGET_X, (float) target.x);
        this.dataTracker.set(LASER_TARGET_Y, (float) target.y);
        this.dataTracker.set(LASER_TARGET_Z, (float) target.z);
        this.dataTracker.set(LASER_VISUAL_TICKS, LASER_VISUAL_TICKS_TOTAL);
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        BirdNavigation nav = new BirdNavigation(this, world);
        nav.setCanSwim(false);
        nav.setCanPathThroughDoors(false);
        nav.setCanEnterOpenDoors(true);
        return nav;
    }

    @Override
    public boolean hasNoGravity() {
        return true;
    }

    private static final class UfoSounds {
        static final SoundEvent HURT  = ModSounds.SAUCER_HURT;
        static final SoundEvent DEATH = ModSounds.SAUCER_DEATH;
        static final SoundEvent LASER = ModSounds.SAUCER_ATTACK;
        static final SoundEvent DIVE  = ModSounds.SAUCER_BOOST;
    }

    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(net.minecraft.entity.damage.DamageSource src) { return UfoSounds.HURT; }
    @Override protected SoundEvent getDeathSound() { return UfoSounds.DEATH; }
    @Override protected float getSoundVolume() { return 0.6F; }
    @Override public int getMinAmbientSoundDelay() { return 200; }

    @Override
    protected void initGoals() {
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true,
                p -> p.isAlive() && !p.isSpectator()));

        this.goalSelector.add(1, new UfoAbductGoal());
        this.goalSelector.add(2, new UfoLaserAttackGoal());
        this.goalSelector.add(3, new UfoChaseGoal());
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 28f));
        this.goalSelector.add(9, new LookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.getWorld().isClient) {
            boolean beamNow =
                    this.abductPhase == AbductPhase.APPROACH_OVERHEAD
                            || this.abductPhase == AbductPhase.SUCTION_LOCK
                            || this.abductPhase == AbductPhase.ASCENT;
            setTractorBeamActive(beamNow);

            int laserVisualTicks = this.dataTracker.get(LASER_VISUAL_TICKS);
            if (laserVisualTicks > 0) {
                this.dataTracker.set(LASER_VISUAL_TICKS, laserVisualTicks - 1);
            }

            if (pendingLaserTicks > 0) {
                pendingLaserTicks--;
                if (pendingLaserTicks <= 0) {
                    if (pendingLaserTarget != null && pendingLaserTarget.isAlive() && !pendingLaserTarget.isSpectator()) {
                        fireLaserNow(pendingLaserTarget);
                    }
                    pendingLaserTarget = null;
                    pendingLaserTicks = 0;
                }
            }

            if (this.getTarget() == null || !this.getTarget().isAlive() || this.age % 10 == 0) {
                PlayerEntity nearest = this.getWorld().getClosestPlayer(this, AGGRO_RADIUS);
                if (nearest != null && !nearest.isSpectator()) {
                    this.setTarget(nearest);
                }
            }
        }

        if (!this.isTouchingWater() && !this.isSubmergedIn(FluidTags.WATER)) {
            double bob = Math.sin(age * HOVER_BOB_FREQ) * HOVER_BOB_AMPL * 0.016;
            this.setVelocity(this.getVelocity().x * 0.97, this.getVelocity().y * 0.97 + bob, this.getVelocity().z * 0.97);
        }

        applyAscendAssist();

        if (!this.getWorld().isClient) {
            if (attackTicks > 0) {
                attackTicks--;
                this.dataTracker.set(ATTACK_TIME, attackTicks);
            }
        }

        if (laserCooldown > 0) laserCooldown--;

        Vec3d v = this.getVelocity();
        if (v.lengthSquared() > 1.0E-3) {
            float targetYaw = (float)(MathHelper.atan2(v.z, v.x) * (180F / Math.PI)) - 90F;
            float smooth = rotateTowards(this.getYaw(), targetYaw, 12f);
            this.setYaw(smooth);
            this.setBodyYaw(smooth);
            this.setHeadYaw(smooth);
        }

        if (altHoldChase > 0) altHoldChase--;
        if (altHoldSuction > 0) altHoldSuction--;
    }

    private static float rotateTowards(float current, float target, float maxStep) {
        float delta = MathHelper.wrapDegrees(target - current);
        if (delta > maxStep) delta = maxStep;
        if (delta < -maxStep) delta = -maxStep;
        return current + delta;
    }

    private int groundYAt(BlockPos pos) {
        return getWorld().getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, pos).getY();
    }

    private double desiredCombatY(PlayerEntity tgt, boolean far) {
        int groundY = groundYAt(tgt.getBlockPos());
        double desiredAboveTarget = tgt.getY() + (far ? COMBAT_ALT_FAR : COMBAT_ALT_NEAR);
        double desiredAboveGround = groundY + (far ? COMBAT_ALT_FAR : COMBAT_ALT_NEAR);
        return Math.max(desiredAboveTarget, desiredAboveGround);
    }

    private double desiredAbductY(PlayerEntity tgt) {
        int groundY = groundYAt(tgt.getBlockPos());
        return Math.max(tgt.getY() + ABDUCT_OVERHEAD, groundY + MIN_ABOVE_GROUND);
    }

    private void moveToThrottled(double x, double y, double z, double speed, boolean suctionPhase) {
        boolean shouldUpdateY;
        if (Double.isNaN(requestedY)) {
            requestedY = y;
            shouldUpdateY = true;
        } else {
            int hold = suctionPhase ? altHoldSuction : altHoldChase;
            shouldUpdateY = hold <= 0 && Math.abs(y - requestedY) > ALT_CHANGE_EPS;
        }

        if (shouldUpdateY) {
            requestedY = y;
            if (suctionPhase) altHoldSuction = ALT_HOLD_TICKS_SUCTION;
            else altHoldChase = ALT_HOLD_TICKS_CHASE;
        }

        this.getMoveControl().moveTo(x, requestedY, z, speed);
    }

    private void moveToReqY(double x, double y, double z, double speed) {
        this.getMoveControl().moveTo(x, y, z, speed);
        this.requestedY = y;
    }

    private void applyAscendAssist() {
        if (Double.isNaN(requestedY)) return;

        double dy = requestedY - this.getY();
        Vec3d cur = this.getVelocity();

        if (dy > 0.05) {
            double boost = MathHelper.clamp(dy * 0.18, 0.07, 0.52);
            this.setVelocity(cur.x, Math.max(cur.y, cur.y + boost), cur.z);
        } else if (dy < -0.30 && abductPhase != AbductPhase.DIVE) {
            // Descend without crashing too low.
            double drop = MathHelper.clamp((-dy) * 0.12, 0.06, 0.55);
            this.setVelocity(cur.x, Math.max(cur.y - drop, -0.55), cur.z);
        }
    }

    private void applyTractorBeamArea(boolean strong) {
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();

        Box box = new Box(
                x - BEAM_RADIUS_BOTTOM, y - BEAM_HEIGHT, z - BEAM_RADIUS_BOTTOM,
                x + BEAM_RADIUS_BOTTOM, y,               z + BEAM_RADIUS_BOTTOM
        );

        List<LivingEntity> victims = this.getWorld().getEntitiesByClass(LivingEntity.class, box, e -> {
            if (e == this) return false;
            if (!e.isAlive()) return false;
            if (e.isSpectator()) return false;
            if (e instanceof PlayerEntity pl && pl.getAbilities().creativeMode) return false;

            double dy = (y - ANCHOR_OFFSET_DOWN) - e.getY();
            if (dy <= 0 || dy > BEAM_HEIGHT) return false;

            double t = dy / BEAM_HEIGHT;
            double allowedR = MathHelper.lerp(t, BEAM_RADIUS_TOP, BEAM_RADIUS_BOTTOM);
            double dx = e.getX() - x;
            double dz = e.getZ() - z;
            return (dx * dx + dz * dz) <= (allowedR * allowedR);
        });

        if (victims.isEmpty()) return;

        double gain     = strong ? PULL_GAIN_STRONG   : PULL_GAIN_MILD;
        double maxSpeed = strong ? PULL_MAX_STRONG    : PULL_MAX_MILD;
        double minUp    = strong ? PULL_MIN_UP_STRONG : PULL_MIN_UP_MILD;
        int levTicks    = strong ? LEVITATION_TICKS_STRONG : LEVITATION_TICKS_MILD;
        int levAmp      = strong ? LEVITATION_AMP_STRONG   : LEVITATION_AMP_MILD;

        Vec3d anchorMc = new Vec3d(x, y - ANCHOR_OFFSET_DOWN, z);

        for (LivingEntity e : victims) {
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, levTicks, levAmp, true, false));

            Vector3d anchor = new Vector3d(anchorMc.x, anchorMc.y, anchorMc.z);
            Vector3d ev = new Vector3d(e.getVelocity().x, e.getVelocity().y, e.getVelocity().z);
            Vector3d ep = new Vector3d(e.getX(), e.getY(), e.getZ());

            Vector3d toAnchor = anchor.sub(ep, new Vector3d());
            Vector3d desired = toAnchor.mul(gain, new Vector3d());

            double maxSq = maxSpeed * maxSpeed;
            if (desired.lengthSquared() > maxSq) desired.normalize(maxSpeed);

            Vector3d blended = ev.mul(PULL_DAMPING, new Vector3d()).add(desired);
            if (blended.y < minUp) blended.y = minUp;

            e.setVelocity(blended.x, blended.y, blended.z);
            e.velocityModified = true;
            e.fallDistance = 0;
        }
    }

    private boolean canLaserAt(PlayerEntity target) {
        if (laserCooldown > 0 || pendingLaserTicks > 0) return false;
        if (this.squaredDistanceTo(target) > LASER_RANGE * LASER_RANGE) return false;

        // Still airborne while shooting, but not absurdly high.
        if ((this.getY() - target.getY()) > MAX_ATTACK_HEIGHT_ABOVE_TARGET) return false;

        Vec3d eye = this.getPos().add(0, this.getStandingEyeHeight() * 0.7, 0);
        Vec3d tgt = target.getPos().add(0, target.getStandingEyeHeight() * 0.6, 0);
        HitResult hit = this.getWorld().raycast(new RaycastContext(
                eye, tgt,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                this
        ));
        return hit.getType() == HitResult.Type.MISS;
    }

    private void scheduleLaser(PlayerEntity target) {
        laserCooldown = LASER_COOLDOWN_TICKS;
        setAttackAnim(ATTACK_ANIM_TICKS);
        pendingLaserTarget = target;
        pendingLaserTicks = LASER_FIRE_DELAY_TICKS;
    }

    private void fireLaserNow(PlayerEntity target) {
        Vec3d to = target.getPos().add(0, target.getStandingEyeHeight() * 0.45, 0);
        triggerLaserVisual(to);

        target.damage(this.getDamageSources().mobAttack(this), LASER_DAMAGE);

        if (getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 2, 0.03, 0.03, 0.03, 0.01);
            sw.playSound(null, this.getBlockPos(), UfoSounds.LASER, SoundCategory.HOSTILE, 0.8f, 1.55f);
        }
    }

    private void startApproach(PlayerEntity p) {
        abductPhase = AbductPhase.APPROACH_OVERHEAD;
        abductTarget = p;
        abductTicks = ABDUCT_MAX_DURATION;
        suctionTicks = 0;

        int gY = groundYAt(getBlockPos());
        int topCap = this.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, getBlockX(), getBlockZ()) - 2;
        ascentTargetY = Math.min(gY + ASCENT_ALT_ABOVE_GROUND, topCap);
        setTractorBeamActive(true);
    }

    private void startDive() {
        abductPhase = AbductPhase.DIVE;
        diveTicks = DIVE_TICKS;
        requestedY = Double.NaN;
        setTractorBeamActive(false);

        if (getWorld() instanceof ServerWorld sw) {
            sw.playSound(null, getBlockPos(), UfoSounds.DIVE, SoundCategory.HOSTILE, 0.9f, 0.75f);
        }
    }

    private void stopAbduction() {
        abductPhase = AbductPhase.NONE;
        abductTarget = null;
        abductTicks = 0;
        suctionTicks = 0;
        requestedY = Double.NaN;
        setTractorBeamActive(false);
    }

    private void tickApproach() {
        if (abductTarget == null) { stopAbduction(); return; }
        PlayerEntity p = abductTarget;
        if (!p.isAlive() || p.isSpectator() || p.getAbilities().creativeMode) { stopAbduction(); return; }

        double targetY = desiredAbductY(p);
        moveToThrottled(p.getX(), targetY, p.getZ(), ABDUCT_APPROACH_SPEED, false);

        // Tractor beam is already active and now reaches much farther downward.
        applyTractorBeamArea(false);

        double dx = this.getX() - p.getX();
        double dz = this.getZ() - p.getZ();
        double horizSq = dx * dx + dz * dz;

        if (horizSq < (1.8 * 1.8) && (this.getY() - p.getY()) >= MIN_OVERHEAD) {
            abductPhase = AbductPhase.SUCTION_LOCK;
            suctionTicks = 0;
        }

        if (--abductTicks <= 0) startDive();
    }

    private void tickSuctionLock() {
        if (abductTarget == null) { stopAbduction(); return; }
        PlayerEntity p = abductTarget;
        if (!p.isAlive() || p.isSpectator() || p.getAbilities().creativeMode) { stopAbduction(); return; }

        double ang = (this.age * SUCTION_ORBIT_W) + this.getId() * 0.39;
        double offX = Math.cos(ang) * SUCTION_HOVER_R;
        double offZ = Math.sin(ang) * SUCTION_HOVER_R;

        double targetY = Math.max(p.getY() + ABDUCT_OVERHEAD + 0.6, groundYAt(p.getBlockPos()) + MIN_ABOVE_GROUND);
        moveToThrottled(p.getX() + offX, targetY, p.getZ() + offZ, ABDUCT_LOCK_SPEED, true);

        applyTractorBeamArea(true);

        if (++suctionTicks >= SUCTION_LOCK_MIN_TICKS) {
            abductPhase = AbductPhase.ASCENT;
        }

        if (--abductTicks <= 0) startDive();
    }

    private void tickAscent() {
        if (abductTarget == null) { stopAbduction(); return; }
        PlayerEntity p = abductTarget;
        if (!p.isAlive() || p.isSpectator() || p.getAbilities().creativeMode) { stopAbduction(); return; }

        double ang = (this.age * SUCTION_ORBIT_W) + this.getId() * 0.39;
        double offX = Math.cos(ang) * SUCTION_HOVER_R;
        double offZ = Math.sin(ang) * SUCTION_HOVER_R;

        double nextY = Math.min(ascentTargetY, Math.max(this.getY() + 0.65, p.getY() + ABDUCT_OVERHEAD + 1.5));
        moveToThrottled(p.getX() + offX, nextY, p.getZ() + offZ, ABDUCT_ASCENT_SPEED, true);

        applyTractorBeamArea(true);

        if (this.getY() >= ascentTargetY - 0.25 || --abductTicks <= 0) {
            if (p.hasStatusEffect(StatusEffects.LEVITATION)) {
                p.removeStatusEffect(StatusEffects.LEVITATION);
            }
            startDive();
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "ufo.controller", 0, state -> {
            if (getAttackAnim() > 0) {
                state.setAndContinue(ANIM_ATTACK);
                return PlayState.CONTINUE;
            }
            if (this.isTractorBeamActive()) {
                state.setAndContinue(ANIM_FLY);
                return PlayState.CONTINUE;
            }
            if (this.boosting || this.abductPhase == AbductPhase.DIVE) {
                state.setAndContinue(ANIM_FLY);
                return PlayState.CONTINUE;
            }
            state.setAndContinue(ANIM_IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    final class UfoChaseGoal extends Goal {
        private int recalcCooldown;

        UfoChaseGoal() {
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return getTarget() != null && getTarget().isAlive();
        }

        @Override
        public void tick() {
            PlayerEntity tgt = (PlayerEntity) UfoSaucerEntity.this.getTarget();
            if (tgt == null) return;

            switch (abductPhase) {
                case APPROACH_OVERHEAD -> { tickApproach(); return; }
                case SUCTION_LOCK      -> { tickSuctionLock(); return; }
                case ASCENT            -> { tickAscent(); return; }
                case DIVE -> {
                    double desiredY = Math.max(
                            tgt.getY() + 4.0,
                            groundYAt(tgt.getBlockPos()) + MIN_ABOVE_GROUND
                    );

                    double angle = (UfoSaucerEntity.this.age * 0.35) + UfoSaucerEntity.this.getId() * 0.73;
                    double r = 5.0;
                    double offX = Math.cos(angle) * r;
                    double offZ = Math.sin(angle) * r;

                    UfoSaucerEntity.this.moveToReqY(
                            tgt.getX() + offX, desiredY, tgt.getZ() + offZ, DIVE_SPEED
                    );

                    if (UfoSaucerEntity.this.getY() - desiredY > 2.0) {
                        Vec3d vv = UfoSaucerEntity.this.getVelocity();
                        UfoSaucerEntity.this.setVelocity(vv.x, Math.min(vv.y, -1.0), vv.z);
                    }

                    if (Math.abs(UfoSaucerEntity.this.getY() - desiredY) < 1.2 || --diveTicks <= 0) {
                        abductPhase = AbductPhase.NONE;
                        setTractorBeamActive(false);
                    }
                    return;
                }
                default -> {}
            }

            double distSq = UfoSaucerEntity.this.squaredDistanceTo(tgt);
            boolean far = distSq > 18 * 18;
            boolean veryFar = distSq > 34 * 34;
            boosting = far;

            double spd = veryFar ? (DASH_SPEED + 0.10) : (far ? DASH_SPEED : CRUISE_SPEED);

            double moveX;
            double moveZ;

            // Far away = more direct pursuit.
            // Closer = airborne orbit/strafe while firing.
            if (veryFar) {
                moveX = tgt.getX();
                moveZ = tgt.getZ();
            } else {
                double r = far ? ORBIT_R_FAR : ORBIT_R_NEAR;
                double angle = (UfoSaucerEntity.this.age * ORBIT_W) + UfoSaucerEntity.this.getId() * 0.41;
                double offX = Math.cos(angle) * r;
                double offZ = Math.sin(angle) * r;
                moveX = tgt.getX() + offX;
                moveZ = tgt.getZ() + offZ;
            }

            double targetY = desiredCombatY(tgt, far);

            if (--recalcCooldown <= 0 || UfoSaucerEntity.this.getNavigation().isIdle()) {
                recalcCooldown = REPATH_TICKS;
                UfoSaucerEntity.this.moveToThrottled(moveX, targetY, moveZ, spd, false);
            }

            double dx = UfoSaucerEntity.this.getX() - tgt.getX();
            double dz = UfoSaucerEntity.this.getZ() - tgt.getZ();
            double horizSq = dx * dx + dz * dz;

            // When it commits to abduction, it switches into faster direct snatch mode.
            if ((UfoSaucerEntity.this.getY() - tgt.getY()) >= MIN_OVERHEAD
                    && horizSq < (ABDUCT_TRIGGER_HORIZ * ABDUCT_TRIGGER_HORIZ)) {
                startApproach(tgt);
            }
        }
    }

    final class UfoLaserAttackGoal extends Goal {
        UfoLaserAttackGoal() {
            this.setControls(EnumSet.of(Control.TARGET));
        }

        @Override
        public boolean canStart() {
            return getTarget() != null && getTarget().isAlive();
        }

        @Override
        public void tick() {
            PlayerEntity tgt = (PlayerEntity) getTarget();
            if (tgt == null) return;

            if (abductPhase == AbductPhase.SUCTION_LOCK || abductPhase == AbductPhase.ASCENT) {
                if (laserCooldown <= 0 && pendingLaserTicks <= 0 && random.nextFloat() < 0.08f && canLaserAt(tgt)) {
                    scheduleLaser(tgt);
                }
                return;
            }

            if (abductPhase == AbductPhase.DIVE) return;

            if (laserCooldown <= 0 && pendingLaserTicks <= 0 && random.nextFloat() < LASER_RANDOM_CHANCE && canLaserAt(tgt)) {
                scheduleLaser(tgt);
            }
        }
    }

    final class UfoAbductGoal extends Goal {
        UfoAbductGoal() {
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override public boolean canStart() { return false; }
        @Override public boolean shouldContinue() { return abductPhase != AbductPhase.NONE; }
        @Override public void stop() { stopAbduction(); }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("AttackTicks", attackTicks);
        nbt.putInt("LaserCD", laserCooldown);
        nbt.putBoolean("Boosting", boosting);
        nbt.putString("AbductPhase", abductPhase.name());
        nbt.putInt("AbductTicks", abductTicks);
        nbt.putInt("SuctionTicks", suctionTicks);
        nbt.putDouble("AscentTargetY", ascentTargetY);
        nbt.putInt("DiveTicks", diveTicks);
        nbt.putDouble("RequestedY", requestedY);
        nbt.putInt("AltHoldChase", altHoldChase);
        nbt.putInt("AltHoldSuction", altHoldSuction);
        nbt.putBoolean("TractorActive", this.isTractorBeamActive());
        nbt.putInt("LaserVisualTicks", this.dataTracker.get(LASER_VISUAL_TICKS));
        nbt.putFloat("LaserTargetX", this.dataTracker.get(LASER_TARGET_X));
        nbt.putFloat("LaserTargetY", this.dataTracker.get(LASER_TARGET_Y));
        nbt.putFloat("LaserTargetZ", this.dataTracker.get(LASER_TARGET_Z));
        nbt.putInt("PendingLaserTicks", this.pendingLaserTicks);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        attackTicks    = nbt.getInt("AttackTicks");
        laserCooldown  = nbt.getInt("LaserCD");
        boosting       = nbt.getBoolean("Boosting");
        try {
            abductPhase = AbductPhase.valueOf(nbt.getString("AbductPhase"));
        } catch (Exception ignored) {
            abductPhase = AbductPhase.NONE;
        }
        abductTicks    = nbt.getInt("AbductTicks");
        suctionTicks   = nbt.getInt("SuctionTicks");
        ascentTargetY  = nbt.getDouble("AscentTargetY");
        diveTicks      = nbt.getInt("DiveTicks");
        requestedY     = nbt.contains("RequestedY") ? nbt.getDouble("RequestedY") : Double.NaN;
        altHoldChase   = nbt.getInt("AltHoldChase");
        altHoldSuction = nbt.getInt("AltHoldSuction");

        this.dataTracker.set(TRACTOR_ACTIVE, nbt.getBoolean("TractorActive"));
        this.dataTracker.set(LASER_VISUAL_TICKS, nbt.getInt("LaserVisualTicks"));
        this.dataTracker.set(LASER_TARGET_X, nbt.getFloat("LaserTargetX"));
        this.dataTracker.set(LASER_TARGET_Y, nbt.getFloat("LaserTargetY"));
        this.dataTracker.set(LASER_TARGET_Z, nbt.getFloat("LaserTargetZ"));

        this.pendingLaserTicks = 0;
        this.pendingLaserTarget = null;

        this.setNoGravity(true);
        this.setSilent(false);
    }
}