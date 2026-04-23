package net.seep.odd.entity.ufo;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.seep.odd.sound.ModSounds;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

public final class UfoSlicerEntity extends PathAwareEntity implements GeoEntity {
    private enum SlicePhase { ORBIT, PREP, DIVE, CONTACT, RECOVER }

    private static final RawAnimation ANIM_IDLE  = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation ANIM_SLICE = RawAnimation.begin().thenLoop("slice");

    private static final int AGGRO_RADIUS = 36;

    private static final double ORBIT_SPEED = 0.95;
    private static final double ORBIT_R_NEAR = 4.5;
    private static final double ORBIT_R_FAR = 7.2;
    private static final double ORBIT_W = 0.33;
    private static final double ORBIT_HEIGHT = 3.7;
    private static final double ORBIT_Y_WAVE = 1.15;
    private static final double MIN_ALT_ABOVE_GROUND = 4.0;

    private static final int PREP_TICKS = 14;
    private static final int CONTACT_TICKS = 4;
    private static final int RECOVER_TICKS = 7;
    private static final int ORBIT_PAUSE_MIN = 16;
    private static final int ORBIT_PAUSE_MAX = 28;

    private static final double DIVE_BURST_SPEED = 2.35;
    private static final double CONTACT_STICK_SPEED = 0.90;
    private static final double RECOVER_BURST_SPEED = 1.75;

    private static final double EXIT_FORWARD_DIST = 5.6;
    private static final double EXIT_HEIGHT = 3.6;

    private static final float SLICE_DAMAGE = 3.0f;
    private static final int DAMAGE_INTERVAL = 2;

    private static final TrackedData<Integer> ANIM_STATE =
            DataTracker.registerData(UfoSlicerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> VISUAL_TILT =
            DataTracker.registerData(UfoSlicerEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private SlicePhase slicePhase = SlicePhase.ORBIT;
    private int sliceCooldown = 20;
    private int prepTicks;
    private int contactTicks;
    private int recoverTicks;
    private int burstsRemaining;

    private Vec3d attackDir = new Vec3d(1, 0, 0);
    private Vec3d lockedPrepPos = Vec3d.ZERO;
    private Vec3d exitPoint = Vec3d.ZERO;
    private double requestedY = Double.NaN;

    private float lockedYaw = 0.0f;
    private boolean lockFacing = false;
    private int diveRampTicks = 0;
    private int recoverRampTicks = 0;

    public UfoSlicerEntity(EntityType<? extends UfoSlicerEntity> type, World world) {
        super(type, world);
        this.moveControl = new FlightMoveControl(this, 55, true);
        this.setNoGravity(true);
        this.ignoreCameraFrustum = true;
        this.setPersistent();
        this.experiencePoints = 8;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 18.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.40D)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 1.35D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, AGGRO_RADIUS)
                .add(EntityAttributes.GENERIC_ARMOR, 2.0D);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(ANIM_STATE, 0);
        this.dataTracker.startTracking(VISUAL_TILT, 0.0f);
    }

    private void setAnimState(int state) {
        this.dataTracker.set(ANIM_STATE, state);
    }

    private int getAnimState() {
        return this.dataTracker.get(ANIM_STATE);
    }

    public float getVisualTiltRad() {
        return this.dataTracker.get(VISUAL_TILT);
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

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    private static final class SlicerSounds {
        static final SoundEvent HURT  = ModSounds.SAUCER_HURT;
        static final SoundEvent DEATH = ModSounds.SAUCER_DEATH;
        static final SoundEvent DIVE  = ModSounds.SAUCER_BOOST;
        static final SoundEvent SLICE = ModSounds.UFO_SLICE;
    }

    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(net.minecraft.entity.damage.DamageSource src) { return SlicerSounds.HURT; }
    @Override protected SoundEvent getDeathSound() { return SlicerSounds.DEATH; }
    @Override protected float getSoundVolume() { return 0.5F; }
    @Override public int getMinAmbientSoundDelay() { return 200; }

    @Override
    protected void initGoals() {
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true,
                p -> p.isAlive() && !p.isSpectator()));
        this.goalSelector.add(1, new UfoSliceGoal());
    }

    @Override
    public void tick() {
        super.tick();

        this.fallDistance = 0.0f;

        if (!this.getWorld().isClient) {
            if (this.getTarget() == null || !this.getTarget().isAlive() || this.age % 20 == 0) {
                PlayerEntity nearest = this.getWorld().getClosestPlayer(this, AGGRO_RADIUS);
                if (nearest != null && !nearest.isSpectator()) {
                    this.setTarget(nearest);
                }
            }
        }

        if (!this.isTouchingWater() && !this.isSubmergedIn(FluidTags.WATER)) {
            Vec3d v = this.getVelocity();

            if (slicePhase == SlicePhase.DIVE || slicePhase == SlicePhase.CONTACT || slicePhase == SlicePhase.RECOVER) {
                this.setVelocity(v.x * 0.995, v.y * 0.995, v.z * 0.995);
            } else {
                this.setVelocity(v.x * 0.988, v.y * 0.988, v.z * 0.988);
            }
        }

        if (sliceCooldown > 0) sliceCooldown--;

        applyVerticalAssist();
        updateFacing();
        updateVisualTilt();
    }

    private void updateFacing() {
        if (lockFacing) {
            this.setYaw(lockedYaw);
            this.setBodyYaw(lockedYaw);
            this.setHeadYaw(lockedYaw);
            return;
        }

        Vec3d v = this.getVelocity();
        if (v.lengthSquared() > 1.0E-3) {
            float targetYaw = (float)(MathHelper.atan2(v.z, v.x) * (180F / Math.PI)) - 90F;
            float smooth = rotateTowards(this.getYaw(), targetYaw, 18f);
            this.setYaw(smooth);
            this.setBodyYaw(smooth);
            this.setHeadYaw(smooth);
        }
    }

    private void updateVisualTilt() {
        float current = this.getVisualTiltRad();
        float desired;

        switch (slicePhase) {
            case DIVE, CONTACT -> desired = MathHelper.clamp((float)(0.58f + (-this.getVelocity().y * 0.30f)), -0.05f, 1.0f);
            case RECOVER -> desired = MathHelper.clamp((float)(-0.42f + (-this.getVelocity().y * 0.18f)), -0.95f, 0.15f);
            default -> desired = 0.0f;
        }

        this.dataTracker.set(VISUAL_TILT, MathHelper.lerp(0.38f, current, desired));
    }

    private static float rotateTowards(float current, float target, float maxStep) {
        float delta = MathHelper.wrapDegrees(target - current);
        if (delta > maxStep) delta = maxStep;
        if (delta < -maxStep) delta = -maxStep;
        return current + delta;
    }

    private int groundYAt(BlockPos pos) {
        return this.getWorld().getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, pos).getY();
    }

    private void moveToPoint(Vec3d point, double speed) {
        this.getMoveControl().moveTo(point.x, point.y, point.z, speed);
        this.requestedY = point.y;
    }

    private void applyVerticalAssist() {
        if (Double.isNaN(requestedY)) return;
        if (slicePhase == SlicePhase.DIVE || slicePhase == SlicePhase.CONTACT || slicePhase == SlicePhase.RECOVER) return;

        double dy = requestedY - this.getY();
        Vec3d cur = this.getVelocity();

        if (dy > 0.05) {
            double boost = MathHelper.clamp(dy * 0.22, 0.05, 0.55);
            this.setVelocity(cur.x, Math.max(cur.y, cur.y + boost), cur.z);
        } else {
            this.setVelocity(cur.x, Math.max(cur.y, -0.45), cur.z);
        }
    }

    private Vec3d targetBody(PlayerEntity target) {
        return target.getPos().add(0.0, target.getStandingEyeHeight() * 0.45, 0.0);
    }

    private Vec3d horizontalDirTo(PlayerEntity target) {
        Vec3d to = targetBody(target).subtract(this.getPos());
        Vec3d flat = new Vec3d(to.x, 0.0, to.z);
        if (flat.lengthSquared() < 1.0E-4) {
            return new Vec3d(1, 0, 0);
        }
        return flat.normalize();
    }

    private void lockFacingTo(Vec3d dir) {
        Vec3d flat = new Vec3d(dir.x, 0.0, dir.z);
        if (flat.lengthSquared() < 1.0E-4) return;
        flat = flat.normalize();
        this.lockedYaw = (float)(MathHelper.atan2(flat.z, flat.x) * (180F / Math.PI)) - 90F;
        this.lockFacing = true;
    }

    private void unlockFacing() {
        this.lockFacing = false;
    }

    private void beginSliceRun(PlayerEntity target) {
        this.attackDir = horizontalDirTo(target);
        this.lockedPrepPos = this.getPos();
        this.exitPoint = targetBody(target)
                .add(this.attackDir.multiply(EXIT_FORWARD_DIST))
                .add(0.0, EXIT_HEIGHT, 0.0);

        this.slicePhase = SlicePhase.PREP;
        this.prepTicks = PREP_TICKS;
        this.contactTicks = 0;
        this.recoverTicks = RECOVER_TICKS;
        this.setAnimState(0);
        this.requestedY = this.getY();
        this.unlockFacing();
        this.diveRampTicks = 0;
        this.recoverRampTicks = 0;

        if (this.getWorld() instanceof ServerWorld sw) {
            sw.playSound(null, this.getBlockPos(), SlicerSounds.DIVE, SoundCategory.HOSTILE, 0.8f, 1.35f);
        }
    }

    private void returnToOrbit() {
        this.slicePhase = SlicePhase.ORBIT;
        this.setAnimState(0);
        this.sliceCooldown = ORBIT_PAUSE_MIN + this.random.nextInt(ORBIT_PAUSE_MAX - ORBIT_PAUSE_MIN + 1);
        this.requestedY = Double.NaN;
        this.unlockFacing();
        this.diveRampTicks = 0;
        this.recoverRampTicks = 0;
    }

    private void doSliceDamage(PlayerEntity target) {
        if (this.age % DAMAGE_INTERVAL != 0) return;

        target.damage(this.getDamageSources().mobAttack(this), SLICE_DAMAGE);

        Vec3d gripPoint = targetBody(target).add(this.attackDir.multiply(0.25));
        Vec3d pull = gripPoint.subtract(target.getPos()).multiply(0.18, 0.0, 0.18);

        target.setVelocity(target.getVelocity().multiply(0.55).add(pull.x, 0.02, pull.z));
        target.velocityModified = true;
        target.fallDistance = 0;

        if (this.getWorld() instanceof ServerWorld sw) {
            Vec3d body = targetBody(target);
            sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, body.x, body.y, body.z, 4, 0.15, 0.15, 0.15, 0.01);
            sw.spawnParticles(ParticleTypes.CRIT, body.x, body.y, body.z, 6, 0.12, 0.12, 0.12, 0.03);
            sw.playSound(null, target.getBlockPos(), SlicerSounds.SLICE, SoundCategory.HOSTILE, 0.55f, 1.05f);
        }
    }

    private double distanceSqTo(Vec3d pos) {
        return this.squaredDistanceTo(pos.x, pos.y, pos.z);
    }

    private boolean touchingTarget(PlayerEntity target) {
        Box mine = this.getBoundingBox().expand(0.35);
        return mine.intersects(target.getBoundingBox());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "ufo_slicer.controller", 0, state -> {
            if (getAnimState() == 1) {
                state.setAndContinue(ANIM_SLICE);
            } else {
                state.setAndContinue(ANIM_IDLE);
            }
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    final class UfoSliceGoal extends Goal {
        UfoSliceGoal() {
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.TARGET));
        }

        @Override
        public boolean canStart() {
            return getTarget() != null && getTarget().isAlive();
        }

        @Override
        public void tick() {
            PlayerEntity target = (PlayerEntity) getTarget();
            if (target == null) return;

            if (!target.isAlive() || target.isSpectator() || target.getAbilities().creativeMode) {
                returnToOrbit();
                return;
            }

            switch (slicePhase) {
                case ORBIT -> tickOrbit(target);
                case PREP -> tickPrep(target);
                case DIVE -> tickDive(target);
                case CONTACT -> tickContact(target);
                case RECOVER -> tickRecover(target);
            }
        }

        private void tickOrbit(PlayerEntity target) {
            double distSq = UfoSlicerEntity.this.squaredDistanceTo(target);
            boolean far = distSq > 10.0 * 10.0;

            double r = far ? ORBIT_R_FAR : ORBIT_R_NEAR;
            double angle = (UfoSlicerEntity.this.age * ORBIT_W) + UfoSlicerEntity.this.getId() * 0.57;

            double orbitX = target.getX() + Math.cos(angle) * r;
            double orbitZ = target.getZ() + Math.sin(angle) * r;
            double groundY = groundYAt(BlockPos.ofFloored(orbitX, target.getY(), orbitZ));
            double targetY = Math.max(
                    groundY + MIN_ALT_ABOVE_GROUND,
                    target.getY() + ORBIT_HEIGHT + Math.sin((UfoSlicerEntity.this.age + UfoSlicerEntity.this.getId()) * 0.19) * ORBIT_Y_WAVE
            );

            moveToPoint(new Vec3d(orbitX, targetY, orbitZ), ORBIT_SPEED);
            setAnimState(0);

            if (sliceCooldown <= 0 && distSq < 20.0 * 20.0 && UfoSlicerEntity.this.canSee(target)) {
                burstsRemaining = 2 + UfoSlicerEntity.this.random.nextInt(2);
                beginSliceRun(target);
            }
        }

        private void tickPrep(PlayerEntity target) {
            Vec3d hover = lockedPrepPos.add(0.0, Math.sin(UfoSlicerEntity.this.age * 0.35) * 0.08, 0.0);
            moveToPoint(hover, 0.45);
            setAnimState(0);

            if (--prepTicks <= 0) {
                slicePhase = SlicePhase.DIVE;
                setAnimState(1);
                diveRampTicks = 0;

                Vec3d startDiveDir = targetBody(target).subtract(UfoSlicerEntity.this.getPos());
                if (startDiveDir.lengthSquared() > 1.0E-4) {
                    lockFacingTo(startDiveDir);
                }

                if (getWorld() instanceof ServerWorld sw) {
                    sw.playSound(null, getBlockPos(), SlicerSounds.DIVE, SoundCategory.HOSTILE, 0.9f, 1.7f);
                }
            }
        }

        private void tickDive(PlayerEntity target) {
            Vec3d body = targetBody(target);
            Vec3d lead = target.getVelocity().multiply(0.28);
            Vec3d aim = body.add(lead);

            Vec3d dir = aim.subtract(UfoSlicerEntity.this.getPos());
            if (dir.lengthSquared() > 1.0E-4) {
                dir = dir.normalize();

                Vec3d flat = new Vec3d(dir.x, 0.0, dir.z);
                if (flat.lengthSquared() > 1.0E-4) {
                    attackDir = flat.normalize();
                }

                lockFacingTo(dir);

                double ramp = MathHelper.clamp((diveRampTicks + 1) / 3.0, 0.0, 1.0);
                double speed = MathHelper.lerp(ramp, 0.95, DIVE_BURST_SPEED);
                diveRampTicks++;

                UfoSlicerEntity.this.setVelocity(dir.multiply(speed));
            }

            if (distanceSqTo(aim) < 1.45 || touchingTarget(target)) {
                slicePhase = SlicePhase.CONTACT;
                contactTicks = CONTACT_TICKS;
                setAnimState(1);
                doSliceDamage(target);
            }
        }

        private void tickContact(PlayerEntity target) {
            Vec3d gripPoint = targetBody(target).add(attackDir.multiply(0.30));
            Vec3d dir = gripPoint.subtract(UfoSlicerEntity.this.getPos());

            if (dir.lengthSquared() > 1.0E-4) {
                dir = dir.normalize();
                lockFacingTo(dir);
                UfoSlicerEntity.this.setVelocity(dir.multiply(CONTACT_STICK_SPEED));
            }

            setAnimState(1);
            doSliceDamage(target);

            if (--contactTicks <= 0) {
                slicePhase = SlicePhase.RECOVER;
                recoverTicks = RECOVER_TICKS;
                recoverRampTicks = 0;
                setAnimState(0);
            }
        }

        private void tickRecover(PlayerEntity target) {
            exitPoint = targetBody(target)
                    .add(attackDir.multiply(EXIT_FORWARD_DIST))
                    .add(0.0, EXIT_HEIGHT, 0.0);

            Vec3d dir = exitPoint.subtract(UfoSlicerEntity.this.getPos());
            if (dir.lengthSquared() > 1.0E-4) {
                dir = dir.normalize();
                lockFacingTo(dir);

                double ramp = MathHelper.clamp((recoverRampTicks + 1) / 3.0, 0.0, 1.0);
                double speed = MathHelper.lerp(ramp, 0.75, RECOVER_BURST_SPEED);
                recoverRampTicks++;

                UfoSlicerEntity.this.setVelocity(dir.multiply(speed));
            }

            setAnimState(0);

            if (--recoverTicks <= 0 || distanceSqTo(exitPoint) < 2.2) {
                burstsRemaining--;
                if (burstsRemaining > 0 && target.isAlive()) {
                    beginSliceRun(target);
                } else {
                    returnToOrbit();
                }
            }
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("SlicePhase", this.slicePhase.name());
        nbt.putInt("SliceCooldown", this.sliceCooldown);
        nbt.putInt("PrepTicks", this.prepTicks);
        nbt.putInt("ContactTicks", this.contactTicks);
        nbt.putInt("RecoverTicks", this.recoverTicks);
        nbt.putInt("BurstsRemaining", this.burstsRemaining);

        nbt.putDouble("AttackDirX", this.attackDir.x);
        nbt.putDouble("AttackDirY", this.attackDir.y);
        nbt.putDouble("AttackDirZ", this.attackDir.z);

        nbt.putDouble("PrepPosX", this.lockedPrepPos.x);
        nbt.putDouble("PrepPosY", this.lockedPrepPos.y);
        nbt.putDouble("PrepPosZ", this.lockedPrepPos.z);

        nbt.putDouble("ExitX", this.exitPoint.x);
        nbt.putDouble("ExitY", this.exitPoint.y);
        nbt.putDouble("ExitZ", this.exitPoint.z);

        nbt.putDouble("RequestedY", this.requestedY);
        nbt.putFloat("LockedYaw", this.lockedYaw);
        nbt.putBoolean("LockFacing", this.lockFacing);
        nbt.putInt("DiveRampTicks", this.diveRampTicks);
        nbt.putInt("RecoverRampTicks", this.recoverRampTicks);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);

        try {
            this.slicePhase = SlicePhase.valueOf(nbt.getString("SlicePhase"));
        } catch (Exception ignored) {
            this.slicePhase = SlicePhase.ORBIT;
        }

        this.sliceCooldown = nbt.getInt("SliceCooldown");
        this.prepTicks = nbt.getInt("PrepTicks");
        this.contactTicks = nbt.getInt("ContactTicks");
        this.recoverTicks = nbt.getInt("RecoverTicks");
        this.burstsRemaining = nbt.getInt("BurstsRemaining");

        this.attackDir = new Vec3d(
                nbt.getDouble("AttackDirX"),
                nbt.getDouble("AttackDirY"),
                nbt.getDouble("AttackDirZ")
        );

        this.lockedPrepPos = new Vec3d(
                nbt.getDouble("PrepPosX"),
                nbt.getDouble("PrepPosY"),
                nbt.getDouble("PrepPosZ")
        );

        this.exitPoint = new Vec3d(
                nbt.getDouble("ExitX"),
                nbt.getDouble("ExitY"),
                nbt.getDouble("ExitZ")
        );

        this.requestedY = nbt.contains("RequestedY") ? nbt.getDouble("RequestedY") : Double.NaN;
        this.lockedYaw = nbt.contains("LockedYaw") ? nbt.getFloat("LockedYaw") : 0.0f;
        this.lockFacing = nbt.getBoolean("LockFacing");
        this.diveRampTicks = nbt.getInt("DiveRampTicks");
        this.recoverRampTicks = nbt.getInt("RecoverRampTicks");

        if (this.slicePhase == SlicePhase.DIVE || this.slicePhase == SlicePhase.CONTACT) {
            this.setAnimState(1);
        } else {
            this.setAnimState(0);
        }

        this.setNoGravity(true);
        this.setSilent(false);
    }
}