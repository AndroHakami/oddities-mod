package net.seep.odd.entity.star_ride;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.entity.librarian.LibrarianEntity;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

public final class StarRideEntity extends PathAwareEntity implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private static final TrackedData<Boolean> QUEST_LOCKED =
            DataTracker.registerData(StarRideEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> PLAYER_MOUNTABLE =
            DataTracker.registerData(StarRideEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Optional<UUID>> QUEST_OWNER =
            DataTracker.registerData(StarRideEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Boolean> RACE_MODE =
            DataTracker.registerData(StarRideEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    // Player feel
    private static final double BASE_RIDE_SPEED = 0.816D; // 20% slower than 1.02
    private static final float PLAYER_ACCEL_GROUND = 0.0145F; // ~3.5s to full
    private static final float PLAYER_ACCEL_AIR = 0.0105F;
    private static final double PLAYER_FORWARD_DAMP = 0.972D;
    private static final double PLAYER_DRIFT_DAMP = 0.988D;

    // AI feel
    private static final double AI_RACE_SPEED = 1.52D;
    private static final double AI_PREP_SPEED = 1.28D;
    private static final double AI_ACCEL = 0.085D;
    private static final double NODE_REACHED_SQ = 1.90D * 1.90D;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Nullable
    private BlockPos aiRaceTarget;
    private boolean aiRaceActive;
    private int repathCooldown;
    private int noProgressTicks;
    private int approachCursor;
    private Vec3d lastProgressPos = Vec3d.ZERO;

    private float smoothedForward;
    private float smoothedSideways;

    public StarRideEntity(EntityType<? extends StarRideEntity> type, World world) {
        super(type, world);
        this.setStepHeight(0.0F);
        this.experiencePoints = 0;
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 28.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, BASE_RIDE_SPEED)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 96.0D);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(QUEST_LOCKED, false);
        this.dataTracker.startTracking(PLAYER_MOUNTABLE, true);
        this.dataTracker.startTracking(QUEST_OWNER, Optional.empty());
        this.dataTracker.startTracking(RACE_MODE, false);
    }

    public boolean isQuestLocked() {
        return this.dataTracker.get(QUEST_LOCKED);
    }

    public void setQuestLocked(boolean value) {
        this.dataTracker.set(QUEST_LOCKED, value);
        if (value) {
            this.setVelocity(0.0D, Math.min(this.getVelocity().y, 0.0D), 0.0D);
            this.smoothedForward = 0.0F;
            this.smoothedSideways = 0.0F;
        }
    }

    public boolean isPlayerMountable() {
        return this.dataTracker.get(PLAYER_MOUNTABLE);
    }

    public void setPlayerMountable(boolean value) {
        this.dataTracker.set(PLAYER_MOUNTABLE, value);
    }

    public boolean isRaceMode() {
        return this.dataTracker.get(RACE_MODE);
    }

    public void setRaceMode(boolean value) {
        this.dataTracker.set(RACE_MODE, value);
    }

    @Nullable
    public UUID getQuestOwnerUuid() {
        return this.dataTracker.get(QUEST_OWNER).orElse(null);
    }

    public void assignQuestOwner(PlayerEntity player) {
        this.dataTracker.set(QUEST_OWNER, Optional.of(player.getUuid()));
        this.setPersistent();
    }

    public boolean isRaceAiActive() {
        return this.aiRaceActive && this.aiRaceTarget != null;
    }

    public void configureRaceAi(@Nullable BlockPos target) {
        this.aiRaceTarget = target;
        this.aiRaceActive = target != null;
        this.repathCooldown = 0;
        this.noProgressTicks = 0;
        this.approachCursor = 0;
        this.lastProgressPos = this.getPos();

        if (target == null) {
            this.getNavigation().stop();
        }
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new RaceToTargetGoal(this));
        this.goalSelector.add(2, new LookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.getWorld().isClient) {
            if (this.isRaceAiActive() && !(this.getFirstPassenger() instanceof PlayerEntity) && this.aiRaceTarget != null) {
                tickRaceAi();
            }

            if (this.isQuestLocked()) {
                Vec3d v = this.getVelocity();
                this.setVelocity(0.0D, Math.min(v.y, 0.0D), 0.0D);
            }
        }
    }

    private void tickRaceAi() {
        if (this.aiRaceTarget == null) return;

        LibrarianEntity librarian = resolveLibrarianTarget();
        double tx = this.aiRaceTarget.getX() + 0.5D;
        double tz = this.aiRaceTarget.getZ() + 0.5D;

        double movedSq = this.getPos().squaredDistanceTo(this.lastProgressPos);
        boolean stuckish = movedSq < 0.016D && this.getVelocity().horizontalLengthSquared() < 0.0025D;
        if (!this.isQuestLocked() && (stuckish || this.horizontalCollision)) {
            this.noProgressTicks++;
        } else {
            this.noProgressTicks = Math.max(0, this.noProgressTicks - 2);
            if (movedSq >= 0.016D || this.age % 8 == 0) {
                this.lastProgressPos = this.getPos();
            }
        }

        if (this.repathCooldown > 0) {
            this.repathCooldown--;
        }

        boolean forceRepath = this.repathCooldown <= 0
                || this.getNavigation().isIdle()
                || this.noProgressTicks >= 10
                || this.horizontalCollision;

        if (forceRepath) {
            double speed = this.isRaceMode() ? AI_RACE_SPEED : AI_PREP_SPEED;
            boolean started = false;

            if (librarian != null) {
                started = startTowardLibrarianApproach(librarian, speed, this.noProgressTicks >= 10 || this.horizontalCollision);
                if (!started) {
                    started = this.getNavigation().startMovingTo(librarian, speed);
                }
            }

            if (!started) {
                started = this.getNavigation().startMovingTo(tx, this.getY(), tz, speed);
            }

            this.repathCooldown = this.isQuestLocked() ? 10 : 5;
            if (this.noProgressTicks >= 10 || this.horizontalCollision) {
                this.approachCursor++;
            }
            this.noProgressTicks = 0;
        }

        if (this.isQuestLocked()) {
            this.setVelocity(0.0D, Math.min(this.getVelocity().y, 0.0D), 0.0D);
            return;
        }

        if (this.horizontalCollision) {
            hardBrakeFromWall();
            return;
        }

        Vec3d steerTarget = currentSteerTarget(tx, tz);
        steerToward(steerTarget, this.isRaceMode() ? AI_RACE_SPEED : AI_PREP_SPEED);
    }

    private void hardBrakeFromWall() {
        Vec3d vel = this.getVelocity();
        this.setVelocity(vel.x * 0.16D, Math.min(vel.y, -0.10D), vel.z * 0.16D);
        this.getNavigation().stop();
        this.repathCooldown = 0;
        this.noProgressTicks += 8;
    }

    private Vec3d currentSteerTarget(double fallbackX, double fallbackZ) {
        Path path = this.getNavigation().getCurrentPath();
        if (path != null && !path.isFinished()) {
            int idx = path.getCurrentNodeIndex();
            Vec3d nodePos = path.getNodePosition(this, idx);
            double nodeDistSq = this.squaredDistanceTo(nodePos.x, this.getY(), nodePos.z);
            if (nodeDistSq < NODE_REACHED_SQ && idx < path.getLength() - 1) {
                idx++;
                nodePos = path.getNodePosition(this, idx);
            }
            return new Vec3d(nodePos.x, this.getY(), nodePos.z);
        }
        return new Vec3d(fallbackX, this.getY(), fallbackZ);
    }

    private void steerToward(Vec3d target, double speed) {
        Vec3d to = new Vec3d(target.x - this.getX(), 0.0D, target.z - this.getZ());
        if (to.lengthSquared() < 0.0001D) {
            return;
        }

        float currentYaw = this.getYaw();
        Vec3d dir = to.normalize();
        float desiredYaw = (float) (MathHelper.atan2(dir.z, dir.x) * (180.0F / Math.PI)) - 90.0F;
        float yawDiff = MathHelper.wrapDegrees(desiredYaw - currentYaw);

        float yawStep = this.isRaceMode() ? 8.0F : 5.5F;
        float newYaw = currentYaw + MathHelper.clamp(yawDiff, -yawStep, yawStep);

        this.setYaw(newYaw);
        this.prevYaw = newYaw;
        this.bodyYaw = newYaw;
        this.headYaw = newYaw;

        // Forward-only thrust. Big turns brake first instead of snake-sliding sideways.
        double absDiff = Math.abs(yawDiff);
        double forwardScale = 1.0D - MathHelper.clamp((float) ((absDiff - 6.0D) / 84.0D), 0.0F, 1.0F) * 0.90D;
        if (absDiff > 100.0D) {
            forwardScale = 0.03D;
        }

        float yawRad = newYaw * 0.017453292F;
        Vec3d forward = new Vec3d(-MathHelper.sin(yawRad), 0.0D, MathHelper.cos(yawRad));

        double topSpeed = speed * 0.18D * forwardScale;
        Vec3d desired = forward.multiply(topSpeed);
        Vec3d current = this.getVelocity();
        Vec3d horiz = new Vec3d(current.x, 0.0D, current.z);

        Vec3d nextHoriz = new Vec3d(
                MathHelper.lerp(AI_ACCEL, horiz.x, desired.x),
                0.0D,
                MathHelper.lerp(AI_ACCEL, horiz.z, desired.z)
        );

        this.setVelocity(nextHoriz.x, current.y, nextHoriz.z);
    }

    @Nullable
    private LibrarianEntity resolveLibrarianTarget() {
        if (this.aiRaceTarget == null) return null;

        LibrarianEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LibrarianEntity librarian : this.getWorld().getEntitiesByClass(
                LibrarianEntity.class,
                this.getBoundingBox().expand(256.0D),
                entity -> !entity.isRemoved())) {
            double d = librarian.getBlockPos().getSquaredDistance(this.aiRaceTarget);
            if (d < bestDist) {
                bestDist = d;
                best = librarian;
            }
        }
        return best;
    }

    private boolean startTowardLibrarianApproach(LibrarianEntity librarian, double speed, boolean rotate) {
        BlockPos base = librarian.getBlockPos();
        int[][] offsets = new int[][]{
                { 4, 0}, {-4, 0}, {0, 4}, {0, -4},
                { 6, 0}, {-6, 0}, {0, 6}, {0, -6},
                { 4, 3}, {-4, 3}, {4, -3}, {-4, -3},
                { 6, 3}, {-6, 3}, {6, -3}, {-6, -3}
        };

        int start = rotate ? this.approachCursor : 0;
        for (int i = 0; i < offsets.length; i++) {
            int idx = (start + i) % offsets.length;
            BlockPos stand = findStandable(base.add(offsets[idx][0], 0, offsets[idx][1]));
            if (stand != null && this.getNavigation().startMovingTo(
                    stand.getX() + 0.5D,
                    stand.getY() + 1.0D,
                    stand.getZ() + 0.5D,
                    speed
            )) {
                this.approachCursor = idx;
                return true;
            }
        }
        return false;
    }

    @Nullable
    private BlockPos findStandable(BlockPos around) {
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos feet = around.up(dy);
            BlockPos below = feet.down();
            if (areaClearForRide(feet) && !this.getWorld().getBlockState(below).isAir()) {
                return below;
            }
        }
        return null;
    }

    private boolean areaClearForRide(BlockPos feet) {
        BlockPos head = feet.up();
        BlockPos above = head.up();

        int[][] checks = new int[][]{
                {0, 0},
                {1, 0}, {-1, 0},
                {0, 1}, {0, -1}
        };

        for (int[] check : checks) {
            int dx = check[0];
            int dz = check[1];
            if (!this.getWorld().getBlockState(feet.add(dx, 0, dz)).isAir()) return false;
            if (!this.getWorld().getBlockState(head.add(dx, 0, dz)).isAir()) return false;
            if (!this.getWorld().getBlockState(above.add(dx, 0, dz)).isAir()) return false;
        }
        return true;
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!this.isPlayerMountable()) return ActionResult.success(this.getWorld().isClient);
        UUID owner = this.getQuestOwnerUuid();
        if (owner != null && !owner.equals(player.getUuid())) return ActionResult.success(this.getWorld().isClient);
        if (!player.hasVehicle()) player.startRiding(this);
        return ActionResult.success(this.getWorld().isClient);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengerList().isEmpty();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (this.getWorld().isClient) {
            return true;
        }

        float reduced = Math.max(0.25F, amount * 0.08F);
        if (this.getHealth() <= reduced + 1.0F) {
            this.playSound(SoundEvents.ENTITY_ALLAY_HURT, 0.7F, 0.8F);
            return false;
        }
        return super.damage(source, reduced);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_ALLAY_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_ALLAY_DEATH;
    }

    @Override
    public void travel(Vec3d movementInput) {
        LivingEntity rider = this.getControllingPassenger();
        if (rider instanceof PlayerEntity player) {
            boolean drifting = !this.isQuestLocked() && player.isSprinting();

            float targetYaw = player.getYaw();
            float yawStep = drifting ? 3.0F : 7.0F;
            float smoothYaw = MathHelper.stepTowards(this.getYaw(), targetYaw, yawStep);
            this.setYaw(smoothYaw);
            this.prevYaw = smoothYaw;
            this.bodyYaw = smoothYaw;
            this.headYaw = smoothYaw;
            this.setPitch(player.getPitch() * 0.22F);

            float targetSideways = this.isQuestLocked() ? 0.0F : player.sidewaysSpeed * (drifting ? 0.82F : 0.26F);
            float targetForward = this.isQuestLocked() ? 0.0F : player.forwardSpeed * (drifting ? 0.72F : 1.0F);
            if (targetForward < 0.0F) {
                targetForward *= 0.18F;
            }

            float accel = this.isOnGround() ? PLAYER_ACCEL_GROUND : PLAYER_ACCEL_AIR;
            this.smoothedForward = approach(this.smoothedForward, targetForward, accel);
            this.smoothedSideways = approach(this.smoothedSideways, targetSideways, accel * (drifting ? 1.8F : 1.15F));

            this.setMovementSpeed((float) this.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED));
            super.travel(new Vec3d(this.smoothedSideways, movementInput.y, this.smoothedForward));
            applyLowGravity();
            applyAirDrag(drifting);

            if (this.horizontalCollision) {
                Vec3d vel = this.getVelocity();
                this.setVelocity(vel.x * 0.45D, Math.min(vel.y, -0.06D), vel.z * 0.45D);
            }
            return;
        }

        super.travel(movementInput);
        applyLowGravity();
        applyAirDrag(false);

        if (this.horizontalCollision) {
            Vec3d vel = this.getVelocity();
            this.setVelocity(vel.x * 0.45D, Math.min(vel.y, -0.06D), vel.z * 0.45D);
        }
    }

    private static float approach(float current, float target, float amount) {
        if (current < target) return Math.min(current + amount, target);
        return Math.max(current - amount, target);
    }

    private void applyLowGravity() {
        if (!this.isOnGround()) {
            Vec3d velocity = this.getVelocity();
            double y = Math.max(velocity.y - 0.0105D, -0.22D);
            if (this.horizontalCollision) {
                y = Math.min(y, -0.04D);
            }
            this.setVelocity(velocity.x, y, velocity.z);
        }
    }

    private void applyAirDrag(boolean drifting) {
        Vec3d vel = this.getVelocity();
        double drag = this.isOnGround()
                ? (drifting ? PLAYER_DRIFT_DAMP : PLAYER_FORWARD_DAMP)
                : (drifting ? 0.991D : 0.980D);
        this.setVelocity(vel.x * drag, vel.y, vel.z * drag);
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        Entity passenger = this.getFirstPassenger();
        return passenger instanceof LivingEntity living ? living : null;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("QuestLocked", this.isQuestLocked());
        nbt.putBoolean("PlayerMountable", this.isPlayerMountable());
        nbt.putBoolean("RaceMode", this.isRaceMode());
        if (this.getQuestOwnerUuid() != null) nbt.putUuid("QuestOwner", this.getQuestOwnerUuid());
        if (this.aiRaceTarget != null) {
            nbt.putBoolean("HasRaceTarget", true);
            nbt.putInt("RaceX", this.aiRaceTarget.getX());
            nbt.putInt("RaceY", this.aiRaceTarget.getY());
            nbt.putInt("RaceZ", this.aiRaceTarget.getZ());
        }
        nbt.putBoolean("AiRaceActive", this.aiRaceActive);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.setQuestLocked(nbt.getBoolean("QuestLocked"));
        this.setPlayerMountable(!nbt.contains("PlayerMountable") || nbt.getBoolean("PlayerMountable"));
        this.setRaceMode(nbt.getBoolean("RaceMode"));
        if (nbt.containsUuid("QuestOwner")) this.dataTracker.set(QUEST_OWNER, Optional.of(nbt.getUuid("QuestOwner")));
        if (nbt.getBoolean("HasRaceTarget")) {
            this.aiRaceTarget = new BlockPos(nbt.getInt("RaceX"), nbt.getInt("RaceY"), nbt.getInt("RaceZ"));
        }
        this.aiRaceActive = nbt.getBoolean("AiRaceActive");
        this.lastProgressPos = this.getPos();
        if (this.getQuestOwnerUuid() != null) {
            this.setPersistent();
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "star_ride.controller", 0, state -> {
            state.setAndContinue(IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public ItemStack getPickBlockStack() {
        return ItemStack.EMPTY;
    }

    static final class RaceToTargetGoal extends Goal {
        private final StarRideEntity ride;

        RaceToTargetGoal(StarRideEntity ride) {
            this.ride = ride;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return this.ride.isRaceAiActive() && !(this.ride.getFirstPassenger() instanceof PlayerEntity);
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public void tick() {
            // driven in StarRideEntity.tick()
        }
    }
}
