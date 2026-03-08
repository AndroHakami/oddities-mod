// FILE: src/main/java/net/seep/odd/entity/whiskers/WhiskersEntity.java
package net.seep.odd.entity.whiskers;

import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SitGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.EntityView;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class WhiskersEntity extends TameableEntity implements GeoEntity {

    // ===== Animations =====
    private static final RawAnimation IDLE      = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK      = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation SIT_TRANS = RawAnimation.begin().thenPlay("transition_to_sit");
    private static final RawAnimation SIT_LOOP  = RawAnimation.begin().thenLoop("sitting");

    private static final int SIT_TRANSITION_TICKS_MAX = 26;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // ===== Tracked data =====
    private static final TrackedData<Integer> VARIANT =
            DataTracker.registerData(WhiskersEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final TrackedData<Boolean> VARIANT_SET =
            DataTracker.registerData(WhiskersEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final TrackedData<Integer> SIT_TRANSITION_TICKS =
            DataTracker.registerData(WhiskersEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private SitGoal sitGoal;

    public WhiskersEntity(EntityType<? extends TameableEntity> type, World world) {
        super(type, world);
        this.setTamed(false);
        this.setStepHeight(1.0f);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return TameableEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 16.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 20.0D);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(VARIANT, 0);
        this.dataTracker.startTracking(VARIANT_SET, false);
        this.dataTracker.startTracking(SIT_TRANSITION_TICKS, 0);
    }

    /* ---------------- Variant ---------------- */

    public int getVariantId() { return this.dataTracker.get(VARIANT); }

    public void setVariantId(int id) {
        int clamped = Math.max(0, Math.min(3, id));
        this.dataTracker.set(VARIANT, clamped);
        this.dataTracker.set(VARIANT_SET, true);
    }

    public void ensureVariantPicked() {
        if (this.dataTracker.get(VARIANT_SET)) return;

        // same logic as Booklet (you can change later)
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
            setVariantId(entityNbt.getInt("Variant"));
        } else {
            ensureVariantPicked();
        }

        return data;
    }

    /* ---------------- AI ---------------- */

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SwimGoal(this));

        this.sitGoal = new SitGoal(this);
        this.goalSelector.add(2, this.sitGoal);

        this.goalSelector.add(3, new FollowOwnerGoal(this, 1.05D, 4.0F, 14.0F, false) {
            @Override
            public boolean canStart() {
                return !WhiskersEntity.this.isInSittingPose() && super.canStart();
            }

            @Override
            public boolean shouldContinue() {
                return !WhiskersEntity.this.isInSittingPose() && super.shouldContinue();
            }
        });

        this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.7D) {
            @Override public boolean canStart() {
                return !WhiskersEntity.this.isInSittingPose() && super.canStart();
            }
            @Override public boolean shouldContinue() {
                return !WhiskersEntity.this.isInSittingPose() && super.shouldContinue();
            }
        });

        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 7.0F));
        this.goalSelector.add(9, new LookAroundGoal(this));
    }

    @Override
    public boolean canAttackWithOwner(net.minecraft.entity.LivingEntity target, net.minecraft.entity.LivingEntity owner) {
        return false;
    }

    @Override
    public boolean isBreedingItem(ItemStack stack) {
        return false;
    }

    @Nullable
    @Override
    public WhiskersEntity createChild(ServerWorld world, PassiveEntity entity) {
        return null;
    }

    /* ---------------- Sit transition ticks ---------------- */

    public int getSitTransitionTicks() { return this.dataTracker.get(SIT_TRANSITION_TICKS); }
    private void setSitTransitionTicks(int ticks) { this.dataTracker.set(SIT_TRANSITION_TICKS, Math.max(0, ticks)); }

    /* ---------------- interaction ---------------- */

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

        if (!this.getWorld().isClient) {
            // If tamed, only owner can toggle sit
            if (this.isTamed() && !this.isOwner(player)) {
                return ActionResult.PASS;
            }

            boolean newSit = !this.isInSittingPose();
            this.setSitting(newSit);
            this.setInSittingPose(newSit);

            if (newSit) {
                setSitTransitionTicks(SIT_TRANSITION_TICKS_MAX);
                this.getNavigation().stop();
            } else {
                setSitTransitionTicks(0);
            }

            this.playSound(SoundEvents.ENTITY_CAT_PURR, 0.7f, 0.9f + this.random.nextFloat() * 0.2f);
            return ActionResult.CONSUME;
        }

        return ActionResult.SUCCESS;
    }

    /* ---------------- tick ---------------- */

    @Override
    public void tick() {
        super.tick();

        if (!this.getWorld().isClient) {
            int t = getSitTransitionTicks();
            if (t > 0) setSitTransitionTicks(t - 1);

            // freeze while sitting
            if (this.isInSittingPose()) {
                this.getNavigation().stop();
                this.setVelocity(0.0, this.getVelocity().y, 0.0);
            }
        }
    }

    /* ---------------- sounds ---------------- */

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_CAT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(net.minecraft.entity.damage.DamageSource source) {
        return SoundEvents.ENTITY_CAT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_CAT_DEATH;
    }

    /* ---------------- GeckoLib ---------------- */

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {

        // MAIN controller: idle vs sitting (full body)
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            if (this.isInSittingPose()) {
                if (getSitTransitionTicks() > 0) state.setAndContinue(SIT_TRANS);
                else state.setAndContinue(SIT_LOOP);
                return PlayState.CONTINUE;
            }

            state.setAndContinue(IDLE);
            return PlayState.CONTINUE;
        }));

        // WALK overlay: legs-only while moving
        controllers.add(new AnimationController<>(this, "walk", 0, state -> {
            if (this.isInSittingPose()) return PlayState.STOP;

            boolean moving = state.isMoving() || this.getVelocity().horizontalLengthSquared() > 1.0e-4;
            if (moving) {
                state.setAndContinue(WALK);
                return PlayState.CONTINUE;
            }

            return PlayState.STOP;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // (same pattern you used in BookletEntity)
    @Override
    public EntityView method_48926() {
        return this.getWorld();
    }

    /* ---------------- NBT ---------------- */

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Variant", getVariantId());
        nbt.putBoolean("Sitting", this.isInSittingPose());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);

        if (nbt.contains("Variant")) setVariantId(nbt.getInt("Variant"));
        else this.dataTracker.set(VARIANT_SET, false);

        if (nbt.contains("Sitting")) {
            boolean sit = nbt.getBoolean("Sitting");
            this.setSitting(sit);
            this.setInSittingPose(sit);
        }

        setSitTransitionTicks(0);
    }
}