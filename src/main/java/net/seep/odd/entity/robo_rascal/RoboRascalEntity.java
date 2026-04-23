package net.seep.odd.entity.robo_rascal;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.entity.scared_rascal_fight.ScaredRascalFightEntity;
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

public final class RoboRascalEntity extends PathAwareEntity implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");
    private static final RawAnimation SUPLEX = RawAnimation.begin().thenPlay("suplex");
    private static final RawAnimation SUPLEX_FAIL = RawAnimation.begin().thenPlay("suplex_fail");
    private static final RawAnimation SUPLEX_SUCCESS = RawAnimation.begin().thenPlay("suplex_success");

    private static final TrackedData<Optional<UUID>> QUEST_OWNER =
            DataTracker.registerData(RoboRascalEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Optional<UUID>> TARGET_RASCAL =
            DataTracker.registerData(RoboRascalEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Integer> ATTACK_STATE =
            DataTracker.registerData(RoboRascalEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> ATTACK_TICKS =
            DataTracker.registerData(RoboRascalEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final int STATE_NONE = 0;
    private static final int STATE_ATTACK = 1;
    private static final int STATE_SUPLEX = 2;
    private static final int STATE_SUPLEX_FAIL = 3;
    private static final int STATE_SUPLEX_SUCCESS = 4;

    private static final int ATTACK_TOTAL_TICKS = 34;
    private static final int ATTACK_HIT_ELAPSED = 5;
    private static final int SUPLEX_TOTAL_TICKS = 28;
    private static final int SUPLEX_GRAB_ELAPSED = 11;
    private static final int SUPLEX_FAIL_TOTAL_TICKS = 11;
    private static final int SUPLEX_SUCCESS_TOTAL_TICKS = 24;
    private static final double MOVE_SPEED = 1.04D;
    private static final double ATTACK_RANGE = 3.2D;
    private static final double GRAB_RANGE = 2.4D;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int actionCooldownTicks = 0;
    @Nullable
    private UUID heldRascalUuid;

    public RoboRascalEntity(EntityType<? extends RoboRascalEntity> type, World world) {
        super(type, world);
        this.setStepHeight(1.1F);
        this.experiencePoints = 0;
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 110.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.26D)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 10.0D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.85D);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(QUEST_OWNER, Optional.empty());
        this.dataTracker.startTracking(TARGET_RASCAL, Optional.empty());
        this.dataTracker.startTracking(ATTACK_STATE, STATE_NONE);
        this.dataTracker.startTracking(ATTACK_TICKS, 0);
    }

    public void assignQuestOwner(ServerPlayerEntity player) {
        this.dataTracker.set(QUEST_OWNER, Optional.of(player.getUuid()));
        this.setPersistent();
    }

    @Nullable
    public UUID getQuestOwnerUuid() {
        return this.dataTracker.get(QUEST_OWNER).orElse(null);
    }

    public void setTargetRascalUuid(@Nullable UUID uuid) {
        this.dataTracker.set(TARGET_RASCAL, Optional.ofNullable(uuid));
    }

    @Nullable
    public UUID getTargetRascalUuid() {
        return this.dataTracker.get(TARGET_RASCAL).orElse(null);
    }

    public int getAttackState() {
        return this.dataTracker.get(ATTACK_STATE);
    }

    private void setAttackState(int state) {
        this.dataTracker.set(ATTACK_STATE, state);
    }

    public int getAttackTicks() {
        return this.dataTracker.get(ATTACK_TICKS);
    }

    private void setAttackTicks(int ticks) {
        this.dataTracker.set(ATTACK_TICKS, Math.max(0, ticks));
    }

    @Nullable
    private ScaredRascalFightEntity getTrackedRascal() {
        UUID uuid = this.getTargetRascalUuid();
        if (uuid == null || !(this.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld)) {
            return null;
        }
        Entity entity = serverWorld.getEntity(uuid);
        return entity instanceof ScaredRascalFightEntity rascal ? rascal : null;
    }

    @Nullable
    private ScaredRascalFightEntity getHeldRascal() {
        if (this.heldRascalUuid == null || !(this.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld)) {
            return null;
        }
        Entity entity = serverWorld.getEntity(this.heldRascalUuid);
        return entity instanceof ScaredRascalFightEntity rascal ? rascal : null;
    }

    private boolean canStartAction(ScaredRascalFightEntity rascal) {
        return rascal != null
                && rascal.isAlive()
                && !rascal.isRemoved()
                && this.canSee(rascal)
                && this.squaredDistanceTo(rascal) <= ATTACK_RANGE * ATTACK_RANGE;
    }

    private boolean canGrab(ScaredRascalFightEntity rascal) {
        return rascal != null
                && rascal.isAlive()
                && !rascal.isRemoved()
                && !rascal.isHeldByRobo()
                && this.canSee(rascal)
                && this.squaredDistanceTo(rascal) <= GRAB_RANGE * GRAB_RANGE
                && this.getBoundingBox().expand(0.45D).intersects(rascal.getBoundingBox());
    }

    private void startAttack() {
        this.setAttackState(STATE_ATTACK);
        this.setAttackTicks(ATTACK_TOTAL_TICKS);
        this.actionCooldownTicks = 24;
        this.getNavigation().stop();
    }

    private void startSuplex() {
        this.setAttackState(STATE_SUPLEX);
        this.setAttackTicks(SUPLEX_TOTAL_TICKS);
        this.actionCooldownTicks = 32;
        this.getNavigation().stop();
    }

    private void startSuplexFail() {
        this.setAttackState(STATE_SUPLEX_FAIL);
        this.setAttackTicks(SUPLEX_FAIL_TOTAL_TICKS);
    }

    private void startSuplexSuccess(ScaredRascalFightEntity rascal) {
        this.setAttackState(STATE_SUPLEX_SUCCESS);
        this.setAttackTicks(SUPLEX_SUCCESS_TOTAL_TICKS);
        this.heldRascalUuid = rascal.getUuid();
        rascal.setHeldByRobo(true);
        rascal.setFollowActive(false);
        rascal.setEscortActive(false);
    }

    private void releaseHeldRascal() {
        ScaredRascalFightEntity held = getHeldRascal();
        if (held != null) {
            held.setHeldByRobo(false);
        }
        this.heldRascalUuid = null;
        this.setNoGravity(false);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient) {
            return;
        }

        if (this.getQuestOwnerUuid() != null) {
            this.setPersistent();
        }

        if (this.actionCooldownTicks > 0) {
            this.actionCooldownTicks--;
        }

        int attackState = this.getAttackState();
        if (attackState != STATE_NONE) {
            tickCurrentAction();
            return;
        }

        ScaredRascalFightEntity rascal = getTrackedRascal();
        if (rascal == null || !rascal.isAlive() || rascal.isRemoved()) {
            this.getNavigation().stop();
            return;
        }

        this.getLookControl().lookAt(rascal, 30.0F, 30.0F);
        this.getNavigation().startMovingTo(rascal, MOVE_SPEED);

        if (this.actionCooldownTicks <= 0 && canStartAction(rascal)) {
            if (this.random.nextFloat() < 0.36F) {
                startSuplex();
            } else {
                startAttack();
            }
        }
    }

    private void tickCurrentAction() {
        int state = this.getAttackState();
        int ticks = this.getAttackTicks();
        int total = switch (state) {
            case STATE_ATTACK -> ATTACK_TOTAL_TICKS;
            case STATE_SUPLEX -> SUPLEX_TOTAL_TICKS;
            case STATE_SUPLEX_FAIL -> SUPLEX_FAIL_TOTAL_TICKS;
            case STATE_SUPLEX_SUCCESS -> SUPLEX_SUCCESS_TOTAL_TICKS;
            default -> 0;
        };
        int elapsed = total - ticks;

        ScaredRascalFightEntity rascal = getTrackedRascal();
        if (rascal != null) {
            this.getLookControl().lookAt(rascal, 30.0F, 30.0F);
        }

        switch (state) {
            case STATE_ATTACK -> {
                if (elapsed == ATTACK_HIT_ELAPSED) {
                    performSweepHit();
                }
            }
            case STATE_SUPLEX -> {
                if (elapsed == SUPLEX_GRAB_ELAPSED) {
                    if (rascal != null && canGrab(rascal)) {
                        startSuplexSuccess(rascal);
                        return;
                    }
                }
            }
            case STATE_SUPLEX_SUCCESS -> {
                tickSuplexSuccess(elapsed);
            }
            default -> {
            }
        }

        this.setAttackTicks(ticks - 1);
        if (this.getAttackTicks() > 0) {
            return;
        }

        switch (state) {
            case STATE_SUPLEX -> startSuplexFail();
            case STATE_SUPLEX_SUCCESS -> {
                performSuplexCrash();
                this.setAttackState(STATE_NONE);
            }
            default -> this.setAttackState(STATE_NONE);
        }
    }

    private void performSweepHit() {
        Vec3d facing = this.getRotationVec(1.0F);
        Box box = this.getBoundingBox().expand(3.0D, 1.2D, 3.0D);

        for (LivingEntity target : this.getWorld().getEntitiesByClass(LivingEntity.class, box, entity ->
                entity != this && entity.isAlive() && !(entity instanceof RoboRascalEntity))) {
            Vec3d offset = target.getPos().subtract(this.getPos());
            if (offset.lengthSquared() < 0.01D) {
                continue;
            }
            Vec3d horizontal = new Vec3d(offset.x, 0.0D, offset.z).normalize();
            double dot = facing.x * horizontal.x + facing.z * horizontal.z;
            if (dot < 0.10D) {
                continue;
            }

            float damage = target instanceof ScaredRascalFightEntity ? 9.0F : 7.0F;
            target.damage(this.getDamageSources().mobAttack(this), damage);
            Vec3d knock = horizontal.multiply(1.1D);
            target.addVelocity(knock.x, 0.22D, knock.z);
        }
    }

    private void tickSuplexSuccess(int elapsed) {
        ScaredRascalFightEntity held = getHeldRascal();
        if (held == null) {
            this.setAttackState(STATE_NONE);
            this.setAttackTicks(0);
            this.setNoGravity(false);
            return;
        }

        held.refreshPositionAndAngles(this.getX(), this.getY() + this.getHeight() * 0.75D, this.getZ(), held.getYaw(), held.getPitch());
        held.setVelocity(Vec3d.ZERO);

        if (elapsed == 3) {
            this.setNoGravity(true);
            this.setVelocity(0.0D, 1.02D, 0.0D);
        } else if (elapsed >= 6 && elapsed < 17) {
            this.setNoGravity(true);
            this.setVelocity(0.0D, 0.0D, 0.0D);
        } else if (elapsed == 17) {
            this.setNoGravity(false);
            this.setVelocity(0.0D, 0.0D, 0.0D);
        }
    }

    private void performSuplexCrash() {
        ScaredRascalFightEntity held = getHeldRascal();
        if (held != null) {
            held.damage(this.getDamageSources().mobAttack(this), 13.0F);
        }

        Box box = this.getBoundingBox().expand(2.6D, 1.2D, 2.6D);
        for (LivingEntity target : this.getWorld().getEntitiesByClass(LivingEntity.class, box, entity ->
                entity != this && entity.isAlive() && !(entity instanceof RoboRascalEntity))) {
            if (held != null && target == held) {
                continue;
            }
            float damage = target instanceof ScaredRascalFightEntity ? 8.0F : 6.0F;
            target.damage(this.getDamageSources().mobAttack(this), damage);
            Vec3d away = target.getPos().subtract(this.getPos());
            if (away.lengthSquared() > 0.01D) {
                away = away.normalize().multiply(1.25D);
                target.addVelocity(away.x, 0.5D, away.z);
            }
        }

        releaseHeldRascal();
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new HoldLookGoal(this));
        this.goalSelector.add(2, new LookAroundGoal(this));
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean result = super.damage(source, amount);
        if (result && this.getHealth() <= 0.0F) {
            releaseHeldRascal();
        }
        return result;
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        releaseHeldRascal();
        super.remove(reason);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_IRON_GOLEM_REPAIR;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_IRON_GOLEM_DEATH;
    }

    @Override
    public int getMinAmbientSoundDelay() {
        return 120;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (this.getQuestOwnerUuid() != null) nbt.putUuid("QuestOwner", this.getQuestOwnerUuid());
        if (this.getTargetRascalUuid() != null) nbt.putUuid("TargetRascal", this.getTargetRascalUuid());
        if (this.heldRascalUuid != null) nbt.putUuid("HeldRascal", this.heldRascalUuid);
        nbt.putInt("AttackState", this.getAttackState());
        nbt.putInt("AttackTicks", this.getAttackTicks());
        nbt.putInt("ActionCooldownTicks", this.actionCooldownTicks);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.dataTracker.set(QUEST_OWNER, nbt.containsUuid("QuestOwner") ? Optional.of(nbt.getUuid("QuestOwner")) : Optional.empty());
        this.dataTracker.set(TARGET_RASCAL, nbt.containsUuid("TargetRascal") ? Optional.of(nbt.getUuid("TargetRascal")) : Optional.empty());
        this.dataTracker.set(ATTACK_STATE, nbt.getInt("AttackState"));
        this.dataTracker.set(ATTACK_TICKS, nbt.getInt("AttackTicks"));
        this.actionCooldownTicks = nbt.getInt("ActionCooldownTicks");
        this.heldRascalUuid = nbt.containsUuid("HeldRascal") ? nbt.getUuid("HeldRascal") : null;
        if (this.getQuestOwnerUuid() != null) {
            this.setPersistent();
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "robo_rascal.controller", 0, state -> {
            switch (this.getAttackState()) {
                case STATE_ATTACK -> state.setAndContinue(ATTACK);
                case STATE_SUPLEX -> state.setAndContinue(SUPLEX);
                case STATE_SUPLEX_FAIL -> state.setAndContinue(SUPLEX_FAIL);
                case STATE_SUPLEX_SUCCESS -> state.setAndContinue(SUPLEX_SUCCESS);
                default -> {
                    if (state.isMoving()) {
                        state.setAndContinue(WALK);
                    } else {
                        state.setAndContinue(IDLE);
                    }
                }
            }
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    static final class HoldLookGoal extends Goal {
        private final RoboRascalEntity robo;

        HoldLookGoal(RoboRascalEntity robo) {
            this.robo = robo;
            this.setControls(EnumSet.of(Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return true;
        }

        @Override
        public boolean shouldContinue() {
            return true;
        }

        @Override
        public void tick() {
            ScaredRascalFightEntity target = this.robo.getTrackedRascal();
            if (target != null) {
                this.robo.getLookControl().lookAt(target, 30.0F, 30.0F);
            }
        }
    }
}
