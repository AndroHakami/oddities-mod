package net.seep.odd.entity.dragoness;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.seep.odd.Oddities;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class DragonessEntity extends HostileEntity implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ATTACK_LASER = RawAnimation.begin().thenPlay("attack_laser");
    private static final RawAnimation STANCE_FLIGHT = RawAnimation.begin().thenPlay("stance_flight");
    private static final RawAnimation FLIGHT = RawAnimation.begin().thenLoop("flight");
    private static final RawAnimation FLIGHT_TO_THROW = RawAnimation.begin().thenPlay("flight_to_throw");
    private static final RawAnimation ATTACK_KICKS = RawAnimation.begin().thenPlay("attack_kicks");
    private static final RawAnimation ATTACK_BREAK = RawAnimation.begin().thenPlay("attack_break");
    private static final RawAnimation BACKFLIP = RawAnimation.begin().thenPlay("backflip");
    private static final RawAnimation STANCE_KICK_DASH = RawAnimation.begin().thenPlay("stance_kick_dash");
    private static final RawAnimation KICK_DASH = RawAnimation.begin().thenLoop("kick_dash");
    private static final RawAnimation ATTACK_CRASH = RawAnimation.begin().thenPlay("attack_crash");
    private static final RawAnimation STANCE_SIT = RawAnimation.begin().thenPlay("stance_sit");
    private static final RawAnimation SITTING = RawAnimation.begin().thenLoop("sitting");
    private static final RawAnimation SITTING_DISTURBED = RawAnimation.begin().thenPlay("sitting_disturped");
    private static final RawAnimation STANCE_COMBO_DASH = RawAnimation.begin().thenPlay("stance_combo_dash");
    private static final RawAnimation COMBO_HIT1 = RawAnimation.begin().thenPlay("combo_hit1");
    private static final RawAnimation COMBO_HIT2 = RawAnimation.begin().thenPlay("combo_hit2");
    private static final RawAnimation COMBO_FLY_DOWN = RawAnimation.begin().thenLoop("combo_fly_down");

    private static final float BASE_MAX_HEALTH = 420.0f;
    private static final double DETECTION_RANGE = 182.0D;
    private static final double WALK_SPEED = 1.02D;
    private static final double WALK_STOP_RANGE = 5.6D;
    private static final double MELEE_RANGE = 6.4D;
    private static final double FLIGHT_START_RANGE = 48.0D;

    public static final int LASER_TOTAL_TICKS = 77;
    public static final int LASER_CAST_TICK = 36;
    private static final int LASER_COOLDOWN = 88;
    private static final double LASER_TARGET_RANGE = 28.0D;
    private static final float LASER_AREA_HALF_SIZE = 1.55f;
    private static final float LASER_EXPLOSION_POWER = 3.4f;

    public static final int FLIGHT_STANCE_TOTAL_TICKS = 18;
    private static final int FLIGHT_LIFT_TICK = 30;
    private static final int FLIGHT_COOLDOWN = 20 * 20;
    private static final int FLIGHT_CHASE_LIMIT = 240;
    private static final int FLIGHT_ASCEND_TICKS = 24;
    public static final int FLIGHT_THROW_TOTAL_TICKS = 25;
    private static final int FLIGHT_THROW_RELEASE_TICK = 13;
    private static final double FLIGHT_SPEED = 1.34D;
    private static final double ASCEND_SPEED = 1.08D;
    private static final double FLIGHT_GRAB_RANGE = 3.8D;
    private static final int FLIGHT_GRAB_WINDOW_TICKS = 30;
    private static final double FLIGHT_GRAB_FRONT_DOT = 0.42D;
    private static final double FLIGHT_GRAB_VERTICAL_TOLERANCE = 4.5D;
    private static final double THROW_SPEED = 1.35D;
    private static final float METEOR_EXPLOSION_POWER = 4.0f;
    private static final int METEOR_MAX_TICKS = 80;

    public static final int KICK_TOTAL_TICKS = 64;
    private static final int KICK_START_TICK = 12;
    private static final int KICK_END_TICK = 58;
    private static final int KICK_HIT_INTERVAL = 4;
    private static final int KICK_COOLDOWN = 84;
    private static final float KICK_TICK_DAMAGE = 6.2f;

    public static final int BREAKER_TOTAL_TICKS = 53;
    public static final int BREAKER_SUMMON_TICK = 33;
    public static final int BREAKER_DIVE_TICK = 45;
    public static final int BREAKER_BACKFLIP_TICKS = 34;
    public static final int BREAKER_BACKFLIP_TOTAL_TICKS = BREAKER_BACKFLIP_TICKS;
    public static final int BREAKER_CRASH_START_TICK = BREAKER_DIVE_TICK;
    private static final int BREAKER_COOLDOWN = 205;
    private static final float BREAKER_WAVE_RADIUS = 5.5f;
    private static final float BREAKER_CRASH_POWER = 3.4f;

    public static final int SLIDE_STANCE_TICKS = 11;
    public static final int SLIDE_DASH_TICKS = 18;
    public static final int SLIDE_DASH_STANCE_TOTAL_TICKS = SLIDE_STANCE_TICKS;
    public static final int SLIDE_DASH_TOTAL_TICKS = SLIDE_DASH_TICKS;
    private static final int SLIDE_COOLDOWN = 58;
    private static final double SLIDE_DASH_SPEED = 1.95D;
    private static final float SLIDE_DAMAGE = 8.0f;

    public static final int CRASH_TOTAL_TICKS = 48;
    public static final int CRASH_DIVE_TICK = 45;
    public static final int CRASH_DIVE_START_TICK = CRASH_DIVE_TICK;
    private static final int CRASH_COOLDOWN = 92;
    private static final float CRASH_EXPLOSION_POWER = 4.3f;

    public static final int CHILL_STANCE_TICKS = 48;
    public static final int CHILL_END_TICKS = 26;
    public static final int CHILL_STANCE_TOTAL_TICKS = CHILL_STANCE_TICKS;
    public static final int CHILL_DISTURBED_TOTAL_TICKS = CHILL_END_TICKS;
    private static final int CHILL_COOLDOWN = 20 * 200;
    private static final int CHILL_UFO_COUNT = 6;

    public static final int COMBO_DASH_TICKS = 18;
    public static final int COMBO_HIT1_TICKS = 47;
    public static final int COMBO_HIT2_TICKS = 43;
    public static final int COMBO_DASH_STANCE_TOTAL_TICKS = COMBO_DASH_TICKS;
    public static final int COMBO_HIT1_TOTAL_TICKS = COMBO_HIT1_TICKS;
    public static final int COMBO_HIT2_TOTAL_TICKS = COMBO_HIT2_TICKS;
    private static final int COMBO_COOLDOWN = 225;
    private static final double COMBO_DASH_SPEED = 1.90D;
    private static final int COMBO_LAUNCH_TICK = 3;
    private static final int COMBO_CHASE_START_TICK = 6;
    private static final int COMBO_CHASE_END_TICK = 28;
    private static final double COMBO_LAUNCH_HORIZONTAL_SPEED = 0.38D;
    private static final double COMBO_LAUNCH_UP_SPEED = 4.95D;
    private static final double COMBO_CHASE_SPEED = 2.85D;
    private static final double COMBO_CHASE_CLOSE_RANGE = 2.8D;
    private static final double BREAKER_ASCEND_HEIGHT = 34.0D;
    private static final double CRASH_ASCEND_HEIGHT = 46.0D;
    private static final double CHILL_FOLLOW_SPEED = 0.34D;
    private static final double CHILL_FOLLOW_STOP_RANGE = 4.5D;
    private static final int COMBO_SLAM_TICK = 18;
    private static final int COMBO_HIT1_MIN_TICKS = 28;
    private static final double COMBO_ATTACH_SPEED = 3.10D;
    private static final double COMBO_PEAK_VERTICAL_THRESHOLD = 0.08D;
    private static final float COMBO_FINAL_CRASH_POWER = 4.8f;

    private static final int ATTACK_RECOVERY_TICKS = 16;

    private static final int WEIGHT_COMBO = 80;
    private static final int WEIGHT_BREAKER = 92;
    private static final int WEIGHT_CHILL = 74;
    private static final int WEIGHT_FLIGHT = 94;
    private static final int WEIGHT_SLIDE = 108;
    private static final int WEIGHT_CRASH = 72;
    private static final int WEIGHT_LASER = 16;

    private static final TrackedData<Integer> ATTACK_TYPE =
            DataTracker.registerData(DragonessEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> ATTACK_TICKS =
            DataTracker.registerData(DragonessEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> ATTACK_SERIAL =
            DataTracker.registerData(DragonessEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> LASER_SEED =
            DataTracker.registerData(DragonessEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> LASER_CENTER_X =
            DataTracker.registerData(DragonessEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> LASER_CENTER_Y =
            DataTracker.registerData(DragonessEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> LASER_CENTER_Z =
            DataTracker.registerData(DragonessEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> FLIGHT_POSE_PITCH =
            DataTracker.registerData(DragonessEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> FLIGHT_POSE_ROLL =
            DataTracker.registerData(DragonessEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> METEOR_TARGET_ID =
            DataTracker.registerData(DragonessEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> METEOR_TICKS =
            DataTracker.registerData(DragonessEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> IMPACT_SERIAL =
            DataTracker.registerData(DragonessEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> IMPACT_X =
            DataTracker.registerData(DragonessEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> IMPACT_Y =
            DataTracker.registerData(DragonessEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> IMPACT_Z =
            DataTracker.registerData(DragonessEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> IMPACT_RADIUS =
            DataTracker.registerData(DragonessEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<NbtCompound> LASER_TARGETS =
            DataTracker.registerData(DragonessEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
    private static final TrackedData<Float> DIVE_TARGET_X =
            DataTracker.registerData(DragonessEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> DIVE_TARGET_Y =
            DataTracker.registerData(DragonessEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> DIVE_TARGET_Z =
            DataTracker.registerData(DragonessEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossBar bossBar = new ServerBossBar(Text.literal("Dragoness"), BossBar.Color.GREEN, BossBar.Style.PROGRESS);

    private int serverAttackTicks = 0;
    private int attackSerial = 0;
    private int laserCooldownTicks = 40;
    private int flightCooldownTicks = 28;
    private int kickCooldownTicks = 10;
    private int breakerCooldownTicks = 110;
    private int slideCooldownTicks = 24;
    private int crashCooldownTicks = 40;
    private int chillCooldownTicks = 140;
    private int comboCooldownTicks = 140;
    private int attackRecoveryTicks = 0;
    private int idleRetargetTicks = 0;

    private @Nullable UUID carriedTargetUuid = null;
    private int flightChaseTicks = 0;
    private int flightAscendTicks = 0;

    private @Nullable UUID meteorTargetUuid = null;
    private int meteorTicksLeft = 0;

    private @Nullable UUID comboTargetUuid = null;
    private @Nullable UUID breakerCrashTargetUuid = null;
    private @Nullable UUID crashTargetUuid = null;
    private @Nullable Vec3d slideDashDirection = null;
    private @Nullable Vec3d comboDashDirection = null;
    private @Nullable Vec3d comboDiveTarget = null;
    private boolean chillProtectorsSpawned = false;
    private float chillLockedYaw = 0.0f;

    public DragonessEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.ignoreCameraFrustum = true;
        this.setPersistent();
        this.experiencePoints = 200;
        this.bossBar.setVisible(true);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, BASE_MAX_HEALTH)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.34D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, DETECTION_RANGE)
                .add(EntityAttributes.GENERIC_ARMOR, 12.0D)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 10.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(ATTACK_TYPE, DragonessAttackType.NONE.ordinal());
        this.dataTracker.startTracking(ATTACK_TICKS, 0);
        this.dataTracker.startTracking(ATTACK_SERIAL, 0);
        this.dataTracker.startTracking(LASER_SEED, 0);
        this.dataTracker.startTracking(LASER_CENTER_X, 0.0f);
        this.dataTracker.startTracking(LASER_CENTER_Y, 0.0f);
        this.dataTracker.startTracking(LASER_CENTER_Z, 0.0f);
        this.dataTracker.startTracking(FLIGHT_POSE_PITCH, 0.0f);
        this.dataTracker.startTracking(FLIGHT_POSE_ROLL, 0.0f);
        this.dataTracker.startTracking(METEOR_TARGET_ID, -1);
        this.dataTracker.startTracking(METEOR_TICKS, 0);
        this.dataTracker.startTracking(IMPACT_SERIAL, 0);
        this.dataTracker.startTracking(IMPACT_X, 0.0f);
        this.dataTracker.startTracking(IMPACT_Y, 0.0f);
        this.dataTracker.startTracking(IMPACT_Z, 0.0f);
        this.dataTracker.startTracking(IMPACT_RADIUS, 0.0f);
        this.dataTracker.startTracking(LASER_TARGETS, new NbtCompound());
        this.dataTracker.startTracking(DIVE_TARGET_X, 0.0f);
        this.dataTracker.startTracking(DIVE_TARGET_Y, -9999.0f);
        this.dataTracker.startTracking(DIVE_TARGET_Z, 0.0f);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 24.0f));
        this.goalSelector.add(9, new LookAroundGoal(this));
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true, p -> p.isAlive() && !p.isSpectator()));
    }

    @Override
    public void tick() {
        super.tick();
        this.fallDistance = 0.0f;
        this.extinguish();
        if (!this.getWorld().isClient()) {
            this.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 10, 0, false, false, false));

            this.bossBar.setPercent(MathHelper.clamp(this.getHealth() / this.getMaxHealth(), 0.0f, 1.0f));
        }

        if (this.getWorld().isClient()) {
            return;
        }

        tickCooldowns();

        LivingEntity target = getCombatTarget();
        if (target == null || !target.isAlive()) {
            findNewTarget();
            target = getCombatTarget();
        }

        tickMeteorVictim();

        DragonessAttackType attack = getAttackType();
        if (attack != DragonessAttackType.NONE) {
            this.serverAttackTicks++;
            this.dataTracker.set(ATTACK_TICKS, this.serverAttackTicks);
            tickCurrentAttack(target);
        } else {
            this.dataTracker.set(ATTACK_TICKS, 0);
            tickGroundCombat(target);
        }

        updateTrackedVisualStateServer();
    }

    private void tickCooldowns() {
        if (this.laserCooldownTicks > 0) this.laserCooldownTicks--;
        if (this.flightCooldownTicks > 0) this.flightCooldownTicks--;
        if (this.kickCooldownTicks > 0) this.kickCooldownTicks--;
        if (this.breakerCooldownTicks > 0) this.breakerCooldownTicks--;
        if (this.slideCooldownTicks > 0) this.slideCooldownTicks--;
        if (this.crashCooldownTicks > 0) this.crashCooldownTicks--;
        if (this.chillCooldownTicks > 0) this.chillCooldownTicks--;
        if (this.comboCooldownTicks > 0) this.comboCooldownTicks--;
        if (this.attackRecoveryTicks > 0) this.attackRecoveryTicks--;
        if (this.idleRetargetTicks > 0) this.idleRetargetTicks--;
    }

    private void updateTrackedVisualStateServer() {
        float pitch = 0.0f;
        float roll = 0.0f;
        if (isAirbornePose()) {
            Vec3d v = this.getVelocity();
            double flat = Math.max(0.001D, Math.sqrt(v.x * v.x + v.z * v.z));
            pitch = (float) -MathHelper.clamp(Math.atan2(v.y, flat), -1.20D, 1.00D);
            float yawDelta = MathHelper.wrapDegrees(this.getYaw() - this.prevYaw);
            roll = MathHelper.clamp(-yawDelta * 0.03f, -0.42f, 0.42f);
        }
        this.dataTracker.set(FLIGHT_POSE_PITCH, pitch);
        this.dataTracker.set(FLIGHT_POSE_ROLL, roll);
        this.dataTracker.set(METEOR_TICKS, this.meteorTicksLeft);
    }

    private void tickGroundCombat(@Nullable LivingEntity target) {
        this.setNoGravity(false);
        if (this.meteorTargetUuid == null) {
            this.dataTracker.set(METEOR_TARGET_ID, -1);
        }

        if (target == null) {
            this.getNavigation().stop();
            return;
        }

        this.getLookControl().lookAt(target, 25.0f, 20.0f);
        double distSq = this.squaredDistanceTo(target);
        if (distSq > WALK_STOP_RANGE * WALK_STOP_RANGE) {
            this.getNavigation().startMovingTo(target, WALK_SPEED);
        } else {
            this.getNavigation().stop();
        }

        if (!this.canSee(target) || this.attackRecoveryTicks > 0) {
            return;
        }

        selectAndStartBestAttack(target, distSq);
    }

    private void selectAndStartBestAttack(LivingEntity target, double distSq) {
        boolean close = distSq <= MELEE_RANGE * MELEE_RANGE;

        if (close && this.kickCooldownTicks <= 0) {
            startKickAttack(target);
            return;
        }

        List<WeightedAttack> bigReady = new ArrayList<>();
        if (distSq <= 9.0D * 9.0D && this.comboCooldownTicks <= 0) {
            bigReady.add(new WeightedAttack(DragonessAttackType.COMBO_DASH_STANCE, WEIGHT_COMBO));
        }
        if (countNearbyPlayers(8.0D) >= 2 && this.breakerCooldownTicks <= 0) {
            bigReady.add(new WeightedAttack(DragonessAttackType.BREAKER, WEIGHT_BREAKER));
        }
        if (this.chillCooldownTicks <= 0 && this.getHealth() <= this.getMaxHealth() * 0.65f && countNearbyPlayers(20.0D) > 0) {
            bigReady.add(new WeightedAttack(DragonessAttackType.CHILL_STANCE, WEIGHT_CHILL));
        }

        if (!bigReady.isEmpty()) {
            DragonessAttackType chosen = pickWeighted(bigReady);
            if (chosen == DragonessAttackType.COMBO_DASH_STANCE) startComboAttack(target);
            else if (chosen == DragonessAttackType.BREAKER) startBreakerAttack(target);
            else if (chosen == DragonessAttackType.CHILL_STANCE) startChillAttack();
            return;
        }

        List<WeightedAttack> regularPool = new ArrayList<>();
        if (distSq <= 18.0D * 18.0D && this.crashCooldownTicks <= 0) {
            regularPool.add(new WeightedAttack(DragonessAttackType.CRASH_DOWN, WEIGHT_CRASH));
        }
        if (distSq <= 16.0D * 16.0D && this.slideCooldownTicks <= 0) {
            regularPool.add(new WeightedAttack(DragonessAttackType.SLIDE_DASH_STANCE, WEIGHT_SLIDE));
        }
        if (distSq <= FLIGHT_START_RANGE * FLIGHT_START_RANGE && this.flightCooldownTicks <= 0 && !target.hasVehicle() && this.isOnGround()) {
            regularPool.add(new WeightedAttack(DragonessAttackType.FLIGHT_STANCE, WEIGHT_FLIGHT));
        }
        if (distSq <= LASER_TARGET_RANGE * LASER_TARGET_RANGE && this.laserCooldownTicks <= 0) {
            regularPool.add(new WeightedAttack(DragonessAttackType.LASER, WEIGHT_LASER));
        }

        if (regularPool.isEmpty()) {
            return;
        }

        DragonessAttackType chosen = pickWeighted(regularPool);
        if (chosen == DragonessAttackType.CRASH_DOWN) startCrashAttack(target);
        else if (chosen == DragonessAttackType.SLIDE_DASH_STANCE) startSlideDashAttack(target);
        else if (chosen == DragonessAttackType.FLIGHT_STANCE) startFlightAttack(target);
        else if (chosen == DragonessAttackType.LASER) startLaserAttack(target);
    }

    private DragonessAttackType pickWeighted(List<WeightedAttack> pool) {
        int total = 0;
        for (WeightedAttack attack : pool) total += Math.max(0, attack.weight);
        if (total <= 0) return pool.get(0).type;

        int roll = this.random.nextInt(total);
        int running = 0;
        for (WeightedAttack attack : pool) {
            running += Math.max(0, attack.weight);
            if (roll < running) return attack.type;
        }
        return pool.get(pool.size() - 1).type;
    }

    private static final class WeightedAttack {
        private final DragonessAttackType type;
        private final int weight;

        private WeightedAttack(DragonessAttackType type, int weight) {
            this.type = type;
            this.weight = weight;
        }
    }

    private void tickCurrentAttack(@Nullable LivingEntity target) {
        switch (getAttackType()) {
            case LASER -> tickLaserAttack(target);
            case FLIGHT_STANCE -> tickFlightStance(target);
            case FLIGHT_LOOP -> tickFlightLoop(target);
            case FLIGHT_THROW -> tickFlightThrow();
            case KICKS -> tickKickAttack(target);
            case BREAKER -> tickBreakerAttack(target);
            case BREAKER_BACKFLIP -> tickBreakerBackflip();
            case SLIDE_DASH_STANCE -> tickSlideDashStance();
            case SLIDE_DASH -> tickSlideDashLoop();
            case CRASH_DOWN -> tickCrashAttack(target);
            case CHILL_STANCE -> tickChillStance();
            case CHILL_LOOP -> tickChillLoop();
            case CHILL_DISTURBED -> tickChillEnd();
            case COMBO_DASH_STANCE -> tickComboDash(target);
            case COMBO_HIT1 -> tickComboHit1();
            case COMBO_HIT2 -> tickComboHit2();
            case COMBO_FLY_DOWN -> tickComboFlyDown();
            default -> finishAttack(false);
        }
    }

    private void startLaserAttack(@Nullable LivingEntity target) {
        commonAttackStart(DragonessAttackType.LASER);
        this.laserCooldownTicks = LASER_COOLDOWN;
        List<Vec3d> strikes = snapshotLaserTargets(target);
        setLaserTargetCenters(strikes);
        Vec3d center = strikes.isEmpty() ? chooseLaserCenter(target) : strikes.get(0);
        this.dataTracker.set(LASER_CENTER_X, (float) center.x);
        this.dataTracker.set(LASER_CENTER_Y, (float) center.y);
        this.dataTracker.set(LASER_CENTER_Z, (float) center.z);
        this.dataTracker.set(LASER_SEED, this.random.nextInt());
        this.playSound(SoundEvents.BLOCK_BEACON_ACTIVATE, 2.2f, 0.8f);
    }

    private void tickLaserAttack(@Nullable LivingEntity target) {
        stopSelf();
        if (target != null) {
            this.getLookControl().lookAt(target, 35.0f, 20.0f);
        }
        if (this.serverAttackTicks == LASER_CAST_TICK) {
            performLaserStrike();
        }
        if (this.serverAttackTicks >= LASER_TOTAL_TICKS) {
            finishAttack(false);
        }
    }

    private void startFlightAttack(@Nullable LivingEntity target) {
        commonAttackStart(DragonessAttackType.FLIGHT_STANCE);
        this.flightCooldownTicks = FLIGHT_COOLDOWN;
        this.flightChaseTicks = 0;
        this.flightAscendTicks = 0;
        this.carriedTargetUuid = null;
        if (target != null) this.setTarget(target);
        this.playSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 2.3f, 1.15f);
    }

    private void tickFlightStance(@Nullable LivingEntity target) {
        stopSelf();
        if (target != null) this.getLookControl().lookAt(target, 40.0f, 25.0f);
        if (this.serverAttackTicks >= FLIGHT_LIFT_TICK) {
            this.setNoGravity(true);
            if (target != null) {
                Vec3d flat = flattenNormalized(target.getPos().subtract(this.getPos()));
                this.setVelocity(flat.x * 0.24D, 0.42D, flat.z * 0.24D);
                this.velocityModified = true;
            }
        }
        if (this.serverAttackTicks >= FLIGHT_STANCE_TOTAL_TICKS) {
            setAttackType(DragonessAttackType.FLIGHT_LOOP);
            this.serverAttackTicks = 0;
            this.dataTracker.set(ATTACK_TICKS, 0);
        }
    }

    private void tickFlightLoop(@Nullable LivingEntity target) {
        this.setNoGravity(true);
        this.getNavigation().stop();
        LivingEntity carried = getCarriedLiving();
        if (carried != null) {
            this.flightAscendTicks++;
            applyPowerless(carried, 10);
            double desiredY = findGroundY(this.getX(), this.getY(), this.getZ()) + 28.0D;
            Vec3d vel = this.getVelocity().lerp(new Vec3d(0.0D, ASCEND_SPEED, 0.0D), 0.18D);
            if (this.getY() >= desiredY || this.flightAscendTicks >= FLIGHT_ASCEND_TICKS) {
                startThrowPhase();
                return;
            }
            this.setVelocity(vel);
            this.velocityModified = true;
            return;
        }
        if (target == null) {
            finishAttack(true);
            return;
        }
        this.flightChaseTicks++;
        if (this.flightChaseTicks > FLIGHT_CHASE_LIMIT) {
            finishAttack(true);
            return;
        }

        Vec3d targetPoint = target.getPos().add(0.0D, target.getHeight() * 0.65D, 0.0D);
        Vec3d desiredDir = targetPoint.subtract(this.getPos());
        if (desiredDir.lengthSquared() < 1.0E-6D) desiredDir = this.getRotationVec(1.0f);
        desiredDir = desiredDir.normalize();
        Vec3d newVel = this.getVelocity().lerp(desiredDir.multiply(FLIGHT_SPEED), 0.26D);
        this.setVelocity(newVel);
        this.velocityModified = true;
        faceDirection(newVel);

        if (this.flightChaseTicks <= FLIGHT_GRAB_WINDOW_TICKS) {
            if (canGrabFlightTarget(target, desiredDir) && this.squaredDistanceTo(target) <= FLIGHT_GRAB_RANGE * FLIGHT_GRAB_RANGE) {
                grabTarget(target);
                return;
            }
        } else {
            finishAttack(true);
        }
    }

    private void startThrowPhase() {
        setAttackType(DragonessAttackType.FLIGHT_THROW);
        this.serverAttackTicks = 0;
        this.dataTracker.set(ATTACK_TICKS, 0);
        this.playSound(SoundEvents.ITEM_TRIDENT_RIPTIDE_3, 1.6f, 0.9f);
    }

    private void tickFlightThrow() {
        this.setNoGravity(true);
        this.getNavigation().stop();
        this.setVelocity(this.getVelocity().multiply(0.92D));
        this.velocityModified = true;
        LivingEntity carried = getCarriedLiving();
        if (this.serverAttackTicks == FLIGHT_THROW_RELEASE_TICK && carried != null) {
            throwMeteorTarget(carried);
        }
        if (this.serverAttackTicks >= FLIGHT_THROW_TOTAL_TICKS) {
            snapToGround();
            finishAttack(false);
        }
    }

    private void startKickAttack(@Nullable LivingEntity target) {
        commonAttackStart(DragonessAttackType.KICKS);
        this.kickCooldownTicks = KICK_COOLDOWN;
        if (target != null) this.setTarget(target);
        this.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.9f, 0.75f);
    }

    private void tickKickAttack(@Nullable LivingEntity target) {
        this.setNoGravity(false);
        this.getNavigation().stop();
        if (target != null) this.getLookControl().lookAt(target, 40.0f, 20.0f);
        if (this.serverAttackTicks >= KICK_START_TICK && this.serverAttackTicks <= KICK_END_TICK && target != null) {
            Vec3d to = flattenNormalized(target.getPos().subtract(this.getPos()));
            this.setVelocity(to.x * 0.24D, this.getVelocity().y, to.z * 0.24D);
            this.velocityModified = true;
            faceDirection(to);
            if (this.serverAttackTicks % KICK_HIT_INTERVAL == 0) {
                Box hitBox = this.getBoundingBox().expand(1.4D, 1.0D, 1.4D).offset(to.multiply(1.3D));
                for (LivingEntity living : this.getWorld().getEntitiesByClass(LivingEntity.class, hitBox, e -> e.isAlive() && e != this)) {
                    if (living == target || living.squaredDistanceTo(this) <= (MELEE_RANGE * MELEE_RANGE)) {
                        living.damage(this.getDamageSources().mobAttack(this), KICK_TICK_DAMAGE);
                        living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 12, 4, false, true, true));
                        living.addVelocity(to.x * 0.22D, 0.08D, to.z * 0.22D);
                        living.velocityModified = true;
                    }
                }
                this.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.1f, 1.15f);
            }
        }
        if (this.serverAttackTicks >= KICK_TOTAL_TICKS) finishAttack(false);
    }

    private void startBreakerAttack(@Nullable LivingEntity target) {
        commonAttackStart(DragonessAttackType.BREAKER);
        this.breakerCooldownTicks = BREAKER_COOLDOWN;
        this.breakerCrashTargetUuid = target != null ? target.getUuid() : null;
        this.setNoGravity(true);
        this.setVelocity(0.0D, 1.24D, 0.0D);
        this.velocityModified = true;
        breakerWaveBlast();
    }

    private void tickBreakerAttack(@Nullable LivingEntity target) {
        this.setNoGravity(true);
        this.getNavigation().stop();

        if (this.serverAttackTicks < BREAKER_DIVE_TICK) {
            double desiredY = findGroundY(this.getX(), this.getY() + 12.0D, this.getZ()) + BREAKER_ASCEND_HEIGHT;
            double vy = MathHelper.clamp((desiredY - this.getY()) * 0.14D, -0.10D, 1.12D);
            this.setVelocity(this.getVelocity().x * 0.84D, vy, this.getVelocity().z * 0.84D);
            this.velocityModified = true;
        }

        if (this.serverAttackTicks == BREAKER_SUMMON_TICK) {
            spawnBreakerOutermen();
        }

        if (this.serverAttackTicks >= BREAKER_DIVE_TICK) {
            LivingEntity diveTarget = getEntityByUuid(this.breakerCrashTargetUuid, LivingEntity.class);
            if (diveTarget == null && target != null) diveTarget = target;
            Vec3d diveAt = diveTarget != null ? diveTarget.getPos().add(0.0D, 0.2D, 0.0D) : this.getPos().add(0.0D, -10.0D, 0.0D);
            Vec3d dir = diveAt.subtract(this.getPos());
            if (dir.lengthSquared() < 1.0E-6D) dir = new Vec3d(0.0D, -1.0D, 0.0D);
            dir = dir.normalize();
            this.setVelocity(dir.x * 1.05D, Math.min(dir.y * 3.10D, -1.95D), dir.z * 1.05D);
            this.velocityModified = true;
            if (this.isOnGround() || this.verticalCollision) {
                doSelfCrashExplosion(BREAKER_CRASH_POWER, 7.5f);
                setAttackType(DragonessAttackType.BREAKER_BACKFLIP);
                this.serverAttackTicks = 0;
                this.dataTracker.set(ATTACK_TICKS, 0);
                this.setNoGravity(false);
            }
        }
    }

    private void tickBreakerBackflip() {
        stopSelf();
        this.setNoGravity(false);
        if (this.serverAttackTicks >= BREAKER_BACKFLIP_TICKS) finishAttack(false);
    }

    private void startSlideDashAttack(@Nullable LivingEntity target) {
        commonAttackStart(DragonessAttackType.SLIDE_DASH_STANCE);
        this.slideCooldownTicks = SLIDE_COOLDOWN;
        if (target != null) {
            faceDirection(flattenNormalized(target.getPos().subtract(this.getPos())));
        }
        Vec3d forward = flattenNormalized(this.getRotationVec(1.0f));
        if (forward.lengthSquared() < 1.0E-6D) forward = new Vec3d(0.0D, 0.0D, 1.0D);
        this.slideDashDirection = forward;
    }

    private void tickSlideDashStance() {
        stopSelf();
        if (this.serverAttackTicks >= SLIDE_STANCE_TICKS) {
            setAttackType(DragonessAttackType.SLIDE_DASH);
            this.serverAttackTicks = 0;
            this.dataTracker.set(ATTACK_TICKS, 0);
        }
    }

    private void tickSlideDashLoop() {
        this.setNoGravity(false);
        Vec3d dir = this.slideDashDirection != null ? this.slideDashDirection : flattenNormalized(this.getRotationVec(1.0f));
        this.setVelocity(dir.x * SLIDE_DASH_SPEED, this.getVelocity().y, dir.z * SLIDE_DASH_SPEED);
        this.velocityModified = true;
        breakBlocksAhead(dir, 2, false);
        damageDashBox(dir, SLIDE_DAMAGE, 0.25D);
        if (this.serverAttackTicks >= SLIDE_DASH_TICKS) finishAttack(false);
    }

    private void startCrashAttack(@Nullable LivingEntity target) {
        commonAttackStart(DragonessAttackType.CRASH_DOWN);
        this.crashCooldownTicks = CRASH_COOLDOWN;
        this.crashTargetUuid = target != null ? target.getUuid() : null;
        Vec3d crashCenter = target != null ? snapLaserCenter(this.getWorld(), target.getPos()) : snapLaserCenter(this.getWorld(), this.getPos().add(this.getRotationVec(1.0f).multiply(10.0D)));
        setDiveIndicatorCenter(crashCenter);
        this.setNoGravity(true);
        this.setVelocity(0.0D, 1.42D, 0.0D);
        this.velocityModified = true;
    }

    private void tickCrashAttack(@Nullable LivingEntity target) {
        this.setNoGravity(true);
        this.getNavigation().stop();
        if (this.serverAttackTicks < CRASH_DIVE_TICK) {
            double desiredY = findGroundY(this.getX(), this.getY() + 12.0D, this.getZ()) + CRASH_ASCEND_HEIGHT;
            double vy = MathHelper.clamp((desiredY - this.getY()) * 0.13D, -0.10D, 1.20D);
            this.setVelocity(this.getVelocity().x * 0.84D, vy, this.getVelocity().z * 0.84D);
            this.velocityModified = true;
            return;
        }

        Vec3d diveAt = getDiveIndicatorCenter();
        if (diveAt.y < -9990.0D) {
            LivingEntity diveTarget = getEntityByUuid(this.crashTargetUuid, LivingEntity.class);
            if (diveTarget == null && target != null) diveTarget = target;
            diveAt = diveTarget != null ? snapLaserCenter(this.getWorld(), diveTarget.getPos()) : this.getPos().add(0.0D, -8.0D, 0.0D);
            setDiveIndicatorCenter(diveAt);
        }
        Vec3d dir = diveAt.subtract(this.getPos());
        if (dir.lengthSquared() < 1.0E-6D) dir = new Vec3d(0.0D, -1.0D, 0.0D);
        dir = dir.normalize();
        this.setVelocity(dir.x * 1.35D, Math.min(dir.y * 3.25D, -2.05D), dir.z * 1.35D);
        this.velocityModified = true;
        if (this.isOnGround() || this.verticalCollision) {
            doSelfCrashExplosion(CRASH_EXPLOSION_POWER, 9.5f);
            finishAttack(false);
        }
    }

    private void startChillAttack() {
        commonAttackStart(DragonessAttackType.CHILL_STANCE);
        this.chillProtectorsSpawned = false;
        this.chillLockedYaw = this.getYaw();
        this.setNoGravity(true);
        this.setVelocity(0.0D, 0.42D, 0.0D);
        this.velocityModified = true;
    }

    private void tickChillStance() {
        this.setNoGravity(true);
        lockChillYaw();
        followTargetWhileChilling();
        Vec3d v = this.getVelocity();
        this.setVelocity(v.x * 0.88D, Math.max(v.y * 0.86D, 0.035D), v.z * 0.88D);
        this.velocityModified = true;
        if (this.serverAttackTicks >= CHILL_STANCE_TICKS) {
            if (!this.chillProtectorsSpawned) spawnChillProtectors();
            this.chillProtectorsSpawned = true;
            setAttackType(DragonessAttackType.CHILL_LOOP);
            this.serverAttackTicks = 0;
            this.dataTracker.set(ATTACK_TICKS, 0);
        }
    }

    private void tickChillLoop() {
        this.setNoGravity(true);
        lockChillYaw();
        followTargetWhileChilling();
        if (!this.chillProtectorsSpawned) {
            spawnChillProtectors();
            this.chillProtectorsSpawned = true;
        }
        if (countChillProtectors() <= 0) {
            setAttackType(DragonessAttackType.CHILL_DISTURBED);
            this.serverAttackTicks = 0;
            this.dataTracker.set(ATTACK_TICKS, 0);
            this.setVelocity(0.0D, -0.22D, 0.0D);
            this.velocityModified = true;
        }
    }

    private void tickChillEnd() {
        this.setNoGravity(true);
        lockChillYaw();
        followTargetWhileChilling();
        Vec3d v = this.getVelocity();
        this.setVelocity(v.x * 0.82D, Math.max(v.y - 0.04D, -0.45D), v.z * 0.82D);
        this.velocityModified = true;
        if (this.isOnGround() || this.serverAttackTicks >= CHILL_END_TICKS) {
            this.setNoGravity(false);
            finishAttack(false);
        }
    }

    private void startComboAttack(@Nullable LivingEntity target) {
        commonAttackStart(DragonessAttackType.COMBO_DASH_STANCE);
        this.comboCooldownTicks = COMBO_COOLDOWN;
        this.comboTargetUuid = target != null ? target.getUuid() : null;
        if (target != null) faceDirection(flattenNormalized(target.getPos().subtract(this.getPos())));
        Vec3d forward = flattenNormalized(this.getRotationVec(1.0f));
        if (forward.lengthSquared() < 1.0E-6D) forward = new Vec3d(0.0D, 0.0D, 1.0D);
        this.comboDashDirection = forward;
        this.setNoGravity(true);
    }

    private void tickComboDash(@Nullable LivingEntity target) {
        this.setNoGravity(true);
        if (this.serverAttackTicks < COMBO_DASH_TICKS - 3) {
            stopSelf();
            return;
        }
        Vec3d dir = this.comboDashDirection != null ? this.comboDashDirection : flattenNormalized(this.getRotationVec(1.0f));
        this.setVelocity(dir.x * COMBO_DASH_SPEED, 0.0D, dir.z * COMBO_DASH_SPEED);
        this.velocityModified = true;
        breakBlocksAhead(dir, 2, false);

        LivingEntity victim = findComboVictim(target, dir);
        if (victim != null) {
            this.comboTargetUuid = victim.getUuid();
            setAttackType(DragonessAttackType.COMBO_HIT1);
            this.serverAttackTicks = 0;
            this.dataTracker.set(ATTACK_TICKS, 0);
            stopSelf();
            return;
        }

        if (this.serverAttackTicks >= COMBO_DASH_TICKS + 12) {
            finishAttack(true);
        }
    }

    private void tickComboHit1() {
        this.setNoGravity(true);
        LivingEntity victim = getComboTarget();
        if (victim != null) {
            if (this.serverAttackTicks < COMBO_LAUNCH_TICK) {
                stopSelf();
                suspendVictim(victim);
            } else if (this.serverAttackTicks == COMBO_LAUNCH_TICK) {
                Vec3d away = flattenNormalized(victim.getPos().subtract(this.getPos()));
                if (away.lengthSquared() < 1.0E-6D) away = new Vec3d(0.0D, 0.0D, -1.0D);
                victim.setVelocity(away.x * COMBO_LAUNCH_HORIZONTAL_SPEED, COMBO_LAUNCH_UP_SPEED, away.z * COMBO_LAUNCH_HORIZONTAL_SPEED);
                victim.velocityModified = true;
                victim.fallDistance = 0.0f;
                applyPowerlessEffectOnly(victim, 40);
            }

            if (this.serverAttackTicks >= COMBO_CHASE_START_TICK) {
                Vec3d anchor = victim.getPos().add(0.0D, Math.max(2.2D, victim.getHeight() * 0.7D), 0.0D);
                Vec3d correction = anchor.subtract(this.getPos());
                Vec3d victimVel = victim.getVelocity();
                Vec3d desiredVel;
                if (correction.lengthSquared() > COMBO_CHASE_CLOSE_RANGE * COMBO_CHASE_CLOSE_RANGE) {
                    Vec3d dir = correction.normalize();
                    desiredVel = new Vec3d(victimVel.x, victimVel.y, victimVel.z).add(dir.multiply(COMBO_CHASE_SPEED));
                } else {
                    desiredVel = victimVel;
                }
                this.setVelocity(desiredVel);
                this.velocityModified = true;
                Vec3d face = new Vec3d(desiredVel.x, 0.0D, desiredVel.z);
                if (face.lengthSquared() > 1.0E-6D) faceDirection(face);
                victim.fallDistance = 0.0f;
                applyPowerlessEffectOnly(victim, 6);
            }

            if (this.serverAttackTicks >= COMBO_HIT1_MIN_TICKS
                    && victim.getVelocity().y <= COMBO_PEAK_VERTICAL_THRESHOLD
                    && this.squaredDistanceTo(victim) <= 36.0D) {
                setAttackType(DragonessAttackType.COMBO_HIT2);
                this.serverAttackTicks = 0;
                this.dataTracker.set(ATTACK_TICKS, 0);
                return;
            }
        } else {
            stopSelf();
        }

        if (this.serverAttackTicks >= COMBO_HIT1_TICKS + 24) {
            if (victim != null) {
                setAttackType(DragonessAttackType.COMBO_HIT2);
                this.serverAttackTicks = 0;
                this.dataTracker.set(ATTACK_TICKS, 0);
            } else {
                finishAttack(true);
            }
        }
    }

    private void tickComboHit2() {
        this.setNoGravity(true);
        LivingEntity victim = getComboTarget();
        if (victim != null) {
            Vec3d anchor = victim.getPos().add(0.0D, Math.max(1.8D, victim.getHeight() * 0.65D), 0.0D);
            Vec3d correction = anchor.subtract(this.getPos());
            Vec3d desiredVel = victim.getVelocity();
            if (correction.lengthSquared() > 1.2D) {
                desiredVel = desiredVel.add(correction.normalize().multiply(COMBO_ATTACH_SPEED));
            }
            this.setVelocity(desiredVel);
            this.velocityModified = true;
            victim.fallDistance = 0.0f;
            applyPowerlessEffectOnly(victim, 8);

            if (this.serverAttackTicks == COMBO_SLAM_TICK) {
                Vec3d slamCenter = snapLaserCenter(this.getWorld(), victim.getPos());
                setDiveIndicatorCenter(slamCenter);
                this.comboDiveTarget = slamCenter;
                victim.setVelocity(0.0D, -4.85D, 0.0D);
                victim.velocityModified = true;
                victim.fallDistance = 0.0f;
                applyPowerlessEffectOnly(victim, 36);
            }
        } else {
            stopSelf();
        }

        if (this.serverAttackTicks >= COMBO_HIT2_TICKS) {
            setAttackType(DragonessAttackType.COMBO_FLY_DOWN);
            this.serverAttackTicks = 0;
            this.dataTracker.set(ATTACK_TICKS, 0);
        }
    }

    private void tickComboFlyDown() {
        this.setNoGravity(true);
        LivingEntity victim = getComboTarget();
        Vec3d diveAt = this.comboDiveTarget != null ? this.comboDiveTarget : getDiveIndicatorCenter();
        if (victim != null && victim.getVelocity().y < -0.2D) {
            diveAt = victim.getPos();
        }
        if (diveAt.y < -9990.0D) {
            diveAt = this.getPos().add(0.0D, -8.0D, 0.0D);
        }
        Vec3d dir = diveAt.subtract(this.getPos());
        if (dir.lengthSquared() < 1.0E-6D) dir = new Vec3d(0.0D, -1.0D, 0.0D);
        dir = dir.normalize();
        this.setVelocity(dir.x * 1.15D, Math.min(dir.y * 3.45D, -2.15D), dir.z * 1.15D);
        this.velocityModified = true;
        if (this.isOnGround() || this.verticalCollision) {
            doSelfCrashExplosion(COMBO_FINAL_CRASH_POWER, 12.0f);
            this.comboTargetUuid = null;
            this.comboDiveTarget = null;
            finishAttack(false);
        }
    }

    private void performLaserStrike() {
        List<Vec3d> strikes = getLaserTargetCenters();
        if (strikes.isEmpty()) {
            strikes = snapshotLaserTargets(this.getTarget());
        }
        for (Vec3d pos : strikes) {
            this.getWorld().createExplosion(this, pos.x, pos.y, pos.z, LASER_EXPLOSION_POWER, false, World.ExplosionSourceType.NONE);
            queueImpactFx(pos, LASER_AREA_HALF_SIZE * 2.0f + 0.6f);
        }
        this.playSound(SoundEvents.ENTITY_GENERIC_EXPLODE, 2.4f, 0.78f);
    }

    private void breakerWaveBlast() {
        for (LivingEntity living : this.getWorld().getEntitiesByClass(LivingEntity.class, this.getBoundingBox().expand(BREAKER_WAVE_RADIUS), e -> e.isAlive() && e != this)) {
            Vec3d away = flattenNormalized(living.getPos().subtract(this.getPos()));
            if (away.lengthSquared() < 1.0E-6D) away = new Vec3d(0.0D, 0.0D, 1.0D);
            living.addVelocity(away.x * 1.1D, 0.35D, away.z * 1.1D);
            living.velocityModified = true;
            living.damage(this.getDamageSources().mobAttack(this), 5.0f);
        }
        breakBlocksAround(5, 3, true);
    }

    private void spawnBreakerOutermen() {
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) return;
        Identifier id = new Identifier(Oddities.MOD_ID, "outerman");
        Registries.ENTITY_TYPE.getOrEmpty(id).ifPresent(type -> {
            for (Vec3d pos : computeBreakerSummonCenters(this)) {
                Entity entity = type.create(serverWorld);
                if (entity == null) continue;
                entity.refreshPositionAndAngles(pos.x, pos.y, pos.z, this.random.nextFloat() * 360.0f, 0.0f);
                if (entity instanceof MobEntity mob && this.getTarget() != null) {
                    mob.setTarget(this.getTarget());
                }
                serverWorld.spawnEntity(entity);
            }
        });
    }

    public Vec3d getDashPathStart() {
        Vec3d dir;
        DragonessAttackType type = getAttackType();
        if (type == DragonessAttackType.SLIDE_DASH_STANCE || type == DragonessAttackType.SLIDE_DASH) {
            dir = this.slideDashDirection != null ? this.slideDashDirection : flattenNormalized(this.getRotationVec(1.0f));
        } else if (type == DragonessAttackType.COMBO_DASH_STANCE) {
            dir = this.comboDashDirection != null ? this.comboDashDirection : flattenNormalized(this.getRotationVec(1.0f));
        } else {
            dir = flattenNormalized(this.getRotationVec(1.0f));
        }

        if (dir.lengthSquared() < 1.0E-6D) {
            dir = new Vec3d(0.0D, 0.0D, 1.0D);
        }

        return this.getPos().add(0.0D, 0.06D, 0.0D).add(dir.multiply(1.2D));
    }

    public Vec3d getDashPathEnd() {
        Vec3d start = getDashPathStart();
        DragonessAttackType type = getAttackType();
        Vec3d dir;

        if (type == DragonessAttackType.SLIDE_DASH_STANCE || type == DragonessAttackType.SLIDE_DASH) {
            dir = this.slideDashDirection != null ? this.slideDashDirection : flattenNormalized(this.getRotationVec(1.0f));
        } else if (type == DragonessAttackType.COMBO_DASH_STANCE) {
            dir = this.comboDashDirection != null ? this.comboDashDirection : flattenNormalized(this.getRotationVec(1.0f));
        } else {
            dir = flattenNormalized(this.getRotationVec(1.0f));
        }

        if (dir.lengthSquared() < 1.0E-6D) {
            dir = new Vec3d(0.0D, 0.0D, 1.0D);
        }

        double length = type == DragonessAttackType.COMBO_DASH_STANCE ? 11.0D : 15.0D;
        return start.add(dir.multiply(length));
    }

    public List<Vec3d> getLaserTargetCenters() {
        return nbtToVecList(this.dataTracker.get(LASER_TARGETS));
    }

    private void setLaserTargetCenters(List<Vec3d> points) {
        this.dataTracker.set(LASER_TARGETS, vecListToNbt(points));
    }

    public List<Vec3d> getBreakerSummonCenters() {
        return computeBreakerSummonCenters(this);
    }

    public Vec3d getDiveIndicatorCenter() {
        return new Vec3d(this.dataTracker.get(DIVE_TARGET_X), this.dataTracker.get(DIVE_TARGET_Y), this.dataTracker.get(DIVE_TARGET_Z));
    }

    public static List<Vec3d> computeBreakerSummonCenters(DragonessEntity dragoness) {
        List<Vec3d> out = new ArrayList<>();
        Vec3d base = dragoness.getPos();
        for (int i = 0; i < 6; i++) {
            double angle = i * (Math.PI * 2.0D / 6.0D) + (dragoness.getAttackSerial() * 0.17D);
            double radius = 4.0D + (i % 2 == 0 ? 1.25D : 0.45D);
            double x = base.x + Math.cos(angle) * radius;
            double z = base.z + Math.sin(angle) * radius;
            double y = dragoness.findGroundStatic(x, base.y + 8.0D, z) + 0.06D;
            out.add(new Vec3d(x, y, z));
        }
        return out;
    }

    private void spawnChillProtectors() {
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) return;
        Identifier id = new Identifier(Oddities.MOD_ID, "ufo_protector");
        Registries.ENTITY_TYPE.getOrEmpty(id).ifPresent(type -> {
            for (int i = 0; i < CHILL_UFO_COUNT; i++) {
                Entity entity = type.create(serverWorld);
                if (entity instanceof UfoProtectorEntity protector) {
                    protector.setParent(this, i, CHILL_UFO_COUNT);
                    protector.refreshPositionAndAngles(this.getX(), this.getY() + 3.0D, this.getZ(), 0.0f, 0.0f);
                    serverWorld.spawnEntity(protector);
                } else if (entity != null) {
                    entity.refreshPositionAndAngles(this.getX(), this.getY() + 3.0D, this.getZ(), 0.0f, 0.0f);
                    serverWorld.spawnEntity(entity);
                }
            }
        });
    }

    private int countChillProtectors() {
        return this.getWorld().getEntitiesByClass(UfoProtectorEntity.class, this.getBoundingBox().expand(48.0D), e -> e.isAlive() && e.hasParent(this.getUuid())).size();
    }

    private void grabTarget(LivingEntity target) {
        if (target.hasVehicle()) return;
        this.carriedTargetUuid = target.getUuid();
        target.startRiding(this, true);
        target.stopUsingItem();
        target.setSneaking(false);
        applyPowerless(target, 10);
        this.flightAscendTicks = 0;
        this.playSound(SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 1.6f, 1.0f);
    }

    private boolean canGrabFlightTarget(LivingEntity target, Vec3d travelDir) {
        if (!target.isAlive() || target.hasVehicle()) return false;

        Vec3d toTarget = target.getPos().add(0.0D, target.getHeight() * 0.55D, 0.0D).subtract(this.getPos());
        if (Math.abs(toTarget.y) > FLIGHT_GRAB_VERTICAL_TOLERANCE) return false;

        Vec3d dir = travelDir.lengthSquared() > 1.0E-6D ? travelDir.normalize() : flattenNormalized(this.getRotationVec(1.0f));
        if (dir.lengthSquared() < 1.0E-6D) dir = new Vec3d(0.0D, 0.0D, 1.0D);

        Vec3d toNorm = toTarget.normalize();
        if (dir.dotProduct(toNorm) < FLIGHT_GRAB_FRONT_DOT) return false;

        Box lungeBox = this.getBoundingBox().stretch(dir.multiply(2.2D)).expand(0.8D, 1.3D, 0.8D);
        return lungeBox.intersects(target.getBoundingBox());
    }

    private void throwMeteorTarget(LivingEntity target) {
        Vec3d releasePos = getCarryPosition().add(0.0D, 0.55D, 0.0D);
        LivingEntity redirect = chooseAlternateThrowTarget(target);
        Vec3d destination = redirect != null ? redirect.getPos().add(0.0D, redirect.getHeight() * 0.35D, 0.0D)
                : releasePos.add(this.getRotationVec(1.0f).multiply(8.0D)).add(0.0D, -24.0D, 0.0D);
        Vec3d delta = destination.subtract(releasePos);
        Vec3d flat = flattenNormalized(delta);
        if (flat.lengthSquared() < 1.0E-6D) flat = flattenNormalized(this.getRotationVec(1.0f));
        if (flat.lengthSquared() < 1.0E-6D) flat = new Vec3d(0.0D, 0.0D, 1.0D);
        double horizontalSpeed = redirect != null ? THROW_SPEED : THROW_SPEED * 0.75D;
        double downwardSpeed = -1.28D + MathHelper.clamp(delta.y * 0.03D, -0.30D, 0.20D);
        Vec3d velocity = new Vec3d(flat.x * horizontalSpeed, downwardSpeed, flat.z * horizontalSpeed);
        target.stopRiding();
        target.refreshPositionAndAngles(releasePos.x, releasePos.y, releasePos.z, target.getYaw(), target.getPitch());
        target.setVelocity(velocity);
        target.velocityModified = true;
        target.fallDistance = 0.0f;
        this.carriedTargetUuid = null;
        this.meteorTargetUuid = target.getUuid();
        this.meteorTicksLeft = METEOR_MAX_TICKS;
        this.dataTracker.set(METEOR_TARGET_ID, target.getId());
        this.dataTracker.set(METEOR_TICKS, this.meteorTicksLeft);
        applyPowerlessEffectOnly(target, 40);
        this.playSound(SoundEvents.ENTITY_BLAZE_SHOOT, 1.9f, 0.7f);
    }

    private void tickMeteorVictim() {
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) return;
        if (this.meteorTargetUuid == null) {
            this.meteorTicksLeft = 0;
            this.dataTracker.set(METEOR_TARGET_ID, -1);
            return;
        }
        Entity entity = serverWorld.getEntity(this.meteorTargetUuid);
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            clearMeteorTarget();
            return;
        }
        this.meteorTicksLeft = Math.max(0, this.meteorTicksLeft - 1);
        this.dataTracker.set(METEOR_TARGET_ID, living.getId());
        this.dataTracker.set(METEOR_TICKS, this.meteorTicksLeft);
        applyPowerlessEffectOnly(living, 8);
        Vec3d fallVelocity = living.getVelocity();
        if (fallVelocity.y > -2.4D) {
            fallVelocity = fallVelocity.add(0.0D, -0.16D, 0.0D);
        }
        fallVelocity = new Vec3d(fallVelocity.x * 0.985D, fallVelocity.y, fallVelocity.z * 0.985D);
        living.setVelocity(fallVelocity);
        living.velocityModified = true;
        living.fallDistance = 0.0f;
        boolean hitGround = living.isOnGround() || living.horizontalCollision || living.verticalCollision;
        boolean hitPlayer = !this.getWorld().getOtherEntities(living, living.getBoundingBox().expand(0.25D), e -> e instanceof PlayerEntity && e.isAlive()).isEmpty();
        if (hitGround || hitPlayer || this.meteorTicksLeft <= 0) {
            Vec3d hitPos = living.getPos();
            this.getWorld().createExplosion(this, hitPos.x, hitPos.y, hitPos.z, METEOR_EXPLOSION_POWER, false, World.ExplosionSourceType.TNT);
            queueImpactFx(hitPos, 10.0f);
            living.damage(this.getDamageSources().explosion(this, this), 10.0f);
            clearMeteorTarget();
            this.playSound(SoundEvents.ENTITY_GENERIC_EXPLODE, 2.6f, 0.65f);
        }
    }

    private void clearMeteorTarget() {
        this.meteorTargetUuid = null;
        this.meteorTicksLeft = 0;
        this.dataTracker.set(METEOR_TARGET_ID, -1);
        this.dataTracker.set(METEOR_TICKS, 0);
    }

    private void commonAttackStart(DragonessAttackType type) {
        setAttackType(type);
        this.serverAttackTicks = 0;
        this.attackSerial++;
        this.dataTracker.set(ATTACK_SERIAL, this.attackSerial);
        stopSelf();
        this.carriedTargetUuid = null;
        this.comboTargetUuid = null;
        this.comboDiveTarget = null;
    }

    private void finishAttack(boolean snapDown) {
        DragonessAttackType previous = getAttackType();
        this.serverAttackTicks = 0;
        this.dataTracker.set(ATTACK_TICKS, 0);
        setAttackType(DragonessAttackType.NONE);
        this.setNoGravity(false);
        if (snapDown) snapToGround();
        this.carriedTargetUuid = null;
        this.comboTargetUuid = null;
        this.comboDiveTarget = null;
        this.slideDashDirection = null;
        this.comboDashDirection = null;
        clearDiveIndicatorCenter();
        setLaserTargetCenters(List.of());
        if (previous == DragonessAttackType.CHILL_STANCE || previous == DragonessAttackType.CHILL_LOOP || previous == DragonessAttackType.CHILL_DISTURBED) {
            this.chillCooldownTicks = CHILL_COOLDOWN;
        }
        this.attackRecoveryTicks = ATTACK_RECOVERY_TICKS;
    }

    private void stopSelf() {
        this.getNavigation().stop();
        this.setVelocity(Vec3d.ZERO);
        this.velocityModified = true;
    }

    private void snapToGround() {
        double ground = findGroundY(this.getX(), this.getY() + 10.0D, this.getZ());
        this.refreshPositionAndAngles(this.getX(), ground, this.getZ(), this.getYaw(), this.getPitch());
        this.setVelocity(Vec3d.ZERO);
        this.velocityModified = true;
        this.setNoGravity(false);
    }

    private void doSelfCrashExplosion(float power, float radiusFx) {
        Vec3d hitPos = this.getPos();
        this.getWorld().createExplosion(this, hitPos.x, hitPos.y, hitPos.z, power, false, World.ExplosionSourceType.TNT);
        queueImpactFx(hitPos, radiusFx);
        this.playSound(SoundEvents.ENTITY_GENERIC_EXPLODE, 2.4f, 0.7f);
        breakBlocksAround(4, 3, true);
    }

    private void breakBlocksAround(int horizontalRadius, int verticalRadius, boolean includeBelow) {
        if (!(this.getWorld() instanceof ServerWorld world)) return;
        if (!world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) return;
        BlockPos center = this.getBlockPos();
        for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
            for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                for (int dy = includeBelow ? -1 : 0; dy <= verticalRadius; dy++) {
                    if ((dx * dx) + (dz * dz) > horizontalRadius * horizontalRadius) continue;
                    BlockPos pos = center.add(dx, dy, dz);
                    if (pos.getY() <= world.getBottomY()) continue;
                    if (world.getBlockState(pos).isAir()) continue;
                    if (world.getBlockState(pos).getHardness(world, pos) < 0.0f) continue;
                    world.breakBlock(pos, false, this);
                }
            }
        }
    }

    private void breakBlocksAhead(Vec3d dir, int width, boolean includeBelow) {
        if (!(this.getWorld() instanceof ServerWorld world)) return;
        if (!world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) return;
        Vec3d side = new Vec3d(-dir.z, 0.0D, dir.x);
        for (int forward = 1; forward <= 2; forward++) {
            for (int lateral = -width; lateral <= width; lateral++) {
                Vec3d sample = this.getPos().add(dir.multiply(forward + 0.8D)).add(side.multiply(lateral * 0.6D));
                int blockX = MathHelper.floor(sample.x);
                int blockZ = MathHelper.floor(sample.z);
                int baseY = MathHelper.floor(this.getY());
                for (int dy = includeBelow ? -1 : 0; dy <= 2; dy++) {
                    BlockPos pos = new BlockPos(blockX, baseY + dy, blockZ);
                    if (world.getBlockState(pos).isAir()) continue;
                    if (world.getBlockState(pos).getHardness(world, pos) < 0.0f) continue;
                    world.breakBlock(pos, false, this);
                }
            }
        }
    }

    private void damageDashBox(Vec3d dir, float damage, double yBoost) {
        Box hitBox = this.getBoundingBox().expand(1.0D, 0.8D, 1.0D).offset(dir.multiply(1.8D));
        for (LivingEntity living : this.getWorld().getEntitiesByClass(LivingEntity.class, hitBox, e -> e.isAlive() && e != this)) {
            living.damage(this.getDamageSources().mobAttack(this), damage);
            living.addVelocity(dir.x * 0.70D, yBoost, dir.z * 0.70D);
            living.velocityModified = true;
        }
    }

    private LivingEntity findComboVictim(@Nullable LivingEntity fallback, Vec3d dir) {
        Box hitBox = this.getBoundingBox().expand(1.1D, 1.0D, 1.1D).offset(dir.multiply(2.0D));
        List<LivingEntity> hits = this.getWorld().getEntitiesByClass(LivingEntity.class, hitBox, e -> e.isAlive() && e != this);
        if (!hits.isEmpty()) return hits.get(0);
        if (fallback != null && this.squaredDistanceTo(fallback) <= 10.0D * 10.0D) return fallback;
        return null;
    }

    private void suspendVictim(LivingEntity victim) {
        victim.setVelocity(0.0D, 0.0D, 0.0D);
        victim.velocityModified = true;
        victim.fallDistance = 0.0f;
        applyPowerlessEffectOnly(victim, 5);
    }

    private @Nullable LivingEntity getComboTarget() {
        return getEntityByUuid(this.comboTargetUuid, LivingEntity.class);
    }

    private int countNearbyPlayers(double range) {
        return this.getWorld().getEntitiesByClass(PlayerEntity.class, this.getBoundingBox().expand(range), p -> p.isAlive() && !p.isSpectator()).size();
    }

    private @Nullable LivingEntity chooseAlternateThrowTarget(LivingEntity victim) {
        List<PlayerEntity> players = (List<PlayerEntity>) this.getWorld().getPlayers().stream()
                .filter(PlayerEntity::isAlive)
                .filter(player -> !player.isSpectator())
                .filter(player -> !player.getUuid().equals(victim.getUuid()))
                .sorted(Comparator.comparingDouble(this::squaredDistanceTo))
                .toList();
        return players.isEmpty() ? null : players.get(0);
    }

    private @Nullable LivingEntity getCombatTarget() {
        if (this.getTarget() instanceof PlayerEntity player && player.isAlive() && !player.isSpectator()) return player;
        return null;
    }

    private void findNewTarget() {
        if (this.idleRetargetTicks > 0) return;
        this.idleRetargetTicks = 10;
        PlayerEntity nearest = this.getWorld().getClosestPlayer(this, DETECTION_RANGE);
        if (nearest != null && !nearest.isSpectator()) this.setTarget(nearest);
    }

    private Vec3d chooseLaserCenter(@Nullable LivingEntity target) {
        Vec3d anchor = target != null ? target.getPos() : this.getPos();
        return snapLaserCenter(this.getWorld(), anchor);
    }

    public static List<Vec3d> computeLaserTargetCenters(World world, DragonessEntity dragoness, boolean includeFallback) {
        List<Vec3d> tracked = dragoness.getLaserTargetCenters();
        if (!tracked.isEmpty()) {
            return tracked;
        }
        return includeFallback ? dragoness.snapshotLaserTargets(dragoness.getTarget()) : List.of();
    }

    private static Vec3d snapLaserCenter(World world, Vec3d around) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int x = MathHelper.floor(around.x);
        int z = MathHelper.floor(around.z);
        int startY = MathHelper.clamp(MathHelper.floor(around.y) + 4, world.getBottomY(), world.getTopY() - 1);
        int minY = Math.max(world.getBottomY(), MathHelper.floor(around.y) - 12);
        for (int y = startY; y >= minY; y--) {
            pos.set(x, y, z);
            if (!world.getBlockState(pos).isSolidBlock(world, pos)) continue;
            BlockPos above = pos.up();
            if (world.getBlockState(above).isSolidBlock(world, above)) continue;
            return new Vec3d(x + 0.5D, y + 1.06D, z + 0.5D);
        }
        BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, startY, z));
        return new Vec3d(top.getX() + 0.5D, top.getY() + 0.06D, top.getZ() + 0.5D);
    }

    public static float getLaserAreaHalfSize() {
        return LASER_AREA_HALF_SIZE;
    }

    private List<Vec3d> snapshotLaserTargets(@Nullable LivingEntity preferred) {
        List<Vec3d> out = new ArrayList<>();
        List<PlayerEntity> candidates = (List<PlayerEntity>) this.getWorld().getPlayers().stream()
                .filter(PlayerEntity::isAlive)
                .filter(player -> !player.isSpectator())
                .filter(player -> player.squaredDistanceTo(this) <= LASER_TARGET_RANGE * LASER_TARGET_RANGE)
                .sorted(Comparator.comparingDouble(this::squaredDistanceTo))
                .toList();
        for (PlayerEntity player : candidates) {
            Vec3d snapped = snapLaserCenter(this.getWorld(), player.getPos());
            boolean tooClose = false;
            for (Vec3d existing : out) {
                if (existing.squaredDistanceTo(snapped) < 6.25D) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) out.add(snapped);
            if (out.size() >= 6) break;
        }
        if (out.isEmpty()) {
            out.add(chooseLaserCenter(preferred));
        }
        return out;
    }

    private void setDiveIndicatorCenter(Vec3d center) {
        this.dataTracker.set(DIVE_TARGET_X, (float) center.x);
        this.dataTracker.set(DIVE_TARGET_Y, (float) center.y);
        this.dataTracker.set(DIVE_TARGET_Z, (float) center.z);
    }

    private void clearDiveIndicatorCenter() {
        this.dataTracker.set(DIVE_TARGET_X, 0.0f);
        this.dataTracker.set(DIVE_TARGET_Y, -9999.0f);
        this.dataTracker.set(DIVE_TARGET_Z, 0.0f);
    }

    private static NbtCompound vecListToNbt(List<Vec3d> points) {
        NbtCompound tag = new NbtCompound();
        tag.putInt("count", points.size());
        for (int i = 0; i < points.size(); i++) {
            Vec3d p = points.get(i);
            tag.putDouble("x" + i, p.x);
            tag.putDouble("y" + i, p.y);
            tag.putDouble("z" + i, p.z);
        }
        return tag;
    }

    private static List<Vec3d> nbtToVecList(NbtCompound tag) {
        List<Vec3d> out = new ArrayList<>();
        int count = tag.getInt("count");
        for (int i = 0; i < count; i++) {
            out.add(new Vec3d(tag.getDouble("x" + i), tag.getDouble("y" + i), tag.getDouble("z" + i)));
        }
        return out;
    }

    public Vec3d getLaserCenter() {
        return new Vec3d(this.dataTracker.get(LASER_CENTER_X), this.dataTracker.get(LASER_CENTER_Y), this.dataTracker.get(LASER_CENTER_Z));
    }

    public int getLaserSeed() { return this.dataTracker.get(LASER_SEED); }
    public int getAttackTicks() { return this.dataTracker.get(ATTACK_TICKS); }
    public int getAttackSerial() { return this.dataTracker.get(ATTACK_SERIAL); }
    public float getFlightPosePitchRad() { return this.dataTracker.get(FLIGHT_POSE_PITCH); }
    public float getFlightPoseRollRad() { return this.dataTracker.get(FLIGHT_POSE_ROLL); }
    public int getMeteorTargetId() { return this.dataTracker.get(METEOR_TARGET_ID); }
    public int getMeteorTicks() { return this.dataTracker.get(METEOR_TICKS); }
    public int getImpactSerial() { return this.dataTracker.get(IMPACT_SERIAL); }
    public Vec3d getImpactCenter() { return new Vec3d(this.dataTracker.get(IMPACT_X), this.dataTracker.get(IMPACT_Y), this.dataTracker.get(IMPACT_Z)); }
    public float getImpactRadius() { return this.dataTracker.get(IMPACT_RADIUS); }

    public DragonessAttackType getAttackType() {
        int idx = MathHelper.clamp(this.dataTracker.get(ATTACK_TYPE), 0, DragonessAttackType.values().length - 1);
        return DragonessAttackType.values()[idx];
    }

    private void setAttackType(DragonessAttackType attackType) {
        this.dataTracker.set(ATTACK_TYPE, attackType.ordinal());
    }

    public boolean isAirbornePose() {
        DragonessAttackType type = getAttackType();
        return type != DragonessAttackType.CHILL_STANCE
                && type != DragonessAttackType.CHILL_LOOP
                && type != DragonessAttackType.CHILL_DISTURBED
                && type.airborne();
    }

    public boolean shouldLockHeadLook() {
        DragonessAttackType type = getAttackType();
        return type == DragonessAttackType.FLIGHT_STANCE
                || type == DragonessAttackType.FLIGHT_LOOP
                || type == DragonessAttackType.FLIGHT_THROW
                || type == DragonessAttackType.BREAKER
                || type == DragonessAttackType.CRASH_DOWN
                || type == DragonessAttackType.CHILL_STANCE
                || type == DragonessAttackType.CHILL_LOOP
                || type == DragonessAttackType.CHILL_DISTURBED
                || type == DragonessAttackType.COMBO_DASH_STANCE
                || type == DragonessAttackType.COMBO_HIT1
                || type == DragonessAttackType.COMBO_HIT2
                || type == DragonessAttackType.COMBO_FLY_DOWN;
    }

    private void lockChillYaw() {
        this.setYaw(this.chillLockedYaw);
        this.bodyYaw = this.chillLockedYaw;
        this.headYaw = this.chillLockedYaw;
        this.prevYaw = this.chillLockedYaw;
        this.prevBodyYaw = this.chillLockedYaw;
        this.prevHeadYaw = this.chillLockedYaw;
    }

    private void followTargetWhileChilling() {
        LivingEntity target = getCombatTarget();
        if (target == null) {
            return;
        }
        Vec3d desiredCenter = target.getPos().add(0.0D, 0.0D, 0.0D);
        Vec3d delta = desiredCenter.subtract(this.getPos());
        Vec3d flat = new Vec3d(delta.x, 0.0D, delta.z);
        Vec3d v = this.getVelocity();
        if (flat.lengthSquared() > CHILL_FOLLOW_STOP_RANGE * CHILL_FOLLOW_STOP_RANGE) {
            Vec3d dir = flat.normalize();
            this.setVelocity(dir.x * CHILL_FOLLOW_SPEED, v.y, dir.z * CHILL_FOLLOW_SPEED);
            this.velocityModified = true;
        } else {
            this.setVelocity(v.x * 0.85D, v.y, v.z * 0.85D);
            this.velocityModified = true;
        }
    }

    private void applyPowerless(LivingEntity living, int duration) {
        Vec3d velocity = living.getVelocity();
        living.setVelocity(velocity.x * 0.15D, velocity.y * 0.15D, velocity.z * 0.15D);
        living.velocityModified = true;
        living.fallDistance = 0.0f;
        addPowerlessEffect(living, duration);
    }

    private void applyPowerlessEffectOnly(LivingEntity living, int duration) {
        living.fallDistance = 0.0f;
        addPowerlessEffect(living, duration);
    }

    private void addPowerlessEffect(LivingEntity living, int duration) {
        Registries.STATUS_EFFECT.getOrEmpty(new Identifier(Oddities.MOD_ID, "powerless")).ifPresent(effect ->
                living.addStatusEffect(new StatusEffectInstance(effect, duration, 0, false, false, true))
        );
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        DragonessAttackType type = getAttackType();
        if (type == DragonessAttackType.CHILL_STANCE || type == DragonessAttackType.CHILL_LOOP) {
            return false;
        }
        return super.damage(source, amount);
    }

    @Override
    public void updatePassengerPosition(Entity passenger, Entity.PositionUpdater positionUpdater) {
        if (this.carriedTargetUuid != null && this.carriedTargetUuid.equals(passenger.getUuid())) {
            Vec3d carry = getCarryPosition();
            positionUpdater.accept(passenger, carry.x, carry.y, carry.z);
            return;
        }
        super.updatePassengerPosition(passenger, positionUpdater);
    }

    private Vec3d getCarryPosition() {
        Vec3d forward = this.getRotationVec(1.0f).normalize();
        return this.getPos().add(forward.multiply(2.2D)).add(0.0D, 3.55D, 0.0D);
    }

    private @Nullable LivingEntity getCarriedLiving() {
        return getEntityByUuid(this.carriedTargetUuid, LivingEntity.class);
    }

    private <T extends Entity> @Nullable T getEntityByUuid(@Nullable UUID uuid, Class<T> type) {
        if (uuid == null) return null;
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) return null;
        Entity entity = serverWorld.getEntity(uuid);
        if (type.isInstance(entity)) return type.cast(entity);
        return null;
    }

    private void faceDirection(Vec3d vec) {
        if (vec.lengthSquared() < 1.0E-6D) return;
        float yaw = (float) (MathHelper.atan2(vec.z, vec.x) * (180.0F / Math.PI)) - 90.0f;
        this.setYaw(yaw);
        this.bodyYaw = yaw;
        this.headYaw = yaw;
        this.prevYaw = yaw;
        this.prevBodyYaw = yaw;
        this.prevHeadYaw = yaw;
    }

    private static Vec3d flattenNormalized(Vec3d vec) {
        Vec3d flat = new Vec3d(vec.x, 0.0D, vec.z);
        if (flat.lengthSquared() < 1.0E-6D) return Vec3d.ZERO;
        return flat.normalize();
    }

    private double findGroundY(double x, double aroundY, double z) {
        BlockPos pos = this.getWorld().getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(MathHelper.floor(x), MathHelper.floor(aroundY), MathHelper.floor(z)));
        return pos.getY();
    }

    private double findGroundStatic(double x, double aroundY, double z) {
        BlockPos pos = this.getWorld().getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(MathHelper.floor(x), MathHelper.floor(aroundY), MathHelper.floor(z)));
        return pos.getY();
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    public boolean hasNoGravity() {
        return getAttackType().airborne()
                || (getAttackType() == DragonessAttackType.FLIGHT_STANCE && this.serverAttackTicks >= FLIGHT_LIFT_TICK)
                || super.hasNoGravity();
    }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean isFireImmune() {
        return true;
    }

    @Override
    public boolean doesRenderOnFire() {
        return false;
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) { return false; }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.ENTITY_ENDER_DRAGON_AMBIENT; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.ENTITY_ENDER_DRAGON_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.ENTITY_ENDER_DRAGON_DEATH; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("AttackType", getAttackType().ordinal());
        nbt.putInt("AttackTicks", this.serverAttackTicks);
        nbt.putInt("AttackSerial", this.attackSerial);
        nbt.putInt("LaserCooldown", this.laserCooldownTicks);
        nbt.putInt("FlightCooldown", this.flightCooldownTicks);
        nbt.putInt("KickCooldown", this.kickCooldownTicks);
        nbt.putInt("BreakerCooldown", this.breakerCooldownTicks);
        nbt.putInt("SlideCooldown", this.slideCooldownTicks);
        nbt.putInt("CrashCooldown", this.crashCooldownTicks);
        nbt.putInt("ChillCooldown", this.chillCooldownTicks);
        nbt.putInt("ComboCooldown", this.comboCooldownTicks);
        nbt.putInt("RecoveryCooldown", this.attackRecoveryTicks);
        nbt.putInt("MeteorTicks", this.meteorTicksLeft);
        if (this.carriedTargetUuid != null) nbt.putUuid("CarriedTarget", this.carriedTargetUuid);
        if (this.meteorTargetUuid != null) nbt.putUuid("MeteorTarget", this.meteorTargetUuid);
        if (this.comboTargetUuid != null) nbt.putUuid("ComboTarget", this.comboTargetUuid);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        int attackIdx = MathHelper.clamp(nbt.getInt("AttackType"), 0, DragonessAttackType.values().length - 1);
        setAttackType(DragonessAttackType.values()[attackIdx]);
        this.serverAttackTicks = nbt.getInt("AttackTicks");
        this.attackSerial = nbt.getInt("AttackSerial");
        this.dataTracker.set(ATTACK_SERIAL, this.attackSerial);
        this.laserCooldownTicks = nbt.getInt("LaserCooldown");
        this.flightCooldownTicks = nbt.getInt("FlightCooldown");
        this.kickCooldownTicks = nbt.getInt("KickCooldown");
        this.breakerCooldownTicks = nbt.getInt("BreakerCooldown");
        this.slideCooldownTicks = nbt.getInt("SlideCooldown");
        this.crashCooldownTicks = nbt.getInt("CrashCooldown");
        this.chillCooldownTicks = nbt.getInt("ChillCooldown");
        this.comboCooldownTicks = nbt.getInt("ComboCooldown");
        this.attackRecoveryTicks = nbt.getInt("RecoveryCooldown");
        this.meteorTicksLeft = nbt.getInt("MeteorTicks");
        this.carriedTargetUuid = nbt.containsUuid("CarriedTarget") ? nbt.getUuid("CarriedTarget") : null;
        this.meteorTargetUuid = nbt.containsUuid("MeteorTarget") ? nbt.getUuid("MeteorTarget") : null;
        this.comboTargetUuid = nbt.containsUuid("ComboTarget") ? nbt.getUuid("ComboTarget") : null;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            switch (getAttackType()) {
                case LASER -> state.setAndContinue(ATTACK_LASER);
                case FLIGHT_STANCE -> state.setAndContinue(STANCE_FLIGHT);
                case FLIGHT_LOOP -> state.setAndContinue(FLIGHT);
                case FLIGHT_THROW -> state.setAndContinue(FLIGHT_TO_THROW);
                case KICKS -> state.setAndContinue(ATTACK_KICKS);
                case BREAKER -> state.setAndContinue(ATTACK_BREAK);
                case BREAKER_BACKFLIP -> state.setAndContinue(BACKFLIP);
                case SLIDE_DASH_STANCE -> state.setAndContinue(STANCE_KICK_DASH);
                case SLIDE_DASH -> state.setAndContinue(KICK_DASH);
                case CRASH_DOWN -> state.setAndContinue(ATTACK_CRASH);
                case CHILL_STANCE -> state.setAndContinue(STANCE_SIT);
                case CHILL_LOOP -> state.setAndContinue(SITTING);
                case CHILL_DISTURBED -> state.setAndContinue(SITTING_DISTURBED);
                case COMBO_DASH_STANCE -> state.setAndContinue(STANCE_COMBO_DASH);
                case COMBO_HIT1 -> state.setAndContinue(COMBO_HIT1);
                case COMBO_HIT2 -> state.setAndContinue(COMBO_HIT2);
                case COMBO_FLY_DOWN -> state.setAndContinue(COMBO_FLY_DOWN);
                case NONE -> {
                    double horizontalSpeedSq = this.getVelocity().x * this.getVelocity().x + this.getVelocity().z * this.getVelocity().z;
                    if (this.isOnGround() && horizontalSpeedSq > 0.010D) state.setAndContinue(WALK);
                    else state.setAndContinue(IDLE);
                }
            }
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return this.cache; }

    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        this.bossBar.addPlayer(player);
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        super.onStoppedTrackingBy(player);
        this.bossBar.removePlayer(player);
    }

    @Override
    public void checkDespawn() {
    }

    private void queueImpactFx(Vec3d pos, float radius) {
        int next = this.dataTracker.get(IMPACT_SERIAL) + 1;
        this.dataTracker.set(IMPACT_SERIAL, next);
        this.dataTracker.set(IMPACT_X, (float) pos.x);
        this.dataTracker.set(IMPACT_Y, (float) pos.y);
        this.dataTracker.set(IMPACT_Z, (float) pos.z);
        this.dataTracker.set(IMPACT_RADIUS, radius);
    }


}
