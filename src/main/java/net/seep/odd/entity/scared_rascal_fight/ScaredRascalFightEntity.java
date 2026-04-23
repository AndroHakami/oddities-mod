package net.seep.odd.entity.scared_rascal_fight;

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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.entity.robo_rascal.RoboRascalEntity;
import net.seep.odd.quest.types.ScaredRascalTwoQuestLogic;
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

public final class ScaredRascalFightEntity extends PathAwareEntity implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");

    private static final TrackedData<Optional<UUID>> QUEST_OWNER =
            DataTracker.registerData(ScaredRascalFightEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Optional<UUID>> ROBO_UUID =
            DataTracker.registerData(ScaredRascalFightEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Boolean> FOLLOW_ACTIVE =
            DataTracker.registerData(ScaredRascalFightEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> ESCORT_ACTIVE =
            DataTracker.registerData(ScaredRascalFightEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> HELD_BY_ROBO =
            DataTracker.registerData(ScaredRascalFightEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final double BASE_MOVE_SPEED = 0.30D;
    private static final double FOLLOW_SPEED = 1.15D;
    private static final double ESCORT_SPEED = 1.08D;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int speechTicks = 0;

    public ScaredRascalFightEntity(EntityType<? extends ScaredRascalFightEntity> type, World world) {
        super(type, world);
        this.setStepHeight(1.0F);
        this.experiencePoints = 0;
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 52.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, BASE_MOVE_SPEED)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(QUEST_OWNER, Optional.empty());
        this.dataTracker.startTracking(ROBO_UUID, Optional.empty());
        this.dataTracker.startTracking(FOLLOW_ACTIVE, false);
        this.dataTracker.startTracking(ESCORT_ACTIVE, false);
        this.dataTracker.startTracking(HELD_BY_ROBO, false);
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
    public UUID getRoboRascalUuid() {
        return this.dataTracker.get(ROBO_UUID).orElse(null);
    }

    public void setRoboRascalUuid(@Nullable UUID uuid) {
        this.dataTracker.set(ROBO_UUID, Optional.ofNullable(uuid));
    }

    public boolean isFollowActive() {
        return this.dataTracker.get(FOLLOW_ACTIVE);
    }

    public void setFollowActive(boolean value) {
        this.dataTracker.set(FOLLOW_ACTIVE, value);
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

    public boolean isHeldByRobo() {
        return this.dataTracker.get(HELD_BY_ROBO);
    }

    public void setHeldByRobo(boolean held) {
        this.dataTracker.set(HELD_BY_ROBO, held);
        this.setNoGravity(held);
        if (!held) {
            this.setVelocity(Vec3d.ZERO);
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
    public RoboRascalEntity getRoboRascal() {
        UUID uuid = this.getRoboRascalUuid();
        if (uuid == null || !(this.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld)) {
            return null;
        }
        Entity entity = serverWorld.getEntity(uuid);
        return entity instanceof RoboRascalEntity robo ? robo : null;
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

        if (!this.isHeldByRobo() && this.hasNoGravity()) {
            this.setNoGravity(false);
        }
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new StayNearOwnerDuringFightGoal(this));
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
        return ScaredRascalTwoQuestLogic.onRascalInteracted(serverPlayer, this) ? ActionResult.SUCCESS : ActionResult.PASS;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.getAttacker() instanceof PlayerEntity) {
            return false;
        }
        if (source.getAttacker() instanceof RoboRascalEntity) {
            return super.damage(source, amount);
        }
        return false;
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
        if (this.getRoboRascalUuid() != null) nbt.putUuid("RoboRascal", this.getRoboRascalUuid());
        nbt.putBoolean("FollowActive", this.isFollowActive());
        nbt.putBoolean("EscortActive", this.isEscortActive());
        nbt.putBoolean("HeldByRobo", this.isHeldByRobo());
        nbt.putInt("SpeechTicks", this.speechTicks);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.dataTracker.set(QUEST_OWNER, nbt.containsUuid("QuestOwner") ? Optional.of(nbt.getUuid("QuestOwner")) : Optional.empty());
        this.dataTracker.set(ROBO_UUID, nbt.containsUuid("RoboRascal") ? Optional.of(nbt.getUuid("RoboRascal")) : Optional.empty());
        this.dataTracker.set(FOLLOW_ACTIVE, nbt.getBoolean("FollowActive"));
        this.dataTracker.set(ESCORT_ACTIVE, nbt.getBoolean("EscortActive"));
        this.dataTracker.set(HELD_BY_ROBO, nbt.getBoolean("HeldByRobo"));
        this.speechTicks = nbt.getInt("SpeechTicks");
        clearSpeech();
        if (this.getQuestOwnerUuid() != null) {
            this.setPersistent();
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "scared_rascal_fight.controller", 0, state -> {
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

    static final class StayNearOwnerDuringFightGoal extends Goal {
        private final ScaredRascalFightEntity rascal;

        StayNearOwnerDuringFightGoal(ScaredRascalFightEntity rascal) {
            this.rascal = rascal;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return this.rascal.isFollowActive() && !this.rascal.isHeldByRobo() && this.rascal.getQuestOwnerPlayer() != null;
        }

        @Override
        public boolean shouldContinue() {
            return this.canStart();
        }

        @Override
        public void tick() {
            ServerPlayerEntity owner = this.rascal.getQuestOwnerPlayer();
            if (owner == null) {
                return;
            }

            RoboRascalEntity robo = this.rascal.getRoboRascal();
            Vec3d targetPos = owner.getPos();

            if (robo != null && !robo.isRemoved()) {
                Vec3d away = owner.getPos().subtract(robo.getPos());
                if (away.lengthSquared() > 0.01D) {
                    away = away.normalize().multiply(2.0D);
                    targetPos = owner.getPos().add(away);
                }
            }

            this.rascal.getLookControl().lookAt(owner, 30.0F, 30.0F);
            double distSq = this.rascal.squaredDistanceTo(targetPos.x, targetPos.y, targetPos.z);
            if (distSq > 3.25D * 3.25D || this.rascal.age % 8 == 0) {
                this.rascal.getNavigation().startMovingTo(targetPos.x, targetPos.y, targetPos.z, FOLLOW_SPEED);
            }
        }

        @Override
        public void stop() {
            this.rascal.getNavigation().stop();
        }
    }

    static final class EscortOwnerGoal extends Goal {
        private final ScaredRascalFightEntity rascal;

        EscortOwnerGoal(ScaredRascalFightEntity rascal) {
            this.rascal = rascal;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return this.rascal.isEscortActive() && !this.rascal.isHeldByRobo() && this.rascal.getQuestOwnerPlayer() != null;
        }

        @Override
        public boolean shouldContinue() {
            return this.canStart();
        }

        @Override
        public void tick() {
            ServerPlayerEntity owner = this.rascal.getQuestOwnerPlayer();
            if (owner == null) {
                return;
            }
            this.rascal.getLookControl().lookAt(owner, 30.0F, 30.0F);
            if (this.rascal.squaredDistanceTo(owner) > 2.6D * 2.6D || this.rascal.age % 10 == 0) {
                this.rascal.getNavigation().startMovingTo(owner, ESCORT_SPEED);
            }
        }

        @Override
        public void stop() {
            this.rascal.getNavigation().stop();
        }
    }
}
