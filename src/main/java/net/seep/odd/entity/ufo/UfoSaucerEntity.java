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
import net.minecraft.particle.DustColorTransitionParticleEffect;
import net.minecraft.particle.ParticleEffect;
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
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.List;

public final class UfoSaucerEntity extends PathAwareEntity implements GeoEntity {
    /* ---------- GeckoLib ---------- */
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation ANIM_IDLE   = RawAnimation.begin().thenLoop("ufo_idle");
    private static final RawAnimation ANIM_FLY    = RawAnimation.begin().thenPlay("ufo_fly");     // HOLD_ON_LAST_FRAME in JSON
    private static final RawAnimation ANIM_ATTACK = RawAnimation.begin().thenPlay("ufo_attack");  // PLAY_ONCE

    /* ---------- Tunables ---------- */
    private static final double CRUISE_SPEED = 0.72;
    private static final double DASH_SPEED   = 1.45;
    private static final double ASCENT_SPEED = 0.85;   // slower climb while abducting
    private static final int    REPATH_TICKS = 1;

    private static final double ORBIT_R_NEAR = 6.0;
    private static final double ORBIT_R_FAR  = 11.0;
    private static final double ORBIT_W      = 0.35;

    private static final int    AGGRO_RADIUS = 42;
    private static final double MIN_OVERHEAD = 4.0;   // keep a little vertical distance while beaming
    private static final double MIN_ABOVE_GROUND = 5.0;

    // Lateral offset during suction so it's not sitting exactly above the head
    private static final double SUCTION_HOVER_R   = 2.0;
    private static final double SUCTION_ORBIT_W   = 0.25;

    // Laser feel
    private static final float  LASER_DAMAGE = 5.0f;
    private static final int    LASER_RANGE  = 24;
    private static final int    LASER_COOLDOWN_TICKS = 95;
    private static final float  LASER_RANDOM_CHANCE  = 0.18f;
    private static final int    ATTACK_ANIM_TICKS    = 30;

    // Abduction phases
    private enum AbductPhase { NONE, APPROACH_OVERHEAD, SUCTION_LOCK, ASCENT, DIVE }
    private static final int    ABDUCT_MAX_DURATION    = 600;
    private static final int    SUCTION_LOCK_MIN_TICKS = 12;

    // Tractor beam cone (downward from saucer)
    private static final double BEAM_HEIGHT         = 12.0;
    private static final double BEAM_RADIUS_TOP     = 1.2;
    private static final double BEAM_RADIUS_BOTTOM  = 4.0;

    // Pull constants (gentler)
    private static final double PULL_GAIN_STRONG    = 0.85;  // was 1.10
    private static final double PULL_GAIN_MILD      = 0.55;  // was 0.70
    private static final double PULL_DAMPING        = 0.08;  // a touch more smoothing
    private static final double PULL_MAX_STRONG     = 0.90;  // was 1.25
    private static final double PULL_MAX_MILD       = 0.70;  // was 0.95
    private static final double PULL_MIN_UP_STRONG  = 0.45;  // was 0.70
    private static final double PULL_MIN_UP_MILD    = 0.35;  // was 0.55

    // Levitation (kept the same)
    private static final int    LEVITATION_TICKS_MILD   = 8;
    private static final int    LEVITATION_TICKS_STRONG = 10;
    private static final int    LEVITATION_AMP_MILD     = 0;
    private static final int    LEVITATION_AMP_STRONG   = 1;

    private static final double ANCHOR_OFFSET_DOWN   = 2.2;

    private static final int    ASCENT_ALT_ABOVE_GROUND = 300;

    private static final int    DIVE_TICKS = 50;
    private static final double DIVE_SPEED = 0.92;

    private static final double HOVER_BOB_AMPL = 0.06;
    private static final double HOVER_BOB_FREQ = 0.08;

    // Altitude readjust “hold” so it doesn’t twitch constantly
    private static final int    ALT_HOLD_TICKS_CHASE   = 10;
    private static final int    ALT_HOLD_TICKS_SUCTION = 8;
    private static final double ALT_CHANGE_EPS         = 0.75;

    /* ---------- Synced anim state ---------- */
    private static final TrackedData<Integer> ATTACK_TIME =
            DataTracker.registerData(UfoSaucerEntity.class, TrackedDataHandlerRegistry.INTEGER);

    /* ---------- State ---------- */
    private int attackTicks;
    private int laserCooldown;
    private boolean boosting;

    private AbductPhase abductPhase = AbductPhase.NONE;
    private int abductTicks;
    private int suctionTicks;
    private double ascentTargetY;
    @Nullable private PlayerEntity abductTarget;

    private int diveTicks;

    // ascent assist + altitude holds
    private double requestedY = Double.NaN;
    private int altHoldChase = 0;
    private int altHoldSuction = 0;

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
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.35D)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 1.10D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, AGGRO_RADIUS)
                .add(EntityAttributes.GENERIC_ARMOR, 4.0D);
    }

    /* ---------- Data tracker ---------- */
    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(ATTACK_TIME, 0);
    }
    private void setAttackAnim(int ticks) {
        this.attackTicks = ticks;
        this.dataTracker.set(ATTACK_TIME, ticks);
    }
    private int getAttackAnim() {
        return this.dataTracker.get(ATTACK_TIME);
    }

    /* ---------- Navigation ---------- */
    @Override
    protected EntityNavigation createNavigation(World world) {
        BirdNavigation nav = new BirdNavigation(this, world);
        nav.setCanSwim(false);
        nav.setCanPathThroughDoors(false);
        nav.setCanEnterOpenDoors(true);
        return nav;
    }

    @Override public boolean hasNoGravity() { return true; }

    /* ---------- Sounds ---------- */
    private static final class UfoSounds {
        static final SoundEvent IDLE   = ModSounds.SAUCER_HOVER;
        static final SoundEvent HURT   = ModSounds.SAUCER_HURT;
        static final SoundEvent DEATH  = ModSounds.SAUCER_DEATH;
        static final SoundEvent LASER  = ModSounds.SAUCER_ATTACK;
        static final SoundEvent ABDUCT = ModSounds.SAUCER_TRACTOR;
        static final SoundEvent DIVE   = ModSounds.SAUCER_BOOST;
    }
    @Override protected SoundEvent getAmbientSound() { return UfoSounds.IDLE; }
    @Override protected SoundEvent getHurtSound(net.minecraft.entity.damage.DamageSource src){ return UfoSounds.HURT; }
    @Override protected SoundEvent getDeathSound(){ return UfoSounds.DEATH; }
    @Override protected float getSoundVolume(){ return 0.6F; }
    @Override public int getMinAmbientSoundDelay(){ return 40; }

    /* ---------- Goals ---------- */
    @Override
    protected void initGoals() {
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true,
                p -> p.isAlive() && !p.isSpectator()));

        this.goalSelector.add(1, new UfoAbductGoal());
        this.goalSelector.add(2, new UfoLaserAttackGoal());
        this.goalSelector.add(3, new UfoChaseGoal());
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 24f));
        this.goalSelector.add(9, new LookAroundGoal(this));
    }

    /* ---------- Tick ---------- */
    @Override
    public void tick() {
        super.tick();

        if (!this.getWorld().isClient) {
            if (this.getTarget() == null || !this.getTarget().isAlive() || this.age % 20 == 0) {
                PlayerEntity nearest = this.getWorld().getClosestPlayer(this, AGGRO_RADIUS);
                if (nearest != null && !nearest.isSpectator()) {
                    this.setTarget(nearest);
                }
            }
        }

        if (!this.isTouchingWater() && !this.isSubmergedIn(FluidTags.WATER)) {
            double bob = Math.sin(age * HOVER_BOB_FREQ) * HOVER_BOB_AMPL * 0.02;
            this.setVelocity(this.getVelocity().x * 0.94, this.getVelocity().y * 0.94 + bob, this.getVelocity().z * 0.94);
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
            float targetYaw = (float)(MathHelper.atan2(v.z, v.x) * (180F/Math.PI)) - 90F;
            float smooth = rotateTowards(this.getYaw(), targetYaw, 12f);
            this.setYaw(smooth);
            this.setBodyYaw(smooth);
            this.setHeadYaw(smooth);
        }

        // decrement altitude holds
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

    private double minChaseYFor(PlayerEntity tgt, double extra) {
        int groundY = groundYAt(tgt.getBlockPos());
        return Math.max(tgt.getY() + MIN_OVERHEAD + extra, groundY + MIN_ABOVE_GROUND);
    }

    /** Move with altitude throttling, so Y isn't updated every tick. */
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

    /** Remember Y so tick() can add vertical boost immediately (no throttle). */
    private void moveToReqY(double x, double y, double z, double speed) {
        this.getMoveControl().moveTo(x, y, z, speed);
        this.requestedY = y;
    }

    /** Gentle vertical authority toward requestedY; also limits instant sink unless diving. */
    private void applyAscendAssist() {
        if (Double.isNaN(requestedY)) return;

        double dy = requestedY - this.getY();
        if (dy > 0.05) {
            double boost = MathHelper.clamp(dy * 0.18, 0.06, 0.42); // gentler upward assist
            Vec3d cur = this.getVelocity();
            this.setVelocity(cur.x, Math.max(cur.y, cur.y + boost), cur.z);
        } else if (dy < -0.35 && abductPhase != AbductPhase.DIVE) {
            Vec3d cur = this.getVelocity();
            this.setVelocity(cur.x, Math.max(cur.y, -0.3), cur.z);
        }
    }

    /* ---------- Tractor beam: apply levitation + pull to all in cone ---------- */
    private void applyTractorBeamArea(boolean strong) {
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();

        double rBottom = BEAM_RADIUS_BOTTOM;
        Box box = new Box(
                x - rBottom, y - BEAM_HEIGHT, z - rBottom,
                x + rBottom, y,              z + rBottom
        );

        List<LivingEntity> victims = this.getWorld().getEntitiesByClass(LivingEntity.class, box, e -> {
            if (e == this) return false;
            if (!e.isAlive()) return false;
            if (e.isSpectator()) return false;
            if (e instanceof PlayerEntity pl && pl.getAbilities().creativeMode) return false;
            // inside cone?
            double dy = (y - ANCHOR_OFFSET_DOWN) - e.getY();
            if (dy <= 0 || dy > BEAM_HEIGHT) return false;
            double t = dy / BEAM_HEIGHT;
            double allowedR = MathHelper.lerp(t, BEAM_RADIUS_TOP, BEAM_RADIUS_BOTTOM);
            double dx = e.getX() - x;
            double dz = e.getZ() - z;
            return (dx*dx + dz*dz) <= (allowedR * allowedR);
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

    /* ---------- Combat ---------- */
    private boolean canLaserAt(PlayerEntity target) {
        if (laserCooldown > 0) return false;
        if (this.squaredDistanceTo(target) > LASER_RANGE * LASER_RANGE) return false;
        Vec3d eye = this.getPos().add(0, this.getStandingEyeHeight() * 0.7, 0);
        Vec3d tgt = target.getPos().add(0, target.getStandingEyeHeight() * 0.6, 0);
        var hit = this.getWorld().raycast(new RaycastContext(eye, tgt, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
        return hit.getType() == HitResult.Type.MISS;
    }

    private void fireLaser(PlayerEntity target) {
        laserCooldown = LASER_COOLDOWN_TICKS;
        setAttackAnim(ATTACK_ANIM_TICKS);

        target.damage(this.getDamageSources().mobAttack(this), LASER_DAMAGE);

        if (getWorld() instanceof ServerWorld sw) {
            Vec3d from = this.getPos().add(0, this.getStandingEyeHeight() * 0.7, 0);
            Vec3d to   = target.getPos().add(0, target.getStandingEyeHeight() * 0.45, 0);

            sw.spawnParticles(ParticleTypes.FLASH, from.x, from.y, from.z, 1, 0, 0, 0, 0);

            ParticleEffect trail = new DustColorTransitionParticleEffect(
                    new Vector3f(0.95f, 0.35f, 0.10f),
                    new Vector3f(1.0f, 0.85f, 0.40f),
                    0.5f);
            int steps = 14;
            for (int i = 0; i <= steps; i++) {
                double t = i / (double)steps;
                Vec3d p = from.lerp(to, t);
                sw.spawnParticles(trail, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
            sw.spawnParticles(ParticleTypes.CRIT, to.x, to.y, to.z, 8, 0.08, 0.08, 0.08, 0.01);
            sw.playSound(null, this.getBlockPos(), UfoSounds.LASER, SoundCategory.HOSTILE, 0.8f, 1.55f);
        }
    }

    /* ---------- Abduction (cone beam) ---------- */
    private void startApproach(PlayerEntity p) {
        abductPhase = AbductPhase.APPROACH_OVERHEAD;
        abductTarget = p;
        abductTicks = ABDUCT_MAX_DURATION;
        suctionTicks = 0;
        int gY = groundYAt(getBlockPos());
        int topCap = this.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, getBlockX(), getBlockZ()) - 2;
        ascentTargetY = Math.min(gY + ASCENT_ALT_ABOVE_GROUND, topCap);
        if (getWorld() instanceof ServerWorld sw) {
            sw.playSound(null, getBlockPos(), UfoSounds.ABDUCT, SoundCategory.HOSTILE, 0.9f, 1.2f);
        }
    }
    private void startDive() {
        abductPhase = AbductPhase.DIVE;
        diveTicks = DIVE_TICKS;
        if (getWorld() instanceof ServerWorld sw) {
            sw.playSound(null, getBlockPos(), UfoSounds.DIVE, SoundCategory.HOSTILE, 0.9f, 0.75f);
        }
        requestedY = Double.NaN;
    }
    private void stopAbduction() {
        abductPhase = AbductPhase.NONE;
        abductTarget = null;
        abductTicks = 0;
        suctionTicks = 0;
        requestedY = Double.NaN;
    }

    private void tickApproach() {
        if (abductTarget == null) { stopAbduction(); return; }
        PlayerEntity p = abductTarget;
        if (!p.isAlive() || p.isSpectator() || p.getAbilities().creativeMode) { stopAbduction(); return; }

        // Move to near-overhead (gentle Y throttling)
        double targetY = minChaseYFor(p, 0);
        moveToThrottled(p.getX(), targetY, p.getZ(), CRUISE_SPEED, false);

        // Sticky mild beam once we're almost overhead
        boolean verticalGood = (this.getY() - p.getY()) >= (MIN_OVERHEAD - 0.5);
        if (verticalGood) {
            applyTractorBeamArea(false);
        }

        // Enter SUCTION_LOCK once nearly centered overhead
        double horiz = this.getPos().squaredDistanceTo(p.getX(), this.getY(), p.getZ());
        if (horiz < 1.2 && (this.getY() - p.getY()) >= MIN_OVERHEAD) {
            abductPhase = AbductPhase.SUCTION_LOCK;
            suctionTicks = 0;
        }

        if (--abductTicks <= 0) { startDive(); }
    }

    private void tickSuctionLock() {
        if (abductTarget == null) { stopAbduction(); return; }
        PlayerEntity p = abductTarget;
        if (!p.isAlive() || p.isSpectator() || p.getAbilities().creativeMode) { stopAbduction(); return; }

        // Keep slightly offset around the player so it isn't hugging directly above
        double ang = (this.age * SUCTION_ORBIT_W) + this.getId() * 0.39;
        double offX = Math.cos(ang) * SUCTION_HOVER_R;
        double offZ = Math.sin(ang) * SUCTION_HOVER_R;

        // Maintain gentle overhead with altitude throttling
        double targetY = Math.max(p.getY() + MIN_OVERHEAD + 0.5, groundYAt(p.getBlockPos()) + MIN_ABOVE_GROUND);
        moveToThrottled(p.getX() + offX, targetY, p.getZ() + offZ, CRUISE_SPEED, true);

        // Strong beam every tick to everyone in cone
        applyTractorBeamArea(true);

        // After a short lock, go to ASCENT
        if (++suctionTicks >= SUCTION_LOCK_MIN_TICKS) {
            abductPhase = AbductPhase.ASCENT;
        }
        if (--abductTicks <= 0) { startDive(); }
    }

    private void tickAscent() {
        if (abductTarget == null) { stopAbduction(); return; }
        PlayerEntity p = abductTarget;
        if (!p.isAlive() || p.isSpectator() || p.getAbilities().creativeMode) { stopAbduction(); return; }

        // Keep offset while climbing; slower ascent speed
        double ang = (this.age * SUCTION_ORBIT_W) + this.getId() * 0.39;
        double offX = Math.cos(ang) * SUCTION_HOVER_R;
        double offZ = Math.sin(ang) * SUCTION_HOVER_R;

        double nextY = Math.min(ascentTargetY, Math.max(this.getY() + 0.45, p.getY() + MIN_OVERHEAD + 1.0));
        moveToThrottled(p.getX() + offX, nextY, p.getZ() + offZ, ASCENT_SPEED, true);

        // Strong beam continues during ascent (gentle pull constants)
        applyTractorBeamArea(true);

        // Finish when high enough or time up → drop + dive
        if (this.getY() >= ascentTargetY - 0.25 || --abductTicks <= 0) {
            if (p.hasStatusEffect(StatusEffects.LEVITATION)) p.removeStatusEffect(StatusEffects.LEVITATION);
            startDive();
        }
    }

    /* ---------- GeckoLib Controllers ---------- */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "ufo.controller", 0, state -> {
            if (getAttackAnim() > 0) {
                state.setAndContinue(ANIM_ATTACK);
                return PlayState.CONTINUE;
            }
            if (this.abductPhase != AbductPhase.NONE && this.abductPhase != AbductPhase.DIVE) {
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

    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){ return cache; }

    /* ---------- AI Goals ---------- */
    final class UfoChaseGoal extends Goal {
        private int recalcCooldown;
        UfoChaseGoal(){ this.setControls(EnumSet.of(Control.MOVE, Control.LOOK)); }
        @Override public boolean canStart(){ return getTarget() != null && getTarget().isAlive(); }

        @Override public void tick() {
            PlayerEntity tgt = (PlayerEntity) UfoSaucerEntity.this.getTarget();
            if (tgt == null) return;

            switch (abductPhase) {
                case APPROACH_OVERHEAD -> { tickApproach(); return; }
                case SUCTION_LOCK      -> { tickSuctionLock(); return; }
                case ASCENT            -> { tickAscent(); return; }
                case DIVE -> {
                    double desiredY = Math.max(
                            tgt.getY() + 2.0,
                            groundYAt(tgt.getBlockPos()) + MIN_ABOVE_GROUND
                    );

                    double angle = (UfoSaucerEntity.this.age * 0.35) + UfoSaucerEntity.this.getId() * 0.73;
                    double r = 4.5;
                    double offX = Math.cos(angle) * r;
                    double offZ = Math.sin(angle) * r;

                    UfoSaucerEntity.this.moveToReqY(
                            tgt.getX() + offX, desiredY, tgt.getZ() + offZ, DIVE_SPEED);

                    if (UfoSaucerEntity.this.getY() - desiredY > 2.0) {
                        Vec3d vv = UfoSaucerEntity.this.getVelocity();
                        UfoSaucerEntity.this.setVelocity(vv.x, Math.min(vv.y, -1.0), vv.z);
                    }

                    if (Math.abs(UfoSaucerEntity.this.getY() - desiredY) < 1.2 || --diveTicks <= 0) {
                        abductPhase = AbductPhase.NONE;
                    }
                    return;
                }
                default -> {}
            }

            // Swarm/orbit (ALLOW DESCENT)
            double distSq = UfoSaucerEntity.this.squaredDistanceTo(tgt);
            boolean far = distSq > 14 * 14;
            boosting = far;

            double spd = far ? DASH_SPEED : CRUISE_SPEED;
            double r   = far ? ORBIT_R_FAR : ORBIT_R_NEAR;
            double angle = (UfoSaucerEntity.this.age * ORBIT_W) + UfoSaucerEntity.this.getId() * 0.41;
            double offX = Math.cos(angle) * r;
            double offZ = Math.sin(angle) * r;

            double targetY = minChaseYFor(tgt, far ? 1.0 : 0.0);
            if (--recalcCooldown <= 0 || UfoSaucerEntity.this.getNavigation().isIdle()) {
                recalcCooldown = REPATH_TICKS;
                UfoSaucerEntity.this.moveToThrottled(
                        tgt.getX() + offX, targetY, tgt.getZ() + offZ, spd, false);
            }

            // Enter abduction if we're already roughly above & close
            double dx = UfoSaucerEntity.this.getX() - tgt.getX();
            double dz = UfoSaucerEntity.this.getZ() - tgt.getZ();
            double horizSq = dx * dx + dz * dz;
            if ((UfoSaucerEntity.this.getY() - tgt.getY()) >= MIN_OVERHEAD && horizSq < 4.0) {
                startApproach(tgt);
            }
        }
    }

    final class UfoLaserAttackGoal extends Goal {
        UfoLaserAttackGoal(){ this.setControls(EnumSet.of(Control.TARGET)); }
        @Override public boolean canStart(){ return getTarget() != null && getTarget().isAlive(); }
        @Override public void tick() {
            PlayerEntity tgt = (PlayerEntity)getTarget();
            if (tgt == null) return;

            if (abductPhase == AbductPhase.SUCTION_LOCK || abductPhase == AbductPhase.ASCENT) {
                if (laserCooldown <= 0 && random.nextFloat() < 0.08f && canLaserAt(tgt)) fireLaser(tgt);
                return;
            }
            if (abductPhase == AbductPhase.DIVE) return;

            if (laserCooldown <= 0 && random.nextFloat() < LASER_RANDOM_CHANCE && canLaserAt(tgt)) {
                fireLaser(tgt);
            }
        }
    }

    final class UfoAbductGoal extends Goal {
        UfoAbductGoal(){ this.setControls(EnumSet.of(Control.MOVE, Control.LOOK)); }
        @Override public boolean canStart(){ return false; }
        @Override public boolean shouldContinue(){ return abductPhase != AbductPhase.NONE; }
        @Override public void stop(){ stopAbduction(); }
    }

    /* ---------- NBT ---------- */
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
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        attackTicks    = nbt.getInt("AttackTicks");
        laserCooldown  = nbt.getInt("LaserCD");
        boosting       = nbt.getBoolean("Boosting");
        try { abductPhase = AbductPhase.valueOf(nbt.getString("AbductPhase")); } catch (Exception ignored){ abductPhase = AbductPhase.NONE; }
        abductTicks    = nbt.getInt("AbductTicks");
        suctionTicks   = nbt.getInt("SuctionTicks");
        ascentTargetY  = nbt.getDouble("AscentTargetY");
        diveTicks      = nbt.getInt("DiveTicks");
        requestedY     = nbt.contains("RequestedY") ? nbt.getDouble("RequestedY") : Double.NaN;
        altHoldChase   = nbt.getInt("AltHoldChase");
        altHoldSuction = nbt.getInt("AltHoldSuction");
        this.setNoGravity(true);
        this.setSilent(false);
    }
}
