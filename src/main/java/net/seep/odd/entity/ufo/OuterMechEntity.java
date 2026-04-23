package net.seep.odd.entity.ufo;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.sound.ModSounds;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class OuterMechEntity extends PathAwareEntity implements GeoEntity {
    private static final RawAnimation ANIM_SPAWN             = RawAnimation.begin().thenPlay("spawn");
    private static final RawAnimation ANIM_IDLE              = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation ANIM_WALK              = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ANIM_RUN               = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation ANIM_RIGHT_CANNON      = RawAnimation.begin().thenPlay("right_cannon_fire");
    private static final RawAnimation ANIM_LEFT_CANNON       = RawAnimation.begin().thenPlay("left_cannon_fire");
    private static final RawAnimation ANIM_EXTENDER_OUT      = RawAnimation.begin().thenPlay("extender_out");
    private static final RawAnimation ANIM_EXTENDER_WARM_UP  = RawAnimation.begin().thenPlay("extender_warm_up");
    private static final RawAnimation ANIM_EXTENDER_FIRE     = RawAnimation.begin().thenLoop("extender_fire");

    private static final int ANIM_IDLE_ID = 0;
    private static final int ANIM_WALK_ID = 1;
    private static final int ANIM_SPAWN_ID = 2;
    private static final int ANIM_RIGHT_CANNON_ID = 3;
    private static final int ANIM_EXTENDER_OUT_ID = 4;
    private static final int ANIM_EXTENDER_FIRE_ID = 5;
    private static final int ANIM_RUN_ID = 6;
    private static final int ANIM_EXTENDER_WARM_UP_ID = 7;
    private static final int ANIM_LEFT_CANNON_ID = 8;

    private static final int SPAWN_TICKS = 36;
    private static final int RIGHT_CANNON_TOTAL_TICKS = 49;
    private static final int RIGHT_CANNON_FIRE_TICK = 34;

    private static final int LEFT_CANNON_TOTAL_TICKS = 121;
    private static final int LEFT_CANNON_START_FIRE_TICK = 16;
    private static final int LEFT_CANNON_STOP_FIRE_TICK = 104;

    private static final int EXTENDER_OUT_TICKS = 34;
    private static final int EXTENDER_WARM_UP_TICKS = 67;
    private static final int EXTENDER_FIRE_TICKS = 64;

    private static final int DETECTION_RANGE = 96;
    private static final int RIGHT_CANNON_COOLDOWN_TICKS = 90;
    private static final int LEFT_CANNON_COOLDOWN_TICKS = 120;

    // Easy knob: 40 seconds at 20 TPS.
    private static final int LASER_ATTACK_COOLDOWN_TICKS = 20 * 40;

    private static final double WALK_SPEED = 0.95;
    private static final double RUN_SPEED = 1.85;
    private static final double STOP_RANGE = 18.0;
    private static final double RUN_TRIGGER_RANGE = 30.0;
    private static final double RUN_EXIT_RANGE = 24.0;
    private static final double RIGHT_CANNON_RANGE = 42.0;
    private static final double LEFT_CANNON_RANGE = 40.0;
    private static final double EXTENDER_RANGE = 60.0;

    private static final double BEAM_RANGE = 60.0;
    private static final double BEAM_START_RADIUS = 1.0;
    private static final double BEAM_END_RADIUS = 7.0;
    private static final int BEAM_DAMAGE_INTERVAL = 4;
    private static final float BEAM_DAMAGE = 4.5f;

    private static final float BODY_TURN_IDLE = 7.5f;
    private static final float BODY_TURN_ATTACK = 5.0f;
    private static final float BODY_TURN_EXTENDER = 3.2f;

    private static final TrackedData<Integer> ANIM_STATE =
            DataTracker.registerData(OuterMechEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> LOOK_YAW =
            DataTracker.registerData(OuterMechEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> LOOK_PITCH =
            DataTracker.registerData(OuterMechEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> BEAM_ACTIVE =
            DataTracker.registerData(OuterMechEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> BEAM_END_X =
            DataTracker.registerData(OuterMechEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> BEAM_END_Y =
            DataTracker.registerData(OuterMechEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> BEAM_END_Z =
            DataTracker.registerData(OuterMechEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> BEAM_ALPHA =
            DataTracker.registerData(OuterMechEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> WARMUP_ALPHA =
            DataTracker.registerData(OuterMechEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> EXTEND_YAW =
            DataTracker.registerData(OuterMechEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> TUBE2_PITCH =
            DataTracker.registerData(OuterMechEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private static final TrackedData<Float> BULLET_START_X =
            DataTracker.registerData(OuterMechEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> BULLET_START_Y =
            DataTracker.registerData(OuterMechEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> BULLET_START_Z =
            DataTracker.registerData(OuterMechEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> BULLET_END_X =
            DataTracker.registerData(OuterMechEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> BULLET_END_Y =
            DataTracker.registerData(OuterMechEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> BULLET_END_Z =
            DataTracker.registerData(OuterMechEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> BULLET_ALPHA =
            DataTracker.registerData(OuterMechEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private enum AttackPhase {
        NONE,
        RIGHT_CANNON,
        LEFT_CANNON,
        EXTENDER_OUT,
        EXTENDER_WARM_UP,
        EXTENDER_FIRE
    }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private AttackPhase attackPhase = AttackPhase.NONE;
    private int spawnTicks = SPAWN_TICKS;
    private int phaseTicks = 0;

    private int rightCannonCooldown = 20;
    private int leftCannonCooldown = 50;
    private int extenderCooldown = LASER_ATTACK_COOLDOWN_TICKS;

    private boolean rightCannonFiredThisCycle = false;
    private Vec3d beamEndServer = Vec3d.ZERO;

    public OuterMechEntity(EntityType<? extends OuterMechEntity> type, World world) {
        super(type, world);
        this.ignoreCameraFrustum = true;
        this.setPersistent();
        this.experiencePoints = 40;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 220.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, DETECTION_RANGE)
                .add(EntityAttributes.GENERIC_ARMOR, 10.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(ANIM_STATE, ANIM_IDLE_ID);
        this.dataTracker.startTracking(LOOK_YAW, 0.0f);
        this.dataTracker.startTracking(LOOK_PITCH, 0.0f);
        this.dataTracker.startTracking(BEAM_ACTIVE, false);
        this.dataTracker.startTracking(BEAM_END_X, 0.0f);
        this.dataTracker.startTracking(BEAM_END_Y, 0.0f);
        this.dataTracker.startTracking(BEAM_END_Z, 0.0f);
        this.dataTracker.startTracking(BEAM_ALPHA, 0.0f);
        this.dataTracker.startTracking(WARMUP_ALPHA, 0.0f);
        this.dataTracker.startTracking(EXTEND_YAW, 0.0f);
        this.dataTracker.startTracking(TUBE2_PITCH, 0.0f);

        this.dataTracker.startTracking(BULLET_START_X, 0.0f);
        this.dataTracker.startTracking(BULLET_START_Y, 0.0f);
        this.dataTracker.startTracking(BULLET_START_Z, 0.0f);
        this.dataTracker.startTracking(BULLET_END_X, 0.0f);
        this.dataTracker.startTracking(BULLET_END_Y, 0.0f);
        this.dataTracker.startTracking(BULLET_END_Z, 0.0f);
        this.dataTracker.startTracking(BULLET_ALPHA, 0.0f);
    }

    @Override
    protected void initGoals() {
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true,
                p -> p.isAlive() && !p.isSpectator()));
    }

    public float getLookYawRad() {
        return this.dataTracker.get(LOOK_YAW);
    }

    public float getLookPitchRad() {
        return this.dataTracker.get(LOOK_PITCH);
    }

    public float getExtendYawRad() {
        return this.dataTracker.get(EXTEND_YAW);
    }

    public float getTube2PitchRad() {
        return this.dataTracker.get(TUBE2_PITCH);
    }

    public boolean isExtenderBeamActive() {
        return this.dataTracker.get(BEAM_ACTIVE);
    }

    public Vec3d getBeamEnd() {
        return new Vec3d(
                this.dataTracker.get(BEAM_END_X),
                this.dataTracker.get(BEAM_END_Y),
                this.dataTracker.get(BEAM_END_Z)
        );
    }

    public Vec3d getBulletStart() {
        return new Vec3d(
                this.dataTracker.get(BULLET_START_X),
                this.dataTracker.get(BULLET_START_Y),
                this.dataTracker.get(BULLET_START_Z)
        );
    }

    public Vec3d getBulletEnd() {
        return new Vec3d(
                this.dataTracker.get(BULLET_END_X),
                this.dataTracker.get(BULLET_END_Y),
                this.dataTracker.get(BULLET_END_Z)
        );
    }

    public float getBulletAlpha() {
        return this.dataTracker.get(BULLET_ALPHA);
    }

    public boolean allowMainBodyLook() {
        return this.attackPhase != AttackPhase.EXTENDER_OUT
                && this.attackPhase != AttackPhase.EXTENDER_WARM_UP
                && this.attackPhase != AttackPhase.EXTENDER_FIRE;
    }

    public boolean isWalkAnimating() {
        return this.dataTracker.get(ANIM_STATE) == ANIM_WALK_ID;
    }

    public boolean isRunAnimating() {
        return this.dataTracker.get(ANIM_STATE) == ANIM_RUN_ID;
    }

    public boolean isLaserCharging() {
        return this.attackPhase == AttackPhase.EXTENDER_WARM_UP;
    }

    public boolean isLaserLooping() {
        return this.attackPhase == AttackPhase.EXTENDER_FIRE;
    }

    public float getBeamAlpha() {
        return this.dataTracker.get(BEAM_ALPHA);
    }

    public float getWarmupAlpha() {
        return this.dataTracker.get(WARMUP_ALPHA);
    }

    public Vec3d localToWorld(Vec3d local) {
        float yawRad = -this.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        return this.getPos().add(local.rotateY(yawRad));
    }

    public Vec3d getRightMissileOrigin() {
        return localToWorld(new Vec3d(-62.75 / 16.0, 115.25 / 16.0, -47.5 / 16.0));
    }

    public Vec3d getLeftBulletOrigin() {
        return localToWorld(new Vec3d(64.75 / 16.0, 112.25 / 16.0, -49.75 / 16.0));
    }

    public Vec3d getRailGunOrigin() {
        return localToWorld(new Vec3d(-1.0 / 16.0, 161.0 / 16.0, -14.0 / 16.0));
    }

    @Override
    protected net.minecraft.sound.SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected net.minecraft.sound.SoundEvent getHurtSound(net.minecraft.entity.damage.DamageSource source) {
        return null;
    }

    @Override
    protected net.minecraft.sound.SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient) {
            return;
        }

        if (this.age == 1) {
            playWorldSound(ModSounds.MECH_SPAWN, 2.4f, 1.0f);
        }

        if (this.getTarget() == null || !this.getTarget().isAlive() || this.age % 20 == 0) {
            PlayerEntity nearest = this.getWorld().getClosestPlayer(this, DETECTION_RANGE);
            if (nearest != null && !nearest.isSpectator()) {
                this.setTarget(nearest);
            }
        }

        if (this.rightCannonCooldown > 0) this.rightCannonCooldown--;
        if (this.leftCannonCooldown > 0) this.leftCannonCooldown--;
        if (this.extenderCooldown > 0) this.extenderCooldown--;

        PlayerEntity target = this.getTarget() instanceof PlayerEntity p && p.isAlive() && !p.isSpectator() ? p : null;

        this.dataTracker.set(BULLET_ALPHA, MathHelper.lerp(0.42f, this.getBulletAlpha(), 0.0f));
        this.setBeamAlphaTracked(MathHelper.lerp(0.18f, this.getBeamAlpha(), 0.0f));
        this.setWarmupAlphaTracked(MathHelper.lerp(0.18f, this.getWarmupAlpha(), 0.0f));

        if (this.spawnTicks > 0) {
            this.spawnTicks--;
            this.attackPhase = AttackPhase.NONE;
            this.phaseTicks = 0;
            this.setBeamActive(false);
            this.setBeamAlphaTracked(0.0f);
            this.setWarmupAlphaTracked(0.0f);
            this.relaxAiming();
            this.getNavigation().stop();
            zeroHorizontalMotion();
            this.setAnimState(ANIM_SPAWN_ID);
            return;
        }

        if (target != null) {
            float bodyTurn = (this.attackPhase == AttackPhase.EXTENDER_OUT
                    || this.attackPhase == AttackPhase.EXTENDER_WARM_UP
                    || this.attackPhase == AttackPhase.EXTENDER_FIRE)
                    ? BODY_TURN_EXTENDER
                    : (this.attackPhase == AttackPhase.NONE ? BODY_TURN_IDLE : BODY_TURN_ATTACK);

            faceTargetBody(target, bodyTurn);

            if (allowMainBodyLook()) {
                updateBodyLook(target, this.attackPhase != AttackPhase.NONE);
            } else {
                this.dataTracker.set(LOOK_YAW, MathHelper.lerp(0.30f, this.getLookYawRad(), 0.0f));
                this.dataTracker.set(LOOK_PITCH, MathHelper.lerp(0.30f, this.getLookPitchRad(), 0.0f));
            }

            updateExtenderAim(target);
        } else {
            relaxAiming();
        }

        switch (this.attackPhase) {
            case RIGHT_CANNON -> {
                tickRightCannon(target);
                return;
            }
            case LEFT_CANNON -> {
                tickLeftCannon(target);
                return;
            }
            case EXTENDER_OUT -> {
                tickExtenderOut(target);
                return;
            }
            case EXTENDER_WARM_UP -> {
                tickExtenderWarmUp(target);
                return;
            }
            case EXTENDER_FIRE -> {
                tickExtenderFire(target);
                return;
            }
            case NONE -> {
            }
        }

        this.setBeamActive(false);

        if (target != null) {
            double distSq = this.squaredDistanceTo(target);
            double dist = Math.sqrt(distSq);

            if (dist > RUN_TRIGGER_RANGE) {
                this.getNavigation().startMovingTo(target, RUN_SPEED);
                this.setAnimState(ANIM_RUN_ID);
                return;
            }

            if (dist > STOP_RANGE) {
                this.getNavigation().startMovingTo(target, WALK_SPEED);
                this.setAnimState(ANIM_WALK_ID);
            } else {
                this.getNavigation().stop();
                this.setAnimState(ANIM_IDLE_ID);
            }

            if (dist > RUN_EXIT_RANGE) {
                return;
            }

            if (this.extenderCooldown <= 0
                    && dist <= EXTENDER_RANGE
                    && dist >= 14.0
                    && canSeeTarget(target)) {
                startExtender(target);
                return;
            }

            if (this.rightCannonCooldown <= 0
                    && dist <= RIGHT_CANNON_RANGE
                    && canSeeTarget(target)) {
                startRightCannon(target);
                return;
            }

            if (this.leftCannonCooldown <= 0
                    && dist <= LEFT_CANNON_RANGE
                    && canSeeTarget(target)) {
                startLeftCannon(target);
                return;
            }
        } else {
            this.getNavigation().stop();
            this.setAnimState(ANIM_IDLE_ID);
        }
    }

    private void startRightCannon(PlayerEntity target) {
        this.attackPhase = AttackPhase.RIGHT_CANNON;
        this.phaseTicks = 0;
        this.rightCannonCooldown = RIGHT_CANNON_COOLDOWN_TICKS;
        this.rightCannonFiredThisCycle = false;
        this.getNavigation().stop();
        zeroHorizontalMotion();
        playWorldSound(ModSounds.MECH_RIGHT_CANNON_FIRE, 2.0f, 1.0f);
    }

    private void tickRightCannon(PlayerEntity target) {
        this.phaseTicks++;
        this.getNavigation().stop();
        zeroHorizontalMotion();
        this.setAnimState(ANIM_RIGHT_CANNON_ID);

        if (!this.rightCannonFiredThisCycle && this.phaseTicks >= RIGHT_CANNON_FIRE_TICK) {
            this.rightCannonFiredThisCycle = true;

            AlienMissileEntity missile = new AlienMissileEntity(ModEntities.ALIEN_MISSILE, this.getWorld());
            Vec3d start = getRightMissileOrigin();

            Vec3d dir = target != null
                    ? target.getPos().add(0.0, target.getStandingEyeHeight() * 0.55, 0.0).subtract(start)
                    : bodyForward();

            if (dir.lengthSquared() < 1.0E-4) dir = bodyForward();
            dir = dir.normalize();

            missile.refreshPositionAndAngles(start.x, start.y, start.z, this.getYaw(), 0.0f);
            missile.setOwnerAndTarget(this, target);
            missile.setVelocity(dir.multiply(0.95));

            this.getWorld().spawnEntity(missile);
        }

        if (this.phaseTicks >= RIGHT_CANNON_TOTAL_TICKS) {
            this.attackPhase = AttackPhase.NONE;
            this.phaseTicks = 0;
        }
    }

    private void startLeftCannon(PlayerEntity target) {
        this.attackPhase = AttackPhase.LEFT_CANNON;
        this.phaseTicks = 0;
        this.leftCannonCooldown = LEFT_CANNON_COOLDOWN_TICKS;
        this.getNavigation().stop();
        zeroHorizontalMotion();
        playWorldSound(ModSounds.MECH_LEFT_CANNON_FIRE, 1.7f, 1.0f);
    }

    private void tickLeftCannon(PlayerEntity target) {
        this.phaseTicks++;
        this.getNavigation().stop();
        zeroHorizontalMotion();
        this.setAnimState(ANIM_LEFT_CANNON_ID);

        if (this.phaseTicks >= LEFT_CANNON_START_FIRE_TICK && this.phaseTicks <= LEFT_CANNON_STOP_FIRE_TICK) {
            if (this.phaseTicks % 2 == 0) {
                fireLeftCannonBurst(target);
            }
        }

        if (this.phaseTicks >= LEFT_CANNON_TOTAL_TICKS) {
            this.attackPhase = AttackPhase.NONE;
            this.phaseTicks = 0;
        }
    }

    private void fireLeftCannonBurst(PlayerEntity target) {
        Vec3d muzzle = getLeftBulletOrigin();
        Vec3d baseDir = target != null
                ? target.getPos().add(0.0, target.getStandingEyeHeight() * 0.55, 0.0).subtract(muzzle)
                : bodyForward();

        if (baseDir.lengthSquared() < 1.0E-4) baseDir = bodyForward();
        baseDir = baseDir.normalize();

        Vec3d right = new Vec3d(-baseDir.z, 0.0, baseDir.x);
        if (right.lengthSquared() < 1.0E-4) {
            right = new Vec3d(1.0, 0.0, 0.0);
        }
        right = right.normalize();
        Vec3d up = right.crossProduct(baseDir).normalize();

        Vec3d start = muzzle.add(baseDir.multiply(6.25));

        Vec3d traceEnd = null;

        for (int i = 0; i < 3; i++) {
            double h = (i - 1) * 0.065;
            double v = (this.random.nextDouble() - 0.5) * 0.035;
            Vec3d dir = baseDir.add(right.multiply(h)).add(up.multiply(v)).normalize();

            Vec3d end = start.add(dir.multiply(52.0));

            HitResult hit = this.getWorld().raycast(new RaycastContext(
                    start, end,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    this
            ));

            Vec3d hitPos = hit.getType() == HitResult.Type.MISS ? end : hit.getPos();
            if (traceEnd == null) {
                traceEnd = hitPos;
            }

            Box box = new Box(start, hitPos).expand(0.55);
            for (LivingEntity e : this.getWorld().getEntitiesByClass(LivingEntity.class, box, ent ->
                    ent.isAlive() && ent != this)) {
                Vec3d center = e.getPos().add(0.0, e.getHeight() * 0.5, 0.0);
                double d = distancePointToSegment(center, start, hitPos);
                if (d > 0.9) continue;

                if (e instanceof PlayerEntity player && isShieldBlocking(player, start)) {
                    damageShield(player, 1);
                    this.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 0.9f, 1.0f);
                    continue;
                }

                e.damage(this.getDamageSources().generic(), 2.5f);
                e.addVelocity(dir.x * 0.11, 0.03, dir.z * 0.11);
                e.velocityModified = true;
            }
        }

        if (traceEnd != null) {
            setBulletTrace(start, traceEnd, 1.0f);
        }
    }

    private boolean isShieldBlocking(PlayerEntity player, Vec3d attackStart) {
        if (!player.isBlocking()) return false;

        Vec3d toAttacker = attackStart.subtract(player.getEyePos()).normalize();
        Vec3d look = player.getRotationVec(1.0f).normalize();

        return look.dotProduct(toAttacker) > 0.15;
    }

    private void damageShield(PlayerEntity player, int amount) {
        ItemStack active = player.getActiveItem();
        if (active.isEmpty()) return;

        active.damage(amount, player, p -> p.sendToolBreakStatus(player.getActiveHand()));
    }

    private void startExtender(PlayerEntity target) {
        this.attackPhase = AttackPhase.EXTENDER_OUT;
        this.phaseTicks = 0;
        this.extenderCooldown = LASER_ATTACK_COOLDOWN_TICKS;
        this.getNavigation().stop();
        zeroHorizontalMotion();
        this.setBeamActive(false);
        this.setBeamAlphaTracked(0.0f);
        this.setWarmupAlphaTracked(0.0f);
        playWorldSound(ModSounds.MECH_EXTENDER_OUT, 1.9f, 1.0f);
    }

    private void tickExtenderOut(PlayerEntity target) {
        this.phaseTicks++;
        this.getNavigation().stop();
        zeroHorizontalMotion();
        this.setAnimState(ANIM_EXTENDER_OUT_ID);
        this.setBeamActive(false);

        if (this.phaseTicks >= EXTENDER_OUT_TICKS) {
            this.attackPhase = AttackPhase.EXTENDER_WARM_UP;
            this.phaseTicks = 0;
        }
    }

    private void tickExtenderWarmUp(PlayerEntity target) {
        this.phaseTicks++;
        this.getNavigation().stop();
        zeroHorizontalMotion();
        this.setAnimState(ANIM_EXTENDER_WARM_UP_ID);
        this.setBeamActive(false);

        float alpha = MathHelper.clamp(this.phaseTicks / (float) EXTENDER_WARM_UP_TICKS, 0.0f, 1.0f);
        this.setWarmupAlphaTracked(alpha);

        if (this.phaseTicks >= EXTENDER_WARM_UP_TICKS) {
            this.attackPhase = AttackPhase.EXTENDER_FIRE;
            this.phaseTicks = 0;
            this.setBeamActive(true);
            this.setBeamAlphaTracked(0.0f);
            this.beamEndServer = clipPointToWorld(
                    getRailGunOrigin(),
                    getRailGunOrigin().add(bodyForward().multiply(BEAM_RANGE))
            );
        }
    }

    private void tickExtenderFire(PlayerEntity target) {
        this.phaseTicks++;
        this.getNavigation().stop();
        zeroHorizontalMotion();
        this.setAnimState(ANIM_EXTENDER_FIRE_ID);

        Vec3d origin = getRailGunOrigin();
        Vec3d desired = target != null
                ? target.getPos().add(0.0, target.getStandingEyeHeight() * 0.55, 0.0)
                : origin.add(bodyForward().multiply(BEAM_RANGE));

        desired = clipPointToWorld(origin, desired);

        if (this.beamEndServer.lengthSquared() < 1.0E-4) {
            this.beamEndServer = desired;
        }

        Vec3d candidate = this.beamEndServer.lerp(desired, 0.10);
        this.beamEndServer = clipPointToWorld(origin, candidate);

        this.setBeamActive(true);
        this.setBeamEnd(this.beamEndServer);

        float midAlpha;
        if (this.phaseTicks <= 6) {
            midAlpha = this.phaseTicks / 6.0f;
        } else if (this.phaseTicks >= EXTENDER_FIRE_TICKS - 6) {
            midAlpha = Math.max(0.0f, (EXTENDER_FIRE_TICKS - this.phaseTicks) / 6.0f);
        } else {
            midAlpha = 1.0f;
        }
        this.setBeamAlphaTracked(midAlpha);
        this.setWarmupAlphaTracked(MathHelper.lerp(0.25f, this.getWarmupAlpha(), 0.0f));

        if (this.age % BEAM_DAMAGE_INTERVAL == 0) {
            applyBeamDamage(origin, this.beamEndServer);
        }

        if (this.phaseTicks >= EXTENDER_FIRE_TICKS) {
            this.attackPhase = AttackPhase.NONE;
            this.phaseTicks = 0;
            this.setBeamActive(false);
        }
    }

    private void applyBeamDamage(Vec3d start, Vec3d end) {
        Vec3d dir = end.subtract(start);
        double len = dir.length();
        if (len < 1.0E-4) return;
        dir = dir.normalize();

        Box box = new Box(start, end).expand(BEAM_END_RADIUS + 2.0);
        for (LivingEntity e : this.getWorld().getEntitiesByClass(LivingEntity.class, box, ent ->
                ent.isAlive() && ent != this && !(ent instanceof OuterMechEntity))) {

            Vec3d center = e.getPos().add(0.0, e.getHeight() * 0.5, 0.0);
            double h = MathHelper.clamp(center.subtract(start).dotProduct(dir), 0.0, len);
            double t = h / len;
            Vec3d nearest = start.add(dir.multiply(h));

            double radius = BEAM_START_RADIUS + (BEAM_END_RADIUS - BEAM_START_RADIUS) * Math.pow(t, 1.85);
            double dist = center.distanceTo(nearest);

            if (dist <= radius + e.getWidth() * 0.35) {
                e.damage(this.getDamageSources().generic(), BEAM_DAMAGE);
                e.setVelocity(e.getVelocity().multiply(0.55));
                e.velocityModified = true;
            }
        }
    }

    private Vec3d clipPointToWorld(Vec3d start, Vec3d end) {
        HitResult hit = this.getWorld().raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                this
        ));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getPos();
    }

    private boolean canSeeTarget(PlayerEntity target) {
        Vec3d from = this.getPos().add(0.0, 7.5, 0.0);
        Vec3d to = target.getPos().add(0.0, target.getStandingEyeHeight() * 0.65, 0.0);

        HitResult hit = this.getWorld().raycast(new RaycastContext(
                from, to,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                this
        ));

        return hit.getType() == HitResult.Type.MISS;
    }

    private void zeroHorizontalMotion() {
        Vec3d v = this.getVelocity();
        this.setVelocity(0.0, v.y, 0.0);
    }

    private void faceTargetBody(PlayerEntity target, float maxTurnDegrees) {
        Vec3d flat = new Vec3d(target.getX() - this.getX(), 0.0, target.getZ() - this.getZ());
        if (flat.lengthSquared() < 1.0E-4) return;

        float targetYaw = (float) (MathHelper.atan2(flat.z, flat.x) * (180.0F / Math.PI)) - 90.0f;
        float newYaw = rotateTowardsDeg(this.getYaw(), targetYaw, maxTurnDegrees);

        this.setYaw(newYaw);
        this.setBodyYaw(newYaw);
        this.setHeadYaw(newYaw);
    }

    private float rotateTowardsDeg(float current, float target, float maxStep) {
        float delta = MathHelper.wrapDegrees(target - current);
        delta = MathHelper.clamp(delta, -maxStep, maxStep);
        return current + delta;
    }

    private void relaxAiming() {
        this.dataTracker.set(LOOK_YAW, MathHelper.lerp(0.20f, this.getLookYawRad(), 0.0f));
        this.dataTracker.set(LOOK_PITCH, MathHelper.lerp(0.20f, this.getLookPitchRad(), 0.0f));
        this.dataTracker.set(EXTEND_YAW, MathHelper.lerp(0.20f, this.getExtendYawRad(), 0.0f));
        this.dataTracker.set(TUBE2_PITCH, MathHelper.lerp(0.20f, this.getTube2PitchRad(), 0.0f));
    }

    private void updateBodyLook(PlayerEntity target, boolean constrained) {
        float desiredYaw = 0.0f;
        float desiredPitch = 0.0f;

        Vec3d from = this.getPos().add(0.0, 6.5, 0.0);
        Vec3d to = target.getPos().add(0.0, target.getStandingEyeHeight() * 0.55, 0.0);
        Vec3d delta = to.subtract(from);
        double len = delta.length();

        if (len > 1.0E-4) {
            float worldYaw = (float)(MathHelper.atan2(delta.z, delta.x) * (180.0F / Math.PI)) - 90.0f;
            float relYaw = MathHelper.wrapDegrees(worldYaw - this.getYaw());

            float yawClamp = constrained ? 0.32f : 0.60f;
            float lerpAmt = constrained ? 0.12f : 0.18f;

            desiredYaw = MathHelper.clamp(relYaw * MathHelper.RADIANS_PER_DEGREE, -yawClamp, yawClamp);

            if (!constrained) {
                float pitchDeg = (float)(-(Math.asin(delta.y / len) * (180.0F / Math.PI)));
                desiredPitch = MathHelper.clamp(pitchDeg * MathHelper.RADIANS_PER_DEGREE, -0.42f, 0.28f);
            } else {
                desiredPitch = 0.0f;
            }

            this.dataTracker.set(LOOK_YAW, MathHelper.lerp(lerpAmt, this.getLookYawRad(), desiredYaw));
            this.dataTracker.set(LOOK_PITCH, MathHelper.lerp(0.20f, this.getLookPitchRad(), desiredPitch));
            return;
        }

        this.dataTracker.set(LOOK_YAW, MathHelper.lerp(0.20f, this.getLookYawRad(), 0.0f));
        this.dataTracker.set(LOOK_PITCH, MathHelper.lerp(0.20f, this.getLookPitchRad(), 0.0f));
    }

    private void updateExtenderAim(PlayerEntity target) {
        if (this.attackPhase != AttackPhase.EXTENDER_WARM_UP && this.attackPhase != AttackPhase.EXTENDER_FIRE) {
            this.dataTracker.set(EXTEND_YAW, MathHelper.lerp(0.20f, this.getExtendYawRad(), 0.0f));
            this.dataTracker.set(TUBE2_PITCH, MathHelper.lerp(0.20f, this.getTube2PitchRad(), 0.0f));
            return;
        }

        Vec3d origin = getRailGunOrigin();
        Vec3d desired = target.getPos().add(0.0, target.getStandingEyeHeight() * 0.55, 0.0);
        desired = clipPointToWorld(origin, desired);

        Vec3d worldDelta = desired.subtract(origin);
        if (worldDelta.lengthSquared() < 1.0E-4) {
            return;
        }

        Vec3d local = worldDelta.rotateY(this.getYaw() * MathHelper.RADIANS_PER_DEGREE);

        double horizLen = Math.sqrt(local.x * local.x + local.z * local.z);
        if (horizLen < 1.0E-4) horizLen = 1.0E-4;

        float yawRad = (float) MathHelper.clamp(Math.atan2(local.z, local.x), -0.70, 0.70);
        float pitchRad = (float) MathHelper.clamp(-Math.atan2(local.y, horizLen), -0.45, 0.35);

        this.dataTracker.set(EXTEND_YAW, MathHelper.lerp(0.10f, this.getExtendYawRad(), yawRad));
        this.dataTracker.set(TUBE2_PITCH, MathHelper.lerp(0.10f, this.getTube2PitchRad(), pitchRad));
    }

    public Vec3d bodyForward() {
        float yawRad = (this.getYaw() + 90.0f) * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(Math.cos(yawRad), 0.0, Math.sin(yawRad)).normalize();
    }

    private double distancePointToSegment(Vec3d p, Vec3d a, Vec3d b) {
        Vec3d ab = b.subtract(a);
        double den = Math.max(ab.lengthSquared(), 1.0E-4);
        double h = MathHelper.clamp(p.subtract(a).dotProduct(ab) / den, 0.0, 1.0);
        return p.distanceTo(a.add(ab.multiply(h)));
    }

    private void playWorldSound(net.minecraft.sound.SoundEvent sound, float volume, float pitch) {
        this.getWorld().playSound(
                null,
                this.getX(), this.getY(), this.getZ(),
                sound,
                SoundCategory.HOSTILE,
                volume,
                pitch
        );
    }

    private void setAnimState(int state) {
        this.dataTracker.set(ANIM_STATE, state);
    }

    private void setBeamActive(boolean active) {
        this.dataTracker.set(BEAM_ACTIVE, active);
    }

    private void setBeamEnd(Vec3d end) {
        this.dataTracker.set(BEAM_END_X, (float) end.x);
        this.dataTracker.set(BEAM_END_Y, (float) end.y);
        this.dataTracker.set(BEAM_END_Z, (float) end.z);
    }

    private void setBeamAlphaTracked(float alpha) {
        this.dataTracker.set(BEAM_ALPHA, alpha);
    }

    private void setWarmupAlphaTracked(float alpha) {
        this.dataTracker.set(WARMUP_ALPHA, alpha);
    }

    private void setBulletTrace(Vec3d start, Vec3d end, float alpha) {
        this.dataTracker.set(BULLET_START_X, (float) start.x);
        this.dataTracker.set(BULLET_START_Y, (float) start.y);
        this.dataTracker.set(BULLET_START_Z, (float) start.z);
        this.dataTracker.set(BULLET_END_X, (float) end.x);
        this.dataTracker.set(BULLET_END_Y, (float) end.y);
        this.dataTracker.set(BULLET_END_Z, (float) end.z);
        this.dataTracker.set(BULLET_ALPHA, alpha);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "outer_mech.controller", 0, state -> {
            switch (this.dataTracker.get(ANIM_STATE)) {
                case ANIM_SPAWN_ID -> state.setAndContinue(ANIM_SPAWN);
                case ANIM_WALK_ID -> state.setAndContinue(ANIM_WALK);
                case ANIM_RIGHT_CANNON_ID -> state.setAndContinue(ANIM_RIGHT_CANNON);
                case ANIM_EXTENDER_OUT_ID -> state.setAndContinue(ANIM_EXTENDER_OUT);
                case ANIM_EXTENDER_FIRE_ID -> state.setAndContinue(ANIM_EXTENDER_FIRE);
                case ANIM_RUN_ID -> state.setAndContinue(ANIM_RUN);
                case ANIM_EXTENDER_WARM_UP_ID -> state.setAndContinue(ANIM_EXTENDER_WARM_UP);
                case ANIM_LEFT_CANNON_ID -> state.setAndContinue(ANIM_LEFT_CANNON);
                default -> state.setAndContinue(ANIM_IDLE);
            }
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("AttackPhase", this.attackPhase.name());
        nbt.putInt("SpawnTicks", this.spawnTicks);
        nbt.putInt("PhaseTicks", this.phaseTicks);
        nbt.putInt("RightCannonCooldown", this.rightCannonCooldown);
        nbt.putInt("LeftCannonCooldown", this.leftCannonCooldown);
        nbt.putInt("ExtenderCooldown", this.extenderCooldown);
        nbt.putBoolean("RightCannonFiredThisCycle", this.rightCannonFiredThisCycle);
        nbt.putDouble("BeamEndX", this.beamEndServer.x);
        nbt.putDouble("BeamEndY", this.beamEndServer.y);
        nbt.putDouble("BeamEndZ", this.beamEndServer.z);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        try {
            this.attackPhase = AttackPhase.valueOf(nbt.getString("AttackPhase"));
        } catch (Exception ignored) {
            this.attackPhase = AttackPhase.NONE;
        }
        this.spawnTicks = nbt.getInt("SpawnTicks");
        this.phaseTicks = nbt.getInt("PhaseTicks");
        this.rightCannonCooldown = nbt.getInt("RightCannonCooldown");
        this.leftCannonCooldown = nbt.getInt("LeftCannonCooldown");
        this.extenderCooldown = nbt.contains("ExtenderCooldown") ? nbt.getInt("ExtenderCooldown") : LASER_ATTACK_COOLDOWN_TICKS;
        this.rightCannonFiredThisCycle = nbt.getBoolean("RightCannonFiredThisCycle");
        this.beamEndServer = new Vec3d(
                nbt.getDouble("BeamEndX"),
                nbt.getDouble("BeamEndY"),
                nbt.getDouble("BeamEndZ")
        );
    }
}