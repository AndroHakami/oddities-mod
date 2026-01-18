package net.seep.odd.entity.seal;

import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SitGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.EntityView;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class SealEntity extends TameableEntity implements GeoEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Variant tracked data (int)
    private static final TrackedData<Integer> VARIANT =
            DataTracker.registerData(SealEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private SitGoal sitGoal;

    public SealEntity(EntityType<? extends TameableEntity> type, World world) {
        super(type, world);
        this.setTamed(false);
    }

    public static DefaultAttributeContainer.Builder createSealAttributes() {
        return TameableEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0D);
    }

    /* ---------------- Variant helpers ---------------- */

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        // Default variant (0). You can map this to "grey" in your model.
        this.dataTracker.startTracking(VARIANT, 0);
    }

    public int getVariantId() {
        return this.dataTracker.get(VARIANT);
    }

    public void setVariantId(int id) {
        // clamp to 0..3 (change if you add more)
        int clamped = Math.max(0, Math.min(3, id));
        this.dataTracker.set(VARIANT, clamped);
    }

    private void pickRandomVariantIfUnset(@Nullable NbtCompound entityNbt) {
        // If entity came with NBT Variant already, keep it.
        if (entityNbt != null && entityNbt.contains("Variant")) return;

        // 4 variants: 0..3
        this.setVariantId(this.random.nextInt(4));
    }

    @Nullable

    public EntityData initialize(net.minecraft.server.world.ServerWorld world, LocalDifficulty difficulty,
                                 SpawnReason spawnReason, @Nullable EntityData entityData, @Nullable NbtCompound entityNbt) {
        EntityData data = super.initialize(world, difficulty, spawnReason, entityData, entityNbt);
        pickRandomVariantIfUnset(entityNbt);
        return data;
    }

    /* ---------------- AI ---------------- */

    @Override
    protected void initGoals() {
        this.sitGoal = new SitGoal(this);
        this.goalSelector.add(1, this.sitGoal);

        // Follow owner (like wolf/cat). Only runs when tamed + not sitting.
        this.goalSelector.add(2, new FollowOwnerGoal(this, 1.0D, 4.0F, 16.0F, false));

        // Passive vibes only
        this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.6D));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 6.0F));
        this.goalSelector.add(9, new LookAroundGoal(this));
    }

    /**
     * Right-click by owner toggles sit/stand.
     */
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!this.getWorld().isClient) {
            if (this.isTamed() && this.isOwner(player)) {
                boolean newSit = !this.isSitting();
                this.setSitting(newSit);
                this.setInSittingPose(newSit);

                // When standing up, nudge navigation so follow kicks quickly
                if (!newSit) {
                    this.getNavigation().stop();
                }

                this.playSound(SoundEvents.ENTITY_CAT_AMBIENT, 0.6f, 1.0f);
                return ActionResult.CONSUME;
            }
        }
        return super.interactMob(player, hand);
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
    public SealEntity createChild(net.minecraft.server.world.ServerWorld world, net.minecraft.entity.passive.PassiveEntity entity) {
        return null;
    }

    /* ---------------- NBT ---------------- */

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Variant", this.getVariantId());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Variant")) {
            this.setVariantId(nbt.getInt("Variant"));
        }
    }

    /* ---------------- GeckoLib ---------------- */

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> {
            state.setAndContinue(IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // Fix for that required synthetic method
    @Override
    public EntityView method_48926() {
        return this.getWorld();
    }
}
