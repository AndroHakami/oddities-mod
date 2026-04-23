package net.seep.odd.entity.rascal;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.entity.librarian.LibrarianEntity;
import net.seep.odd.quest.QuestManager;
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

public final class RascalEntity extends PathAwareEntity implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");

    private static final TrackedData<Boolean> CALMED =
            DataTracker.registerData(RascalEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> RETURNED =
            DataTracker.registerData(RascalEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Optional<UUID>> QUEST_OWNER =
            DataTracker.registerData(RascalEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);

    private static final double BASE_MOVE_SPEED = 0.308D;
    private static final double FOLLOW_SPEED = 1.25D;
    private static final double FLEE_SLOW_SPEED = 1.02D;
    private static final double FLEE_FAST_SPEED = 1.40D;
    private static final double SCATTER_SPEED = 1.23D;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Nullable
    private UUID calmerUuid;

    public RascalEntity(EntityType<? extends RascalEntity> type, World world) {
        super(type, world);
        this.setStepHeight(1.0F);
        this.experiencePoints = 0;
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 6.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, BASE_MOVE_SPEED)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(CALMED, false);
        this.dataTracker.startTracking(RETURNED, false);
        this.dataTracker.startTracking(QUEST_OWNER, Optional.empty());
    }

    public boolean isCalmed() { return this.dataTracker.get(CALMED); }
    public void setCalmed(boolean value) { this.dataTracker.set(CALMED, value); }
    public boolean isQuestReturned() { return this.dataTracker.get(RETURNED); }
    public void setQuestReturned(boolean value) { this.dataTracker.set(RETURNED, value); }
    @Nullable public UUID getQuestOwnerUuid() { return this.dataTracker.get(QUEST_OWNER).orElse(null); }
    public void setQuestOwnerUuid(@Nullable UUID uuid) { this.dataTracker.set(QUEST_OWNER, Optional.ofNullable(uuid)); }
    public boolean hasQuestOwner() { return this.getQuestOwnerUuid() != null; }
    @Nullable public PlayerEntity getCalmerPlayer() { return this.calmerUuid == null ? null : this.getWorld().getPlayerByUuid(this.calmerUuid); }

    public void assignQuestOwner(ServerPlayerEntity player) {
        this.setQuestOwnerUuid(player.getUuid());
        this.setPersistent();
    }

    public void calmTo(ServerPlayerEntity player) {
        this.calmerUuid = player.getUuid();
        this.setQuestOwnerUuid(player.getUuid());
        this.setCalmed(true);
        this.setQuestReturned(false);
        this.setPersistent();
        this.getNavigation().stop();
        this.playSound(SoundEvents.ENTITY_ALLAY_ITEM_GIVEN, 0.9F, 1.1F);
    }

    public void clearQuestState() {
        this.calmerUuid = null;
        this.setCalmed(false);
        this.setQuestReturned(false);
        this.setQuestOwnerUuid(null);
        this.getNavigation().stop();
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient) {
            if (this.hasQuestOwner()) this.setPersistent();
            if (this.isCalmed()) {
                PlayerEntity owner = this.getCalmerPlayer();
                if (!(owner instanceof ServerPlayerEntity serverPlayer) || !owner.isAlive()) {
                    this.clearQuestState();
                    return;
                }
                if (!this.isQuestReturned() && isNearLibrarian(serverPlayer)) {
                    this.setQuestReturned(true);
                    QuestManager.onRascalReturned(serverPlayer, this);
                }
            }
        }
    }

    private boolean isNearLibrarian(ServerPlayerEntity owner) {
        return !this.getWorld().getEntitiesByClass(
                LibrarianEntity.class,
                this.getBoundingBox().expand(2.75D),
                librarian -> owner.squaredDistanceTo(librarian) <= 64.0D
        ).isEmpty();
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new FollowCalmerGoal(this, FOLLOW_SPEED, 3.0F, 1.35F));
        this.goalSelector.add(2, new EscapePlayersGoal(this, 11.0F, FLEE_SLOW_SPEED, FLEE_FAST_SPEED));
        this.goalSelector.add(3, new SkittishScatterGoal(this, SCATTER_SPEED));
        this.goalSelector.add(4, new LookAtEntityGoal(this, PlayerEntity.class, 12.0F));
        this.goalSelector.add(5, new LookAroundGoal(this));
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (this.getWorld().isClient) return true;
        if (source.getAttacker() instanceof ServerPlayerEntity player) {
            this.getWorld().sendEntityStatus(this, (byte) 2);
            this.playSound(SoundEvents.ENTITY_ALLAY_HURT, 0.8F, 1.15F);
            UUID owner = this.getQuestOwnerUuid();
            if (owner != null && owner.equals(player.getUuid()) && !this.isCalmed() && QuestManager.canCalmRascal(player, this)) {
                this.calmTo(player);
            } else {
                this.takeKnockback(0.28D, this.getX() - player.getX(), this.getZ() - player.getZ());
            }
            return true;
        }
        return false;
    }

    @Override public boolean isPushable() { return true; }
    @Override protected SoundEvent getAmbientSound() { return this.isCalmed() ? SoundEvents.ENTITY_ALLAY_AMBIENT_WITH_ITEM : SoundEvents.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.ENTITY_ALLAY_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.ENTITY_ALLAY_DEATH; }
    @Override public int getMinAmbientSoundDelay() { return 80; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Calmed", this.isCalmed());
        nbt.putBoolean("QuestReturned", this.isQuestReturned());
        if (this.calmerUuid != null) nbt.putUuid("Calmer", this.calmerUuid);
        if (this.getQuestOwnerUuid() != null) nbt.putUuid("QuestOwner", this.getQuestOwnerUuid());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.setCalmed(nbt.getBoolean("Calmed"));
        this.setQuestReturned(nbt.getBoolean("QuestReturned"));
        this.calmerUuid = nbt.containsUuid("Calmer") ? nbt.getUuid("Calmer") : null;
        UUID owner = nbt.containsUuid("QuestOwner") ? nbt.getUuid("QuestOwner") : null;
        this.setQuestOwnerUuid(owner);
        if (this.calmerUuid == null && this.isCalmed() && owner != null) this.calmerUuid = owner;
        if (owner != null) this.setPersistent();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "rascal.controller", 0, state -> {
            if (state.isMoving()) {
                state.setAndContinue(WALK);
                return PlayState.CONTINUE;
            }
            state.setAndContinue(IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return this.cache; }

    static final class EscapePlayersGoal extends FleeEntityGoal<PlayerEntity> {
        private final RascalEntity rascal;
        EscapePlayersGoal(RascalEntity rascal, float distance, double slowSpeed, double fastSpeed) {
            super(rascal, PlayerEntity.class, distance, slowSpeed, fastSpeed);
            this.rascal = rascal;
        }
        @Override public boolean canStart() { return !this.rascal.isCalmed() && super.canStart(); }
        @Override public boolean shouldContinue() { return !this.rascal.isCalmed() && super.shouldContinue(); }
    }

    static final class SkittishScatterGoal extends Goal {
        private final RascalEntity rascal;
        private final double speed;
        private int cooldown;
        private int runTicks;

        SkittishScatterGoal(RascalEntity rascal, double speed) {
            this.rascal = rascal;
            this.speed = speed;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (this.rascal.isCalmed()) return false;
            if (this.cooldown > 0) {
                this.cooldown--;
                return false;
            }
            return true;
        }

        @Override public boolean shouldContinue() { return !this.rascal.isCalmed() && this.runTicks > 0; }

        @Override
        public void start() {
            this.runTicks = 18 + this.rascal.random.nextInt(20);
            this.cooldown = 2 + this.rascal.random.nextInt(4);
            moveSomewhere();
        }

        @Override
        public void tick() {
            this.runTicks--;
            if (this.runTicks <= 0 || this.rascal.getNavigation().isIdle() || this.rascal.age % 8 == 0) {
                moveSomewhere();
            }
        }

        @Override public void stop() { this.rascal.getNavigation().stop(); }

        private void moveSomewhere() {
            PlayerEntity nearest = this.rascal.getWorld().getClosestPlayer(this.rascal, 12.0D);
            Vec3d away = nearest != null
                    ? this.rascal.getPos().subtract(nearest.getPos()).normalize()
                    : new Vec3d(this.rascal.random.nextDouble() - 0.5D, 0.0D, this.rascal.random.nextDouble() - 0.5D).normalize();
            double distance = 7.0D + this.rascal.random.nextDouble() * 5.0D;
            double tx = this.rascal.getX() + away.x * distance + (this.rascal.random.nextDouble() - 0.5D) * 4.0D;
            double tz = this.rascal.getZ() + away.z * distance + (this.rascal.random.nextDouble() - 0.5D) * 4.0D;
            BlockPos floor = QuestManager.findNearestFloor(this.rascal.getWorld(), BlockPos.ofFloored(tx, this.rascal.getY() + 3.0D, tz));
            if (floor == null) floor = this.rascal.getBlockPos();
            this.rascal.getNavigation().startMovingTo(floor.getX() + 0.5D, floor.getY() + 1.0D, floor.getZ() + 0.5D, this.speed);
        }
    }

    static final class FollowCalmerGoal extends Goal {
        private final RascalEntity rascal;
        private final double speed;
        private final float startDistance;
        private final float stopDistance;
        @Nullable private PlayerEntity owner;

        FollowCalmerGoal(RascalEntity rascal, double speed, float startDistance, float stopDistance) {
            this.rascal = rascal;
            this.speed = speed;
            this.startDistance = startDistance;
            this.stopDistance = stopDistance;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (!this.rascal.isCalmed()) return false;
            PlayerEntity player = this.rascal.getCalmerPlayer();
            if (player == null || player.isSpectator()) return false;
            if (this.rascal.squaredDistanceTo(player) < (double) (this.startDistance * this.startDistance)) return false;
            this.owner = player;
            return true;
        }

        @Override
        public boolean shouldContinue() {
            return this.owner != null && this.rascal.isCalmed()
                    && this.rascal.squaredDistanceTo(this.owner) > (double) (this.stopDistance * this.stopDistance);
        }

        @Override
        public void tick() {
            if (this.owner == null) return;
            this.rascal.getLookControl().lookAt(this.owner, 20.0F, this.rascal.getMaxLookPitchChange());
            this.rascal.getNavigation().startMovingTo(this.owner, this.speed);
            if (this.rascal.squaredDistanceTo(this.owner) > 24.0D * 24.0D) {
                BlockPos floor = QuestManager.findNearestFloor(this.rascal.getWorld(), this.owner.getBlockPos());
                if (floor != null) {
                    this.rascal.refreshPositionAndAngles(floor.getX() + 0.5D, floor.getY() + 1.0D, floor.getZ() + 0.5D,
                            this.rascal.getYaw(), this.rascal.getPitch());
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
}
