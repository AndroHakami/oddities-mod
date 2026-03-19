// FILE: src/main/java/net/seep/odd/entity/skull_bird/SkullBirdEntity.java
package net.seep.odd.entity.skull_bird;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.JukeboxBlock;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SitGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EntityView;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

public final class SkullBirdEntity extends TameableEntity implements GeoEntity {

    private static final RawAnimation IDLE        = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK        = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation FLYING      = RawAnimation.begin().thenLoop("flying");
    private static final RawAnimation SIT_TRANS   = RawAnimation.begin().thenPlay("transition_to_sitting");
    private static final RawAnimation SIT_LOOP    = RawAnimation.begin().thenLoop("sitting");
    private static final RawAnimation DANCE_TRANS = RawAnimation.begin().thenPlay("transition_to_dancing");
    private static final RawAnimation DANCE_LOOP  = RawAnimation.begin().thenLoop("dancing");

    // 1.36 sec ~= 27 ticks
    private static final int SIT_TRANSITION_TICKS_MAX = 27;
    // 0.2 sec ~= 4 ticks
    private static final int DANCE_TRANSITION_TICKS_MAX = 4;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final TrackedData<Integer> VARIANT =
            DataTracker.registerData(SkullBirdEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final TrackedData<Boolean> VARIANT_SET =
            DataTracker.registerData(SkullBirdEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final TrackedData<Integer> SIT_TRANSITION_TICKS =
            DataTracker.registerData(SkullBirdEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final TrackedData<Boolean> DANCING =
            DataTracker.registerData(SkullBirdEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final TrackedData<Integer> DANCE_TRANSITION_TICKS =
            DataTracker.registerData(SkullBirdEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final TrackedData<Boolean> FLYING_FLAG =
            DataTracker.registerData(SkullBirdEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private SitGoal sitGoal;

    public SkullBirdEntity(EntityType<? extends TameableEntity> type, World world) {
        super(type, world);

        this.setTamed(false);
        this.moveControl = new FlightMoveControl(this, 20, true);
        this.setStepHeight(1.0f);

        this.setPathfindingPenalty(PathNodeType.WATER, 0.0F);
        this.setPathfindingPenalty(PathNodeType.WATER_BORDER, 0.0F);
        this.setPathfindingPenalty(PathNodeType.OPEN, 0.0F);
        this.setPathfindingPenalty(PathNodeType.WALKABLE, 0.0F);
    }

    public static DefaultAttributeContainer.Builder createSkullBirdAttributes() {
        return TameableEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 18.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.24D)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 0.45D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        BirdNavigation nav = new BirdNavigation(this, world);
        nav.setCanPathThroughDoors(false);
        nav.setCanEnterOpenDoors(true);
        nav.setCanSwim(true);
        return nav;
    }

    @Override
    public int getSafeFallDistance() {
        return 8;
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(VARIANT, 0);
        this.dataTracker.startTracking(VARIANT_SET, false);
        this.dataTracker.startTracking(SIT_TRANSITION_TICKS, 0);
        this.dataTracker.startTracking(DANCING, false);
        this.dataTracker.startTracking(DANCE_TRANSITION_TICKS, 0);
        this.dataTracker.startTracking(FLYING_FLAG, false);
    }

    public int getVariantId() {
        return this.dataTracker.get(VARIANT);
    }

    public void setVariantId(int id) {
        int clamped = Math.max(0, Math.min(3, id));
        this.dataTracker.set(VARIANT, clamped);
        this.dataTracker.set(VARIANT_SET, true);
    }

    public void ensureVariantPicked() {
        if (this.dataTracker.get(VARIANT_SET)) return;

        // 3 normal, 1 rare
        if (this.random.nextInt(20) == 0) this.setVariantId(3);
        else this.setVariantId(this.random.nextInt(3));
    }

    @Nullable
    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty,
                                 SpawnReason spawnReason,
                                 @Nullable EntityData entityData,
                                 @Nullable NbtCompound entityNbt) {
        EntityData data = super.initialize(world, difficulty, spawnReason, entityData, entityNbt);

        if (entityNbt != null && entityNbt.contains("Variant")) {
            this.setVariantId(entityNbt.getInt("Variant"));
        } else {
            this.ensureVariantPicked();
        }

        return data;
    }

    @Override
    protected void initGoals() {
        this.sitGoal = new SitGoal(this);

        this.goalSelector.add(1, this.sitGoal);
        this.goalSelector.add(2, new SkullBirdFollowOwnerGoal(this, 1.15D, 4.0F, 18.0F));

        this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.7D) {
            @Override
            public boolean canStart() {
                return !SkullBirdEntity.this.isInSittingPose()
                        && !SkullBirdEntity.this.isDancingFlag()
                        && !SkullBirdEntity.this.isFlyingFlag()
                        && super.canStart();
            }

            @Override
            public boolean shouldContinue() {
                return !SkullBirdEntity.this.isInSittingPose()
                        && !SkullBirdEntity.this.isDancingFlag()
                        && !SkullBirdEntity.this.isFlyingFlag()
                        && super.shouldContinue();
            }
        });

        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 7.0F));
        this.goalSelector.add(9, new LookAroundGoal(this));
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

        if (!this.getWorld().isClient) {
            if (this.isTamed() && !this.isOwner(player)) {
                return ActionResult.PASS;
            }

            boolean newSit = !this.isInSittingPose();

            this.setSitting(newSit);
            this.setInSittingPose(newSit);

            if (newSit) {
                this.setSitTransitionTicks(SIT_TRANSITION_TICKS_MAX);
                this.setFlyingFlag(false);
                this.getNavigation().stop();
            } else {
                this.setSitTransitionTicks(0);
            }

            this.playSound(SoundEvents.ENTITY_PARROT_AMBIENT, 0.6f, 1.0f);
            return ActionResult.CONSUME;
        }

        return ActionResult.SUCCESS;
    }

    @Override
    public boolean canAttackWithOwner(LivingEntity target, LivingEntity owner) {
        return false;
    }

    @Override
    public boolean isBreedingItem(ItemStack stack) {
        return false;
    }

    @Nullable
    @Override
    public SkullBirdEntity createChild(ServerWorld world, PassiveEntity entity) {
        return null;
    }

    public int getSitTransitionTicks() {
        return this.dataTracker.get(SIT_TRANSITION_TICKS);
    }

    private void setSitTransitionTicks(int ticks) {
        this.dataTracker.set(SIT_TRANSITION_TICKS, Math.max(0, ticks));
    }

    public boolean isDancingFlag() {
        return this.dataTracker.get(DANCING);
    }

    private void setDancingFlag(boolean value) {
        this.dataTracker.set(DANCING, value);
    }

    public int getDanceTransitionTicks() {
        return this.dataTracker.get(DANCE_TRANSITION_TICKS);
    }

    private void setDanceTransitionTicks(int ticks) {
        this.dataTracker.set(DANCE_TRANSITION_TICKS, Math.max(0, ticks));
    }

    public boolean isFlyingFlag() {
        return this.dataTracker.get(FLYING_FLAG);
    }

    private void setFlyingFlag(boolean value) {
        this.dataTracker.set(FLYING_FLAG, value);
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.getWorld().isClient) {
            if ((this.age % 10) == 0) {
                boolean shouldDance = this.isJukeboxWithRecordNearby(6);

                if (shouldDance && !this.isDancingFlag()) {
                    this.setDancingFlag(true);
                    this.setDanceTransitionTicks(DANCE_TRANSITION_TICKS_MAX);
                    this.setFlyingFlag(false);
                    this.getNavigation().stop();
                } else if (!shouldDance && this.isDancingFlag()) {
                    this.setDancingFlag(false);
                    this.setDanceTransitionTicks(0);
                }
            }

            int sitTicks = this.getSitTransitionTicks();
            if (sitTicks > 0) this.setSitTransitionTicks(sitTicks - 1);

            int danceTicks = this.getDanceTransitionTicks();
            if (danceTicks > 0) this.setDanceTransitionTicks(danceTicks - 1);

            if (this.isInSittingPose() || this.isDancingFlag()) {
                this.getNavigation().stop();
                this.setFlyingFlag(false);
                this.setVelocity(0.0, this.getVelocity().y, 0.0);
            } else {
                LivingEntity owner = this.getOwner();

                if (!this.isOnGround()) {
                    this.setFlyingFlag(true);
                } else if (owner != null) {
                    double distSq = this.squaredDistanceTo(owner);
                    double dy = owner.getY() - this.getY();

                    if (dy > 1.5D || distSq > 36.0D) {
                        this.setFlyingFlag(true);
                    } else if (distSq < 9.0D && this.isOnGround()) {
                        this.setFlyingFlag(false);
                    }
                } else if (this.isOnGround() && this.getNavigation().isIdle()) {
                    this.setFlyingFlag(false);
                }
            }
        }

        boolean flyNow = this.isFlyingFlag() && !this.isInSittingPose() && !this.isDancingFlag();
        this.setNoGravity(flyNow);

        if (!flyNow && this.isOnGround()) {
            this.fallDistance = 0.0f;
        }
    }

    @Override
    public void travel(Vec3d movementInput) {
        if (this.isInSittingPose() || this.isDancingFlag()) {
            super.travel(Vec3d.ZERO);
            return;
        }

        if (this.isFlyingFlag()) {
            if (this.isLogicalSideForUpdatingMovement()) {
                this.updateVelocity(0.08F, movementInput);
                this.move(MovementType.SELF, this.getVelocity());
                this.setVelocity(this.getVelocity().multiply(0.91D));
            } else {
                this.move(MovementType.SELF, this.getVelocity());
                this.setVelocity(this.getVelocity().multiply(0.98D));
            }
            return;
        }

        super.travel(movementInput);
    }

    private boolean isJukeboxWithRecordNearby(int radius) {
        BlockPos base = this.getBlockPos();

        for (BlockPos p : BlockPos.iterate(base.add(-radius, -radius, -radius), base.add(radius, radius, radius))) {
            BlockState state = this.getWorld().getBlockState(p);
            if (state.isOf(Blocks.JUKEBOX)
                    && state.contains(JukeboxBlock.HAS_RECORD)
                    && state.get(JukeboxBlock.HAS_RECORD)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            if (this.isDancingFlag()) {
                if (this.getDanceTransitionTicks() > 0) {
                    state.setAndContinue(DANCE_TRANS);
                } else {
                    state.setAndContinue(DANCE_LOOP);
                }
                return PlayState.CONTINUE;
            }

            if (this.isInSittingPose()) {
                if (this.getSitTransitionTicks() > 0) {
                    state.setAndContinue(SIT_TRANS);
                } else {
                    state.setAndContinue(SIT_LOOP);
                }
                return PlayState.CONTINUE;
            }

            if (this.isFlyingFlag()) {
                state.setAndContinue(FLYING);
                return PlayState.CONTINUE;
            }

            boolean moving = state.isMoving() || this.getVelocity().horizontalLengthSquared() > 1.0e-4;
            if (moving) {
                state.setAndContinue(WALK);
                return PlayState.CONTINUE;
            }

            state.setAndContinue(IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public EntityView method_48926() {
        return this.getWorld();
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Variant", this.getVariantId());
        nbt.putBoolean("Sitting", this.isInSittingPose());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);

        if (nbt.contains("Variant")) {
            this.setVariantId(nbt.getInt("Variant"));
        } else {
            this.dataTracker.set(VARIANT_SET, false);
        }

        if (nbt.contains("Sitting")) {
            boolean sit = nbt.getBoolean("Sitting");
            this.setSitting(sit);
            this.setInSittingPose(sit);
        }

        this.setSitTransitionTicks(0);
        this.setDancingFlag(false);
        this.setDanceTransitionTicks(0);
        this.setFlyingFlag(false);
    }

    private static final class SkullBirdFollowOwnerGoal extends Goal {
        private final SkullBirdEntity bird;
        private final double speed;
        private final EntityNavigation navigation;
        private final float minDistance;
        private final float maxDistance;
        private LivingEntity owner;
        private int updateCountdownTicks;

        private SkullBirdFollowOwnerGoal(SkullBirdEntity bird, double speed, float minDistance, float maxDistance) {
            this.bird = bird;
            this.speed = speed;
            this.navigation = bird.getNavigation();
            this.minDistance = minDistance;
            this.maxDistance = maxDistance;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            LivingEntity owner = this.bird.getOwner();
            if (owner == null) return false;
            if (this.bird.isInSittingPose() || this.bird.isDancingFlag()) return false;

            double distSq = this.bird.squaredDistanceTo(owner);
            if (distSq < (double) (this.minDistance * this.minDistance)) return false;

            this.owner = owner;
            return true;
        }

        @Override
        public boolean shouldContinue() {
            if (this.owner == null) return false;
            if (this.bird.isInSittingPose() || this.bird.isDancingFlag()) return false;

            double distSq = this.bird.squaredDistanceTo(this.owner);
            return distSq > (double) (this.minDistance * this.minDistance);
        }

        @Override
        public void start() {
            this.updateCountdownTicks = 0;
        }

        @Override
        public void stop() {
            this.owner = null;
            this.bird.setFlyingFlag(false);
            this.navigation.stop();
        }

        @Override
        public void tick() {
            if (this.owner == null) return;

            this.bird.getLookControl().lookAt(this.owner, 10.0F, this.bird.getMaxLookPitchChange());

            if (--this.updateCountdownTicks > 0) return;
            this.updateCountdownTicks = this.getTickCount(6);

            double distSq = this.bird.squaredDistanceTo(this.owner);
            double targetY = this.owner.getY() + this.owner.getHeight() * 0.7D;
            double dy = targetY - this.bird.getY();

            if (distSq >= (double) (this.maxDistance * this.maxDistance)) {
                this.tryTeleportNearOwner();
                return;
            }

            boolean shouldFly = dy > 1.25D || !this.bird.isOnGround() || distSq > 25.0D;

            if (shouldFly) {
                this.bird.setFlyingFlag(true);
                this.navigation.stop();

                this.bird.getMoveControl().moveTo(
                        this.owner.getX(),
                        targetY,
                        this.owner.getZ(),
                        this.speed * 1.15D
                );

                Vec3d dir = new Vec3d(
                        this.owner.getX() - this.bird.getX(),
                        targetY - this.bird.getY(),
                        this.owner.getZ() - this.bird.getZ()
                );

                if (dir.lengthSquared() > 1.0e-6) {
                    this.bird.setVelocity(this.bird.getVelocity().add(dir.normalize().multiply(0.08D)));
                }
            } else {
                this.bird.setFlyingFlag(false);
                this.navigation.startMovingTo(this.owner, this.speed);
            }
        }

        private void tryTeleportNearOwner() {
            if (this.owner == null) return;

            BlockPos ownerPos = this.owner.getBlockPos();

            for (int dx = -2; dx <= 2; ++dx) {
                for (int dz = -2; dz <= 2; ++dz) {
                    if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;

                    for (int dy = 0; dy <= 2; ++dy) {
                        BlockPos pos = ownerPos.add(dx, dy, dz);
                        if (!this.canTeleportTo(pos)) continue;

                        this.bird.refreshPositionAndAngles(
                                pos.getX() + 0.5D,
                                pos.getY(),
                                pos.getZ() + 0.5D,
                                this.bird.getYaw(),
                                this.bird.getPitch()
                        );
                        this.bird.setFlyingFlag(false);
                        this.navigation.stop();
                        return;
                    }
                }
            }
        }

        private boolean canTeleportTo(BlockPos pos) {
            World world = this.bird.getWorld();

            if (!world.isAir(pos) || !world.isAir(pos.up())) return false;

            Vec3d offset = new Vec3d(
                    pos.getX() + 0.5D - this.bird.getX(),
                    pos.getY() - this.bird.getY(),
                    pos.getZ() + 0.5D - this.bird.getZ()
            );

            return world.isSpaceEmpty(this.bird, this.bird.getBoundingBox().offset(offset));
        }
    }
}