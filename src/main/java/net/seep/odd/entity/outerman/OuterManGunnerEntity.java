package net.seep.odd.entity.outerman;

import net.minecraft.entity.EntityData;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.RangedAttackMob;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.ProjectileAttackGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.outerblaster.BlasterProjectileEntity;
import net.seep.odd.item.ModItems;
import net.seep.odd.sound.ModSounds;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class OuterManGunnerEntity extends PathAwareEntity implements GeoEntity, RangedAttackMob {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation FIRE = RawAnimation.begin().thenPlay("fire");

    private static final TrackedData<Integer> FIRE_TIME =
            DataTracker.registerData(OuterManGunnerEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final float PROJECTILE_SPEED = 3.2f;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int fireTicks;

    public OuterManGunnerEntity(net.minecraft.entity.EntityType<? extends OuterManGunnerEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 6;
        this.setCanPickUpLoot(false);
        this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.05f);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 24.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.30D)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 2.0D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.15D);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(FIRE_TIME, 0);
    }

    private void setFireAnim(int ticks) {
        this.fireTicks = ticks;
        this.dataTracker.set(FIRE_TIME, ticks);
    }

    private int getFireAnim() {
        return this.dataTracker.get(FIRE_TIME);
    }

    @Override
    protected void initGoals() {
        // ~2 seconds between shots
        this.goalSelector.add(1, new ProjectileAttackGoal(this, 1.0D, 40, 20.0f));

        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true,
                p -> p.isAlive() && !p.isSpectator()));

        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 12.0f));
        this.goalSelector.add(9, new LookAroundGoal(this));
    }

    @Nullable
    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
                                 @Nullable EntityData entityData, @Nullable NbtCompound entityNbt) {
        EntityData data = super.initialize(world, difficulty, spawnReason, entityData, entityNbt);

        this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.OUTER_BLASTER));
        this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.05f);

        return data;
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.getWorld().isClient && fireTicks > 0) {
            fireTicks--;
            this.dataTracker.set(FIRE_TIME, fireTicks);
        }

        LivingEntity target = this.getTarget();
        if (target != null) {
            this.getLookControl().lookAt(target, 30.0f, 30.0f);
        }
    }

    @Override
    public void attack(LivingEntity target, float pullProgress) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        if (!this.isAlive() || target == null || !target.isAlive()) return;

        if (!this.getMainHandStack().isOf(ModItems.OUTER_BLASTER)) {
            this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.OUTER_BLASTER));
            this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.05f);
        }

        this.setFireAnim(8);

        BlasterProjectileEntity projectile = ModEntities.BLASTER_PROJECTILE.create(sw);
        if (projectile == null) return;

        Vec3d from = getGunMuzzlePos();
        Vec3d targetPos = target.getEyePos().add(target.getVelocity().multiply(0.18));
        Vec3d dir = targetPos.subtract(from);

        if (dir.lengthSquared() < 1.0E-6) {
            dir = this.getRotationVec(1.0f);
        } else {
            dir = dir.normalize();
        }

        projectile.setOwner(this);
        projectile.setPos(from.x, from.y, from.z);
        projectile.setVelocity(dir.multiply(PROJECTILE_SPEED));
        projectile.syncRotationToVelocity();

        sw.spawnEntity(projectile);

        float pitch = 0.95f + (this.random.nextFloat() * 0.18f);
        sw.playSound(
                null,
                this.getX(), this.getY(), this.getZ(),
                ModSounds.OUTER_BLASTER_FIRE,
                SoundCategory.HOSTILE,
                0.90f,
                pitch
        );
    }

    private Vec3d getGunMuzzlePos() {
        Vec3d look = this.getRotationVec(1.0f).normalize();
        Vec3d up = new Vec3d(0.0, 1.0, 0.0);
        Vec3d right = look.crossProduct(up);

        if (right.lengthSquared() < 1.0E-6) {
            right = new Vec3d(1.0, 0.0, 0.0);
        } else {
            right = right.normalize();
        }

        return this.getEyePos()
                .add(look.multiply(0.95))
                .add(right.multiply(-0.28))
                .add(0.0, -0.20, 0.0);
    }

    @Override
    public void onDeath(net.minecraft.entity.damage.DamageSource source) {
        super.onDeath(source);

        if (!this.getWorld().isClient && this.getWorld() instanceof ServerWorld sw) {
            int pearlCount = this.random.nextInt(2) + 1;
            for (int i = 0; i < pearlCount; i++) {
                this.dropStack(new ItemStack(ModItems.ALIEN_PEARL));
            }
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("FireTime", fireTicks);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.fireTicks = nbt.getInt("FireTime");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle_controller", 0, state -> {
            state.setAndContinue(IDLE);
            return PlayState.CONTINUE;
        }));

        controllers.add(new AnimationController<>(this, "walk_controller", 0, state -> {
            if (state.isMoving()) {
                state.setAndContinue(WALK);
                return PlayState.CONTINUE;
            }

            state.getController().forceAnimationReset();
            return PlayState.STOP;
        }));

        controllers.add(new AnimationController<>(this, "fire_controller", 0, state -> {
            if (getFireAnim() > 0) {
                state.setAndContinue(FIRE);
                return PlayState.CONTINUE;
            }

            state.getController().forceAnimationReset();
            return PlayState.STOP;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}