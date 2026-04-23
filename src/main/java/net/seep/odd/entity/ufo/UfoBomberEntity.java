package net.seep.odd.entity.ufo;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.seep.odd.entity.ModEntities;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class UfoBomberEntity extends PathAwareEntity implements GeoEntity {
    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("idle");

    private static final int DETECTION_RANGE = 128;
    private static final int BOMB_COOLDOWN_TICKS = 45; // 2x more frequent than before

    private static final double CRUISE_SPEED = 1.50;
    private static final double ATTACK_SPEED = 1.85;

    private static final double TURN_RATE_CRUISE = 0.045;
    private static final double TURN_RATE_ATTACK = 0.070;

    private static final double CRUISE_ALTITUDE = 30.0;
    private static final double ATTACK_ALTITUDE = 26.0;
    private static final double MIN_SAFE_ALTITUDE = 18.0;

    private static final double PASS_BACK_DISTANCE = 34.0;
    private static final double PASS_FORWARD_DISTANCE = 46.0;
    private static final double DROP_HORIZONTAL_WINDOW = 4.5;
    private static final double DROP_MIN_HEIGHT_ABOVE_TARGET = 12.0;

    private static final TrackedData<Float> VISUAL_PITCH =
            DataTracker.registerData(UfoBomberEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> VISUAL_ROLL =
            DataTracker.registerData(UfoBomberEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private enum RunState { CRUISE, INGRESS, ATTACK_PASS }

    private RunState runState = RunState.CRUISE;
    private Vec3d runDir = new Vec3d(1.0, 0.0, 0.0);
    private boolean bombDroppedThisPass = false;
    private int bombCooldown = 20;

    public UfoBomberEntity(EntityType<? extends UfoBomberEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.ignoreCameraFrustum = true;
        this.setPersistent();
        this.experiencePoints = 14;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 46.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.45D)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 1.60D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, DETECTION_RANGE)
                .add(EntityAttributes.GENERIC_ARMOR, 6.0D);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(VISUAL_PITCH, 0.0f);
        this.dataTracker.startTracking(VISUAL_ROLL, 0.0f);
    }

    @Override
    protected void initGoals() {
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true,
                p -> p.isAlive() && !p.isSpectator()));
    }

    public float getVisualPitchRad() {
        return this.dataTracker.get(VISUAL_PITCH);
    }

    public float getVisualRollRad() {
        return this.dataTracker.get(VISUAL_ROLL);
    }

    @Override
    public boolean hasNoGravity() {
        return true;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(net.minecraft.entity.damage.DamageSource source) {
        return null;
    }

    @Override
    public void tick() {
        super.tick();

        this.fallDistance = 0.0f;

        if (!this.getWorld().isClient) {
            if (this.getTarget() == null || !this.getTarget().isAlive() || this.age % 20 == 0) {
                PlayerEntity nearest = this.getWorld().getClosestPlayer(this, DETECTION_RANGE);
                if (nearest != null && !nearest.isSpectator()) {
                    this.setTarget(nearest);
                }
            }
        }

        if (this.bombCooldown > 0) {
            this.bombCooldown--;
        }

        Vec3d desiredPoint = computeDesiredFlightPoint();
        flyToward(desiredPoint);

        if (!this.isTouchingWater() && !this.isSubmergedIn(FluidTags.WATER)) {
            Vec3d v = this.getVelocity();
            this.setVelocity(v.x * 0.998, v.y * 0.998, v.z * 0.998);
        }
    }

    private Vec3d computeDesiredFlightPoint() {
        PlayerEntity target = this.getTarget() instanceof PlayerEntity p && p.isAlive() && !p.isSpectator() ? p : null;
        Vec3d currentForward = currentForward();

        if (target == null) {
            this.runState = RunState.CRUISE;
            this.bombDroppedThisPass = false;

            Vec3d ahead = this.getPos().add(currentForward.multiply(90.0));
            double y = desiredAltitudeAt(ahead.x, ahead.z, CRUISE_ALTITUDE)
                    + Math.sin((this.age + this.getId()) * 0.05) * 2.5;
            return new Vec3d(ahead.x, y, ahead.z);
        }

        Vec3d targetPos = target.getPos();

        if (this.runState == RunState.CRUISE) {
            Vec3d flatToTarget = new Vec3d(targetPos.x - this.getX(), 0.0, targetPos.z - this.getZ());
            if (flatToTarget.lengthSquared() > 1.0E-4) {
                this.runDir = flatToTarget.normalize();
            } else {
                this.runDir = new Vec3d(currentForward.x, 0.0, currentForward.z);
                if (this.runDir.lengthSquared() < 1.0E-4) this.runDir = new Vec3d(1.0, 0.0, 0.0);
                this.runDir = this.runDir.normalize();
            }
            this.runState = RunState.INGRESS;
            this.bombDroppedThisPass = false;
        }

        if (this.runState == RunState.INGRESS) {
            Vec3d ingress = targetPos.subtract(this.runDir.multiply(PASS_BACK_DISTANCE));
            ingress = new Vec3d(ingress.x, desiredAltitudeAt(ingress.x, ingress.z, ATTACK_ALTITUDE), ingress.z);

            if (horizontalDistanceSq(this.getPos(), ingress) < 110.0) {
                this.runState = RunState.ATTACK_PASS;
                this.bombDroppedThisPass = false;
            }

            return ingress;
        }

        Vec3d egress = targetPos.add(this.runDir.multiply(PASS_FORWARD_DISTANCE));
        egress = new Vec3d(egress.x, desiredAltitudeAt(egress.x, egress.z, ATTACK_ALTITUDE - 2.0), egress.z);

        double along = horizontalDotFromTarget(this.getPos(), targetPos, this.runDir);
        double sideError = horizontalSideError(this.getPos(), targetPos, this.runDir);

        if (!this.bombDroppedThisPass
                && this.bombCooldown <= 0
                && Math.abs(along) <= DROP_HORIZONTAL_WINDOW
                && sideError < 5.5
                && this.getY() - target.getY() >= DROP_MIN_HEIGHT_ABOVE_TARGET) {
            dropBomb();
            this.bombDroppedThisPass = true;
            this.bombCooldown = BOMB_COOLDOWN_TICKS;
        }

        if (along > PASS_FORWARD_DISTANCE * 0.72) {
            Vec3d flatForward = new Vec3d(currentForward.x, 0.0, currentForward.z);
            if (flatForward.lengthSquared() > 1.0E-4) {
                this.runDir = flatForward.normalize();
            }
            this.runState = RunState.INGRESS;
            this.bombDroppedThisPass = false;
        }

        return egress;
    }

    private void flyToward(Vec3d desiredPoint) {
        Vec3d currentForward = currentForward();
        Vec3d toPoint = desiredPoint.subtract(this.getPos());

        double safeFloor = desiredAltitudeAt(this.getX(), this.getZ(), MIN_SAFE_ALTITUDE);
        if (this.getY() < safeFloor) {
            toPoint = toPoint.add(0.0, 1.5, 0.0);
        }

        if (toPoint.lengthSquared() < 1.0E-4) {
            toPoint = currentForward;
        }

        Vec3d desiredDir = toPoint.normalize();
        boolean attacking = this.runState == RunState.INGRESS || this.runState == RunState.ATTACK_PASS;
        double turnRate = attacking ? TURN_RATE_ATTACK : TURN_RATE_CRUISE;
        double speed = attacking ? ATTACK_SPEED : CRUISE_SPEED;

        Vec3d newForward = currentForward.lerp(desiredDir, turnRate).normalize();
        this.setVelocity(newForward.multiply(speed));

        float targetYaw = (float)(MathHelper.atan2(newForward.z, newForward.x) * (180F / Math.PI)) - 90.0f;
        float yawDelta = MathHelper.wrapDegrees(targetYaw - this.getYaw());

        this.setYaw(targetYaw);
        this.setBodyYaw(targetYaw);
        this.setHeadYaw(targetYaw);

        float targetPitch = MathHelper.clamp((float)(-Math.asin(newForward.y)), -0.62f, 0.62f);
        float targetRoll = MathHelper.clamp(-yawDelta * 0.035f, -0.95f, 0.95f);

        float smoothPitch = MathHelper.lerp(0.18f, this.getVisualPitchRad(), targetPitch);
        float smoothRoll = MathHelper.lerp(0.18f, this.getVisualRollRad(), targetRoll);

        this.dataTracker.set(VISUAL_PITCH, smoothPitch);
        this.dataTracker.set(VISUAL_ROLL, smoothRoll);
    }

    private void dropBomb() {
        AlienBombEntity bomb = new AlienBombEntity(ModEntities.ALIEN_BOMB, this.getWorld());

        Vec3d spawn = bomberLocalToWorld(new Vec3d(0.0, -0.65, 0.15));
        bomb.refreshPositionAndAngles(spawn.x, spawn.y, spawn.z, 0.0f, 0.0f);

        Vec3d inherited = this.getVelocity().multiply(0.72);
        bomb.setVelocity(inherited.x, Math.min(-0.20, inherited.y - 0.12), inherited.z);

        this.getWorld().spawnEntity(bomb);
    }

    public Vec3d bomberLocalToWorld(Vec3d local) {
        float pitch = this.getVisualPitchRad();
        float roll = this.getVisualRollRad();
        float yawRad = -this.getYaw() * MathHelper.RADIANS_PER_DEGREE;

        Vec3d rotated = local.rotateZ(roll).rotateX(pitch).rotateY(yawRad);
        return this.getPos().add(rotated);
    }

    private double desiredAltitudeAt(double x, double z, double extra) {
        int groundY = this.getWorld().getTopPosition(
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.ofFloored(x, this.getY(), z)
        ).getY();
        return groundY + extra;
    }

    private Vec3d currentForward() {
        Vec3d vel = this.getVelocity();
        if (vel.lengthSquared() > 1.0E-4) {
            return vel.normalize();
        }

        float yawRad = (this.getYaw() + 90.0f) * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(Math.cos(yawRad), 0.0, Math.sin(yawRad)).normalize();
    }

    private static double horizontalDistanceSq(Vec3d a, Vec3d b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    private static double horizontalDotFromTarget(Vec3d pos, Vec3d target, Vec3d dir) {
        Vec3d rel = new Vec3d(pos.x - target.x, 0.0, pos.z - target.z);
        return rel.dotProduct(new Vec3d(dir.x, 0.0, dir.z).normalize());
    }

    private static double horizontalSideError(Vec3d pos, Vec3d target, Vec3d dir) {
        Vec3d rel = new Vec3d(pos.x - target.x, 0.0, pos.z - target.z);
        Vec3d side = new Vec3d(-dir.z, 0.0, dir.x).normalize();
        return Math.abs(rel.dotProduct(side));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "ufo_bomber.controller", 0, state -> {
            state.setAndContinue(ANIM_IDLE);
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
        nbt.putString("RunState", this.runState.name());
        nbt.putDouble("RunDirX", this.runDir.x);
        nbt.putDouble("RunDirY", this.runDir.y);
        nbt.putDouble("RunDirZ", this.runDir.z);
        nbt.putBoolean("BombDropped", this.bombDroppedThisPass);
        nbt.putInt("BombCooldown", this.bombCooldown);
        nbt.putFloat("VisualPitch", this.getVisualPitchRad());
        nbt.putFloat("VisualRoll", this.getVisualRollRad());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        try {
            this.runState = RunState.valueOf(nbt.getString("RunState"));
        } catch (Exception ignored) {
            this.runState = RunState.CRUISE;
        }

        this.runDir = new Vec3d(
                nbt.getDouble("RunDirX"),
                nbt.getDouble("RunDirY"),
                nbt.getDouble("RunDirZ")
        );
        this.bombDroppedThisPass = nbt.getBoolean("BombDropped");
        this.bombCooldown = nbt.getInt("BombCooldown");
        this.dataTracker.set(VISUAL_PITCH, nbt.getFloat("VisualPitch"));
        this.dataTracker.set(VISUAL_ROLL, nbt.getFloat("VisualRoll"));
        this.setNoGravity(true);
    }
}