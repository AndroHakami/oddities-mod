package net.seep.odd.entity.scared_rascal;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.quest.QuestManager;
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

public final class ScaredRascalEntity extends PathAwareEntity implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");

    private static final TrackedData<Optional<UUID>> QUEST_OWNER =
            DataTracker.registerData(ScaredRascalEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Optional<UUID>> RAKE_UUID =
            DataTracker.registerData(ScaredRascalEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Boolean> CHASE_ACTIVE =
            DataTracker.registerData(ScaredRascalEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> ESCORT_ACTIVE =
            DataTracker.registerData(ScaredRascalEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final double BASE_MOVE_SPEED = 0.30D;
    private static final double ESCORT_SPEED = 1.22D;
    private static final double CHASE_FOLLOW_SPEED = 1.45D;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int speechTicks = 0;

    public ScaredRascalEntity(EntityType<? extends ScaredRascalEntity> type, World world) {
        super(type, world);
        this.setStepHeight(1.0F);
        this.experiencePoints = 0;
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 8.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, BASE_MOVE_SPEED)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(QUEST_OWNER, Optional.empty());
        this.dataTracker.startTracking(RAKE_UUID, Optional.empty());
        this.dataTracker.startTracking(CHASE_ACTIVE, false);
        this.dataTracker.startTracking(ESCORT_ACTIVE, false);
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
    public UUID getRakeUuid() {
        return this.dataTracker.get(RAKE_UUID).orElse(null);
    }

    public void setRakeUuid(@Nullable UUID uuid) {
        this.dataTracker.set(RAKE_UUID, Optional.ofNullable(uuid));
    }

    public boolean isChaseActive() {
        return this.dataTracker.get(CHASE_ACTIVE);
    }

    public void setChaseActive(boolean value) {
        this.dataTracker.set(CHASE_ACTIVE, value);
        if (!value) {
            this.getNavigation().stop();
        }
    }

    public boolean isEscortActive() {
        return this.dataTracker.get(ESCORT_ACTIVE);
    }

    public void setEscortActive(boolean value) {
        this.dataTracker.set(ESCORT_ACTIVE, value);
        if (!value) {
            this.getNavigation().stop();
        }
    }

    @Nullable
    public ServerPlayerEntity getQuestOwnerPlayer() {
        UUID uuid = this.getQuestOwnerUuid();
        if (uuid == null || !(this.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld)) {
            return null;
        }
        return serverWorld.getServer().getPlayerManager().getPlayer(uuid);
    }

    @Nullable
    public Entity getRakeEntity() {
        UUID uuid = this.getRakeUuid();
        if (uuid == null || !(this.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld)) {
            return null;
        }
        return serverWorld.getEntity(uuid);
    }

    public void speak(String text, int ticks) {
        this.setCustomName(Text.literal(text));
        this.setCustomNameVisible(true);
        this.speechTicks = Math.max(this.speechTicks, ticks);
    }

    public void clearSpeech() {
        this.speechTicks = 0;
        this.setCustomNameVisible(false);
        this.setCustomName(null);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient) {
            return;
        }

        if (this.speechTicks > 0) {
            this.speechTicks--;
            if (this.speechTicks <= 0) {
                clearSpeech();
            }
        }

        if (this.getQuestOwnerUuid() != null) {
            this.setPersistent();
        }
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new FollowOwnerDuringChaseGoal(this));
        this.goalSelector.add(2, new EscortOwnerGoal(this));
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 12.0F));
        this.goalSelector.add(4, new LookAroundGoal(this));
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (this.getWorld().isClient) {
            return ActionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }
        UUID owner = this.getQuestOwnerUuid();
        if (owner == null || !owner.equals(serverPlayer.getUuid())) {
            return ActionResult.PASS;
        }
        return ScaredRascalQuestLogic.onRascalInteracted(serverPlayer, this) ? ActionResult.SUCCESS : ActionResult.PASS;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
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
    public int getMinAmbientSoundDelay() {
        return 90;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (this.getQuestOwnerUuid() != null) nbt.putUuid("QuestOwner", this.getQuestOwnerUuid());
        if (this.getRakeUuid() != null) nbt.putUuid("RakeUuid", this.getRakeUuid());
        nbt.putBoolean("ChaseActive", this.isChaseActive());
        nbt.putBoolean("EscortActive", this.isEscortActive());
        nbt.putInt("SpeechTicks", this.speechTicks);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.dataTracker.set(QUEST_OWNER, nbt.containsUuid("QuestOwner") ? Optional.of(nbt.getUuid("QuestOwner")) : Optional.empty());
        this.dataTracker.set(RAKE_UUID, nbt.containsUuid("RakeUuid") ? Optional.of(nbt.getUuid("RakeUuid")) : Optional.empty());
        this.dataTracker.set(CHASE_ACTIVE, nbt.getBoolean("ChaseActive"));
        this.dataTracker.set(ESCORT_ACTIVE, nbt.getBoolean("EscortActive"));
        this.speechTicks = nbt.getInt("SpeechTicks");
        clearSpeech();
        if (this.getQuestOwnerUuid() != null) {
            this.setPersistent();
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "scared_rascal.controller", 0, state -> {
            if (state.isMoving()) {
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

    static final class EscortOwnerGoal extends Goal {
        private final ScaredRascalEntity rascal;
        @Nullable
        private ServerPlayerEntity owner;

        EscortOwnerGoal(ScaredRascalEntity rascal) {
            this.rascal = rascal;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (!this.rascal.isEscortActive() || this.rascal.isChaseActive()) return false;
            this.owner = this.rascal.getQuestOwnerPlayer();
            return this.owner != null && this.owner.isAlive() && !this.owner.isSpectator();
        }

        @Override
        public boolean shouldContinue() {
            return this.rascal.isEscortActive() && !this.rascal.isChaseActive()
                    && this.owner != null && this.owner.isAlive() && !this.owner.isSpectator();
        }

        @Override
        public void tick() {
            if (this.owner == null) return;

            this.rascal.getLookControl().lookAt(this.owner, 20.0F, this.rascal.getMaxLookPitchChange());
            double distSq = this.rascal.squaredDistanceTo(this.owner);
            if (distSq > 2.0D * 2.0D) {
                this.rascal.getNavigation().startMovingTo(this.owner, ESCORT_SPEED);
            } else {
                this.rascal.getNavigation().stop();
            }

            if (distSq > 24.0D * 24.0D) {
                BlockPos floor = QuestManager.findNearestFloor(this.rascal.getWorld(), this.owner.getBlockPos());
                if (floor != null) {
                    this.rascal.refreshPositionAndAngles(
                            floor.getX() + 0.5D,
                            floor.getY() + 1.0D,
                            floor.getZ() + 0.5D,
                            this.rascal.getYaw(),
                            this.rascal.getPitch()
                    );
                }
                this.rascal.getNavigation().stop();
            }
        }

        @Override
        public void stop() {
            this.owner = null;
            this.rascal.getNavigation().stop();
        }
    }

    static final class FollowOwnerDuringChaseGoal extends Goal {
        private final ScaredRascalEntity rascal;
        private int repathCooldown = 0;

        FollowOwnerDuringChaseGoal(ScaredRascalEntity rascal) {
            this.rascal = rascal;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (!this.rascal.isChaseActive()) return false;
            Entity rake = this.rascal.getRakeEntity();
            return rake != null && !rake.isRemoved();
        }

        @Override
        public boolean shouldContinue() {
            if (!this.rascal.isChaseActive()) return false;
            Entity rake = this.rascal.getRakeEntity();
            return rake != null && !rake.isRemoved();
        }

        @Override
        public void tick() {
            Entity rake = this.rascal.getRakeEntity();
            if (rake == null) {
                this.rascal.getNavigation().stop();
                return;
            }

            ServerPlayerEntity owner = this.rascal.getQuestOwnerPlayer();
            if (this.repathCooldown > 0) {
                this.repathCooldown--;
            }

            if (owner != null && owner.isAlive() && !owner.isSpectator()) {
                double ownerDistSq = this.rascal.squaredDistanceTo(owner);
                if (ownerDistSq > 22.0D * 22.0D) {
                    BlockPos floor = QuestManager.findNearestFloor(this.rascal.getWorld(), owner.getBlockPos());
                    if (floor != null) {
                        this.rascal.refreshPositionAndAngles(
                                floor.getX() + 0.5D,
                                floor.getY() + 1.0D,
                                floor.getZ() + 0.5D,
                                this.rascal.getYaw(),
                                this.rascal.getPitch()
                        );
                    }
                    this.rascal.getNavigation().stop();
                    this.repathCooldown = 0;
                    return;
                }

                this.rascal.getLookControl().lookAt(owner, 25.0F, this.rascal.getMaxLookPitchChange());

                boolean shouldRepath = this.repathCooldown <= 0
                        || this.rascal.getNavigation().isIdle()
                        || ownerDistSq > 4.5D * 4.5D
                        || this.rascal.squaredDistanceTo(rake) < 5.5D * 5.5D;

                if (!shouldRepath) {
                    return;
                }

                Vec3d shieldDir = owner.getPos().subtract(rake.getPos());
                if (shieldDir.lengthSquared() < 1.0E-6D) {
                    shieldDir = owner.getRotationVec(1.0F).negate();
                }
                shieldDir = shieldDir.normalize();

                double desiredDistance = 2.75D;
                Vec3d desired = owner.getPos().add(shieldDir.multiply(desiredDistance));
                BlockPos floor = QuestManager.findNearestFloor(this.rascal.getWorld(), BlockPos.ofFloored(desired.x, owner.getY() + 2.0D, desired.z));
                if (floor == null) {
                    floor = QuestManager.findSpawnFloorAround(this.rascal.getWorld(), owner.getBlockPos(), 2, 10);
                }
                if (floor == null) {
                    floor = owner.getBlockPos();
                }

                this.rascal.getNavigation().startMovingTo(
                        floor.getX() + 0.5D,
                        floor.getY() + 1.0D,
                        floor.getZ() + 0.5D,
                        CHASE_FOLLOW_SPEED
                );
                this.repathCooldown = 2;
                return;
            }

            this.rascal.getLookControl().lookAt(rake, 30.0F, this.rascal.getMaxLookPitchChange());

            boolean shouldRepath = this.repathCooldown <= 0
                    || this.rascal.getNavigation().isIdle()
                    || this.rascal.squaredDistanceTo(rake) < 5.5D * 5.5D;
            if (!shouldRepath) {
                return;
            }

            Vec3d away = this.rascal.getPos().subtract(rake.getPos());
            if (away.lengthSquared() < 1.0E-6D) {
                away = new Vec3d(
                        this.rascal.random.nextDouble() - 0.5D,
                        0.0D,
                        this.rascal.random.nextDouble() - 0.5D
                );
            }
            away = away.normalize();

            Vec3d desired = this.rascal.getPos().add(away.multiply(10.0D));
            BlockPos floor = QuestManager.findNearestFloor(this.rascal.getWorld(), BlockPos.ofFloored(desired.x, this.rascal.getY() + 2.0D, desired.z));
            if (floor == null) {
                floor = QuestManager.findSpawnFloorAround(this.rascal.getWorld(), this.rascal.getBlockPos(), 3, 12);
            }
            if (floor == null) {
                floor = this.rascal.getBlockPos();
            }

            this.rascal.getNavigation().startMovingTo(
                    floor.getX() + 0.5D,
                    floor.getY() + 1.0D,
                    floor.getZ() + 0.5D,
                    CHASE_FOLLOW_SPEED
            );
            this.repathCooldown = 2;
        }

        @Override
        public void stop() {
            this.repathCooldown = 0;
            this.rascal.getNavigation().stop();
        }
    }
}
