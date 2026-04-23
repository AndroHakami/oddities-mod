package net.seep.odd.entity.rake;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
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
import net.minecraft.world.World;
import net.seep.odd.entity.scared_rascal.ScaredRascalEntity;
import net.seep.odd.quest.types.ScaredRascalQuestLogic;
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

public final class RakeEntity extends PathAwareEntity implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation RUN_ATTACK = RawAnimation.begin().thenPlay("run_attack");

    private static final TrackedData<Optional<UUID>> QUEST_OWNER =
            DataTracker.registerData(RakeEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Optional<UUID>> TARGET_RASCAL =
            DataTracker.registerData(RakeEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Integer> ATTACK_TICKS =
            DataTracker.registerData(RakeEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final double CHASE_SPEED = 1.30D;
    private static final double START_ATTACK_RADIUS = 1.55D;
    private static final double REAL_HIT_RADIUS = 1.35D;
    private static final int ATTACK_ANIM_TICKS = 12;
    private static final int ATTACK_HIT_TICK = 6;
    private static final int PLAYER_FOCUS_GROUP_RADIUS = 7;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Nullable
    private UUID pendingAttackTargetUuid;
    private int attackCooldownTicks = 0;

    public RakeEntity(EntityType<? extends RakeEntity> type, World world) {
        super(type, world);
        this.setStepHeight(1.0F);
        this.experiencePoints = 0;
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.30D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(QUEST_OWNER, Optional.empty());
        this.dataTracker.startTracking(TARGET_RASCAL, Optional.empty());
        this.dataTracker.startTracking(ATTACK_TICKS, 0);
    }

    @Nullable
    public UUID getQuestOwnerUuid() {
        return this.dataTracker.get(QUEST_OWNER).orElse(null);
    }

    public void assignQuestOwner(ServerPlayerEntity player) {
        this.dataTracker.set(QUEST_OWNER, Optional.of(player.getUuid()));
        this.setPersistent();
    }

    @Nullable
    public UUID getTargetRascalUuid() {
        return this.dataTracker.get(TARGET_RASCAL).orElse(null);
    }

    public void setTargetRascalUuid(@Nullable UUID uuid) {
        this.dataTracker.set(TARGET_RASCAL, Optional.ofNullable(uuid));
    }

    public int getAttackTicks() {
        return this.dataTracker.get(ATTACK_TICKS);
    }

    public void setAttackTicks(int ticks) {
        this.dataTracker.set(ATTACK_TICKS, Math.max(0, ticks));
    }

    @Nullable
    private ServerPlayerEntity getQuestOwnerPlayer() {
        UUID uuid = this.getQuestOwnerUuid();
        if (uuid == null || !(this.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld)) {
            return null;
        }
        return serverWorld.getServer().getPlayerManager().getPlayer(uuid);
    }

    @Nullable
    private ScaredRascalEntity getTrackedRascal() {
        UUID uuid = this.getTargetRascalUuid();
        if (uuid == null || !(this.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld)) {
            return null;
        }
        Entity entity = serverWorld.getEntity(uuid);
        return entity instanceof ScaredRascalEntity rascal ? rascal : null;
    }

    @Nullable
    private Entity getPendingAttackTarget() {
        if (this.pendingAttackTargetUuid == null || !(this.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld)) {
            return null;
        }
        return serverWorld.getEntity(this.pendingAttackTargetUuid);
    }

    @Nullable
    private Entity resolveChaseTarget() {
        ServerPlayerEntity owner = getQuestOwnerPlayer();
        ScaredRascalEntity rascal = getTrackedRascal();

        boolean ownerValid = owner != null && owner.isAlive() && !owner.isSpectator() && owner.getWorld() == this.getWorld();
        boolean rascalValid = rascal != null && rascal.isAlive() && !rascal.isRemoved() && rascal.getWorld() == this.getWorld();

        if (ownerValid && rascalValid) {
            double groupRadiusSq = PLAYER_FOCUS_GROUP_RADIUS * PLAYER_FOCUS_GROUP_RADIUS;
            if (owner.squaredDistanceTo(rascal) <= groupRadiusSq) {
                return owner;
            }
            double toOwner = this.squaredDistanceTo(owner);
            double toRascal = this.squaredDistanceTo(rascal);
            return toOwner <= toRascal + 4.0D ? owner : rascal;
        }
        if (ownerValid) return owner;
        if (rascalValid) return rascal;
        return null;
    }

    private boolean canStartAttackOn(Entity target) {
        return target != null
                && target.isAlive()
                && !target.isRemoved()
                && this.canSee(target)
                && this.squaredDistanceTo(target) <= START_ATTACK_RADIUS * START_ATTACK_RADIUS;
    }

    private boolean actuallyHit(Entity target) {
        return target != null
                && target.isAlive()
                && !target.isRemoved()
                && this.canSee(target)
                && this.squaredDistanceTo(target) <= REAL_HIT_RADIUS * REAL_HIT_RADIUS
                && this.getBoundingBox().expand(0.35D).intersects(target.getBoundingBox());
    }

    private void startAttack(Entity target) {
        this.pendingAttackTargetUuid = target.getUuid();
        this.setAttackTicks(ATTACK_ANIM_TICKS);
        this.attackCooldownTicks = ATTACK_ANIM_TICKS + 4;
        this.getNavigation().stop();
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

        if (this.attackCooldownTicks > 0) {
            this.attackCooldownTicks--;
        }

        int attackTicks = this.getAttackTicks();
        if (attackTicks > 0) {
            Entity strikeTarget = getPendingAttackTarget();
            this.setAttackTicks(attackTicks - 1);

            if (strikeTarget != null) {
                this.getLookControl().lookAt(strikeTarget, 30.0F, 30.0F);
            }

            if (attackTicks == ATTACK_HIT_TICK && strikeTarget != null && actuallyHit(strikeTarget)) {
                ServerPlayerEntity owner = getQuestOwnerPlayer();
                if (owner != null) {
                    ScaredRascalQuestLogic.onRakeCaught(owner);
                }
            }

            if (attackTicks - 1 <= 0) {
                this.pendingAttackTargetUuid = null;
            }
            return;
        }

        Entity target = resolveChaseTarget();
        if (target == null) {
            this.getNavigation().stop();
            return;
        }

        this.getLookControl().lookAt(target, 30.0F, 30.0F);
        this.getNavigation().startMovingTo(target, CHASE_SPEED);

        if (this.attackCooldownTicks <= 0 && canStartAttackOn(target)) {
            startAttack(target);
        }
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new HoldLookGoal(this));
        this.goalSelector.add(2, new LookAroundGoal(this));
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_WARDEN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_WARDEN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_WARDEN_DEATH;
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
        if (this.pendingAttackTargetUuid != null) nbt.putUuid("PendingAttackTarget", this.pendingAttackTargetUuid);
        nbt.putInt("AttackTicks", this.getAttackTicks());
        nbt.putInt("AttackCooldownTicks", this.attackCooldownTicks);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.dataTracker.set(QUEST_OWNER, nbt.containsUuid("QuestOwner") ? Optional.of(nbt.getUuid("QuestOwner")) : Optional.empty());
        this.dataTracker.set(TARGET_RASCAL, nbt.containsUuid("TargetRascal") ? Optional.of(nbt.getUuid("TargetRascal")) : Optional.empty());
        this.pendingAttackTargetUuid = nbt.containsUuid("PendingAttackTarget") ? nbt.getUuid("PendingAttackTarget") : null;
        this.dataTracker.set(ATTACK_TICKS, nbt.getInt("AttackTicks"));
        this.attackCooldownTicks = nbt.getInt("AttackCooldownTicks");
        if (this.getQuestOwnerUuid() != null) {
            this.setPersistent();
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "rake.controller", 0, state -> {
            if (this.getAttackTicks() > 0) {
                state.setAndContinue(RUN_ATTACK);
                return PlayState.CONTINUE;
            }
            if (state.isMoving()) {
                state.setAndContinue(RUN);
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

    static final class HoldLookGoal extends Goal {
        private final RakeEntity rake;

        HoldLookGoal(RakeEntity rake) {
            this.rake = rake;
            this.setControls(EnumSet.of(Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return true;
        }

        @Override
        public void tick() {
            Entity target = this.rake.getAttackTicks() > 0 ? this.rake.getPendingAttackTarget() : this.rake.resolveChaseTarget();
            if (target != null) {
                this.rake.getLookControl().lookAt(target, 30.0F, 30.0F);
            }
        }
    }
}
