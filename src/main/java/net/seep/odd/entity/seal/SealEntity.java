package net.seep.odd.entity.seal;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.JukeboxBlock;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SitGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.pathing.AmphibiousSwimNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
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

public final class SealEntity extends TameableEntity implements GeoEntity {

    private static final RawAnimation IDLE        = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK        = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation SWIM        = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation SIT_TRANS   = RawAnimation.begin().thenPlay("transition_to_sitting");
    private static final RawAnimation SIT_LOOP    = RawAnimation.begin().thenLoop("sitting");
    private static final RawAnimation DANCE_TRANS = RawAnimation.begin().thenPlay("transition_to_dance");
    private static final RawAnimation DANCE_LOOP  = RawAnimation.begin().thenLoop("dancing");

    private static final int SIT_TRANSITION_TICKS_MAX = 14;
    private static final int DANCE_TRANSITION_TICKS_MAX = 46;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final TrackedData<Integer> VARIANT =
            DataTracker.registerData(SealEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final TrackedData<Boolean> VARIANT_SET =
            DataTracker.registerData(SealEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final TrackedData<Integer> SIT_TRANSITION_TICKS =
            DataTracker.registerData(SealEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final TrackedData<Boolean> DANCING =
            DataTracker.registerData(SealEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final TrackedData<Integer> DANCE_TRANSITION_TICKS =
            DataTracker.registerData(SealEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private SitGoal sitGoal;

    public SealEntity(EntityType<? extends TameableEntity> type, World world) {
        super(type, world);
        this.setTamed(false);

        this.setStepHeight(1.25f);

        // Don't hate water or water edges
        this.setPathfindingPenalty(PathNodeType.WATER, 0.0F);
        this.setPathfindingPenalty(PathNodeType.WATER_BORDER, 0.0F);

        // Make air/open-edge nodes less awkward too
        this.setPathfindingPenalty(PathNodeType.OPEN, 0.0F);
        this.setPathfindingPenalty(PathNodeType.WALKABLE, 0.0F);
    }

    public static DefaultAttributeContainer.Builder createSealAttributes() {
        return TameableEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        AmphibiousSwimNavigation nav = new AmphibiousSwimNavigation(this, world);
        nav.setCanSwim(true);
        return nav;
    }

    @Override
    public boolean canBreatheInWater() {
        return true;
    }

    @Override
    public int getSafeFallDistance() {
        return 8;
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(VARIANT, 0);
        this.dataTracker.startTracking(VARIANT_SET, false);
        this.dataTracker.startTracking(SIT_TRANSITION_TICKS, 0);
        this.dataTracker.startTracking(DANCING, false);
        this.dataTracker.startTracking(DANCE_TRANSITION_TICKS, 0);
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

        if (this.random.nextInt(20) == 0) this.setVariantId(3);
        else this.setVariantId(this.random.nextInt(3));
    }

    @Override
    @Nullable
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
        this.goalSelector.add(2, new SealFollowOwnerGoal(this, 1.0D, 4.0F, 16.0F));

        this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.6D) {
            @Override
            public boolean canStart() {
                return !SealEntity.this.isInSittingPose()
                        && !SealEntity.this.isDancingFlag()
                        && super.canStart();
            }

            @Override
            public boolean shouldContinue() {
                return !SealEntity.this.isInSittingPose()
                        && !SealEntity.this.isDancingFlag()
                        && super.shouldContinue();
            }
        });

        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 6.0F));
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
                this.getNavigation().stop();
            } else {
                this.setSitTransitionTicks(0);
            }

            this.playSound(SoundEvents.ENTITY_CAT_AMBIENT, 0.6f, 1.0f);
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
    public SealEntity createChild(ServerWorld world, PassiveEntity entity) {
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

    @Override
    public void tick() {
        super.tick();

        if (!this.getWorld().isClient) {
            if ((this.age % 10) == 0) {
                boolean shouldDance = this.isJukeboxWithRecordNearby(6);

                if (shouldDance && !this.isDancingFlag()) {
                    this.setDancingFlag(true);
                    this.setDanceTransitionTicks(DANCE_TRANSITION_TICKS_MAX);
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
                this.setVelocity(0.0, this.getVelocity().y, 0.0);
            }
        }
    }

    @Override
    public void travel(Vec3d movementInput) {
        if (this.isInSittingPose() || this.isDancingFlag()) {
            super.travel(Vec3d.ZERO);
            return;
        }

        if (this.isLogicalSideForUpdatingMovement() && this.isTouchingWater()) {
            this.setSwimming(this.isSubmergedInWater());

            this.updateVelocity(0.04F, movementInput);
            this.move(MovementType.SELF, this.getVelocity());

            Vec3d vel = this.getVelocity().multiply(0.90D, 0.80D, 0.90D);

            if (this.getNavigation().isIdle()) {
                vel = vel.add(0.0D, -0.01D, 0.0D);
            }

            this.setVelocity(vel);
            this.setAir(this.getMaxAir());
            return;
        }

        this.setSwimming(false);
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

            boolean moving = state.isMoving() || this.getVelocity().horizontalLengthSquared() > 1.0e-4;

            if (this.isTouchingWater() && moving) {
                state.setAndContinue(SWIM);
                return PlayState.CONTINUE;
            }

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
    }

    private static final class SealFollowOwnerGoal extends Goal {
        private final SealEntity seal;
        private final double speed;
        private final EntityNavigation navigation;
        private final float minDistance;
        private final float maxDistance;
        private LivingEntity owner;
        private int updateCountdownTicks;

        private SealFollowOwnerGoal(SealEntity seal, double speed, float minDistance, float maxDistance) {
            this.seal = seal;
            this.speed = speed;
            this.navigation = seal.getNavigation();
            this.minDistance = minDistance;
            this.maxDistance = maxDistance;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            LivingEntity owner = this.seal.getOwner();
            if (owner == null) return false;
            if (this.seal.isInSittingPose() || this.seal.isDancingFlag()) return false;

            double distSq = this.seal.squaredDistanceTo(owner);
            if (distSq < (double) (this.minDistance * this.minDistance)) return false;

            this.owner = owner;
            return true;
        }

        @Override
        public boolean shouldContinue() {
            if (this.owner == null) return false;
            if (this.seal.isInSittingPose() || this.seal.isDancingFlag()) return false;

            double distSq = this.seal.squaredDistanceTo(this.owner);

            // continue until actually close enough, not until "farther than max"
            return distSq > (double) (this.minDistance * this.minDistance);
        }

        @Override
        public void start() {
            this.updateCountdownTicks = 0;
        }

        @Override
        public void stop() {
            this.owner = null;
            this.navigation.stop();
        }

        @Override
        public void tick() {
            if (this.owner == null) return;

            this.seal.getLookControl().lookAt(this.owner, 10.0F, this.seal.getMaxLookPitchChange());

            if (--this.updateCountdownTicks > 0) return;
            this.updateCountdownTicks = this.getTickCount(10);

            double distSq = this.seal.squaredDistanceTo(this.owner);

            if (distSq >= (double) (this.maxDistance * this.maxDistance)) {
                this.tryTeleportNearOwner();
                return;
            }

            this.navigation.startMovingTo(this.owner, this.speed);
        }

        private void tryTeleportNearOwner() {
            if (this.owner == null) return;

            BlockPos ownerPos = this.owner.getBlockPos();

            for (int dx = -2; dx <= 2; ++dx) {
                for (int dz = -2; dz <= 2; ++dz) {
                    if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;

                    for (int dy = -1; dy <= 1; ++dy) {
                        BlockPos pos = ownerPos.add(dx, dy, dz);
                        if (!this.canTeleportTo(pos)) continue;

                        this.seal.refreshPositionAndAngles(
                                pos.getX() + 0.5D,
                                pos.getY(),
                                pos.getZ() + 0.5D,
                                this.seal.getYaw(),
                                this.seal.getPitch()
                        );
                        this.navigation.stop();
                        return;
                    }
                }
            }
        }

        private boolean canTeleportTo(BlockPos pos) {
            BlockPos below = pos.down();
            World world = this.seal.getWorld();

            if (!world.getBlockState(below).isSolidBlock(world, below)) return false;
            if (!world.isAir(pos) || !world.isAir(pos.up())) return false;

            Vec3d offset = new Vec3d(
                    pos.getX() + 0.5D - this.seal.getX(),
                    pos.getY() - this.seal.getY(),
                    pos.getZ() + 0.5D - this.seal.getZ()
            );

            return world.isSpaceEmpty(this.seal, this.seal.getBoundingBox().offset(offset));
        }
    }
}