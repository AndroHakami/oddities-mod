package net.seep.odd.entity.skitter;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

public class SkitterEntity extends HostileEntity implements GeoEntity {

    // GeckoLib
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN  = RawAnimation.begin().thenLoop("run");

    private static final TrackedData<Boolean> RUNNING =
            DataTracker.registerData(SkitterEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    public SkitterEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.setStepHeight(1.1f);              // helps with roots clutter
        this.experiencePoints = 8;
    }

    /* ---------------- Attributes ---------------- */

    public static DefaultAttributeContainer.Builder createAttributes() {
        // Spider HP = 16.0
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 16.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 28.0)
                // Base speed; run happens via goal speed multiplier
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                // helps not get punched into the void
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.25);
    }

    /* ---------------- DataTracker ---------------- */

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(RUNNING, false);
    }

    public boolean isRunning() {
        return this.dataTracker.get(RUNNING);
    }

    private void setRunning(boolean v) {
        this.dataTracker.set(RUNNING, v);
    }

    /* ---------------- Goals ---------------- */

    @Override
    protected void initGoals() {
        // Target players
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));

        this.goalSelector.add(0, new SwimGoal(this));

        // Run + attack (fast like a player sprint “feel”)
        this.goalSelector.add(1, new SafeMeleeAttackGoal(this, 1.35, true));

        // Wander when idle (walk)
        this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.85));

        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 10f));
        this.goalSelector.add(7, new LookAroundGoal(this));
    }

    /** Same as MeleeAttackGoal but cancels movement if a cliff is ahead. */
    private static final class SafeMeleeAttackGoal extends MeleeAttackGoal {
        private final SkitterEntity skitter;

        SafeMeleeAttackGoal(SkitterEntity mob, double speed, boolean pauseWhenIdle) {
            super(mob, speed, pauseWhenIdle);
            this.skitter = mob;
        }

        @Override
        public void tick() {
            // If we'd run into a big drop, stop instead of suiciding.
            if (skitter.isOnGround() && skitter.isForwardDropUnsafe(2.3)) {
                skitter.getNavigation().stop();
                return;
            }
            super.tick();
        }
    }

    /* ---------------- Tick ---------------- */

    @Override
    public void tick() {
        super.tick();

        // Update "running" state (used for animation)
        LivingEntity t = this.getTarget();
        boolean wantsRun = t != null && t.isAlive() && this.canSee(t);

        // If cliff ahead, don’t mark running (also helps animation not “run in place”)
        if (this.isOnGround() && this.isForwardDropUnsafe(2.0)) wantsRun = false;

        setRunning(wantsRun);

        // Extra safety: if wandering/chasing and cliff ahead, stop pathing
        if (!getWorld().isClient && this.isOnGround() && this.isForwardDropUnsafe(2.0) && this.getNavigation().isFollowingPath()) {
            this.getNavigation().stop();
        }
    }

    /* ---------------- Cliff safety helpers ---------------- */

    private boolean isForwardDropUnsafe(double dist) {
        float yawRad = this.getYaw() * ((float)Math.PI / 180f);
        Vec3d fwd = new Vec3d(-MathHelper.sin(yawRad), 0, MathHelper.cos(yawRad));
        Vec3d left = new Vec3d(-fwd.z, 0, fwd.x);

        double halfW = this.getWidth() * 0.45;
        Vec3d base = this.getPos();

        return isPointDropUnsafe(base.add(fwd.multiply(dist)))
                || isPointDropUnsafe(base.add(fwd.multiply(dist)).add(left.multiply(halfW)))
                || isPointDropUnsafe(base.add(fwd.multiply(dist)).add(left.multiply(-halfW)));
    }

    private boolean isPointDropUnsafe(Vec3d pos) {
        BlockPos xz = BlockPos.ofFloored(pos.x, this.getY(), pos.z);
        BlockPos top = getWorld().getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, xz);
        return (this.getY() - top.getY()) > 3;
    }

    /* ---------------- Sounds ---------------- */

    // Skeleton-ish ambience, but lower pitch
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.ENTITY_SKELETON_AMBIENT; }
    @Override protected SoundEvent getDeathSound()   { return SoundEvents.ENTITY_SKELETON_DEATH; }

    // When hit: slime vibe
    @Override protected SoundEvent getHurtSound(net.minecraft.entity.damage.DamageSource source) {
        return SoundEvents.ENTITY_SLIME_HURT;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.ENTITY_SKELETON_STEP, 0.35f, this.getSoundPitch());
    }

    @Override
    public float getSoundPitch() {
        // “slightly lower pitched”
        return super.getSoundPitch() * 0.78f;
    }

    // Optional: if it DOES fall, don’t let it just die instantly in voidy terrain
    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, net.minecraft.entity.damage.DamageSource damageSource) {
        return false;
    }

    /* ---------------- GeckoLib ---------------- */

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "skitter.controller", 0, state -> {
            if (this.isRunning()) return state.setAndContinue(RUN);
            return state.isMoving() ? state.setAndContinue(WALK) : state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}