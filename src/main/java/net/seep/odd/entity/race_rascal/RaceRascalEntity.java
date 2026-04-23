package net.seep.odd.entity.race_rascal;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.pathing.Path;
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

public final class RaceRascalEntity extends PathAwareEntity implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");

    private static final TrackedData<Optional<UUID>> QUEST_OWNER =
            DataTracker.registerData(RaceRascalEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);

    private static final double RACE_NAV_SPEED = 1.75D;
    private static final double TARGET_REACHED_SQ = 4.0D * 4.0D;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Nullable
    private BlockPos raceTarget;
    private boolean raceReleased = false;

    public RaceRascalEntity(EntityType<? extends RaceRascalEntity> type, World world) {
        super(type, world);
        this.setStepHeight(1.0F);
        this.experiencePoints = 0;
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 6.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.30D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0D);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(QUEST_OWNER, Optional.empty());
    }

    @Nullable
    public UUID getQuestOwnerUuid() {
        return this.dataTracker.get(QUEST_OWNER).orElse(null);
    }

    public void assignQuestOwner(ServerPlayerEntity player) {
        this.dataTracker.set(QUEST_OWNER, Optional.of(player.getUuid()));
        this.setPersistent();
    }

    public void setRaceTarget(@Nullable BlockPos target) {
        this.raceTarget = target == null ? null : target.toImmutable();
    }

    @Nullable
    public BlockPos getRaceTarget() {
        return this.raceTarget;
    }

    public void setRaceReleased(boolean released) {
        this.raceReleased = released;
        if (!released) {
            this.getNavigation().stop();
        }
    }

    public boolean isRaceReleased() {
        return this.raceReleased;
    }

    public void primeRacePath() {
        attemptRacePath(false);
    }

    private boolean attemptRacePath(boolean startMoving) {
        if (this.raceTarget == null) {
            return false;
        }

        // Try several approach points around the librarian instead of only the exact center.
        // This helps a lot in mazes / tight corridors.
        int[][] offsets = new int[][]{
                {0, 0},
                {2, 0}, {-2, 0}, {0, 2}, {0, -2},
                {3, 1}, {-3, 1}, {3, -1}, {-3, -1},
                {4, 0}, {-4, 0}, {0, 4}, {0, -4}
        };

        for (int[] off : offsets) {
            BlockPos candidate = this.raceTarget.add(off[0], 0, off[1]);
            Path path = this.getNavigation().findPathTo(candidate, 0);
            if (path != null) {
                if (startMoving) {
                    this.getNavigation().startMovingAlong(path, RACE_NAV_SPEED);
                }
                return true;
            }
        }

        return false;
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new RaceToTargetGoal(this));
        this.goalSelector.add(2, new LookAroundGoal(this));
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (this.getWorld().isClient) {
            return true;
        }
        if (source.getAttacker() instanceof PlayerEntity) {
            this.playSound(SoundEvents.ENTITY_ALLAY_HURT, 0.8F, 1.15F);
            return true;
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
        return 80;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);

        if (this.getQuestOwnerUuid() != null) {
            nbt.putUuid("QuestOwner", this.getQuestOwnerUuid());
        }
        if (this.raceTarget != null) {
            nbt.putInt("RaceTargetX", this.raceTarget.getX());
            nbt.putInt("RaceTargetY", this.raceTarget.getY());
            nbt.putInt("RaceTargetZ", this.raceTarget.getZ());
        }
        nbt.putBoolean("RaceReleased", this.raceReleased);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);

        if (nbt.containsUuid("QuestOwner")) {
            this.dataTracker.set(QUEST_OWNER, Optional.of(nbt.getUuid("QuestOwner")));
            this.setPersistent();
        }

        if (nbt.contains("RaceTargetX") && nbt.contains("RaceTargetY") && nbt.contains("RaceTargetZ")) {
            this.raceTarget = new BlockPos(
                    nbt.getInt("RaceTargetX"),
                    nbt.getInt("RaceTargetY"),
                    nbt.getInt("RaceTargetZ")
            );
        } else {
            this.raceTarget = null;
        }

        this.raceReleased = nbt.getBoolean("RaceReleased");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "race_rascal.controller", 0, state -> {
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

    static final class RaceToTargetGoal extends Goal {
        private final RaceRascalEntity rascal;

        private int repathCooldown = 0;
        private int stuckTicks = 0;
        private Vec3d lastPos = Vec3d.ZERO;

        RaceToTargetGoal(RaceRascalEntity rascal) {
            this.rascal = rascal;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return this.rascal.isRaceReleased() && this.rascal.getRaceTarget() != null;
        }

        @Override
        public boolean shouldContinue() {
            if (!this.rascal.isRaceReleased() || this.rascal.getRaceTarget() == null) {
                return false;
            }

            BlockPos target = this.rascal.getRaceTarget();
            if (target == null) return false;

            return this.rascal.squaredDistanceTo(
                    target.getX() + 0.5D,
                    target.getY() + 0.5D,
                    target.getZ() + 0.5D
            ) > TARGET_REACHED_SQ;
        }

        @Override
        public void start() {
            this.repathCooldown = 0;
            this.stuckTicks = 0;
            this.lastPos = this.rascal.getPos();
            this.rascal.attemptRacePath(true);
        }

        @Override
        public void tick() {
            BlockPos target = this.rascal.getRaceTarget();
            if (target == null) return;

            this.rascal.getLookControl().lookAt(
                    target.getX() + 0.5D,
                    target.getY() + 1.0D,
                    target.getZ() + 0.5D,
                    20.0F,
                    this.rascal.getMaxLookPitchChange()
            );

            if (this.repathCooldown > 0) {
                this.repathCooldown--;
            }

            double movedSq = this.rascal.getPos().squaredDistanceTo(this.lastPos);
            if (movedSq < 0.015D) {
                this.stuckTicks++;
            } else {
                this.stuckTicks = 0;
                this.lastPos = this.rascal.getPos();
            }

            if (this.rascal.getNavigation().isIdle() || this.repathCooldown <= 0 || this.stuckTicks > 12) {
                this.rascal.attemptRacePath(true);
                this.repathCooldown = this.stuckTicks > 12 ? 4 : 10;
                if (this.stuckTicks > 12) {
                    this.stuckTicks = 0;
                }
            }
        }

        @Override
        public void stop() {
            this.rascal.getNavigation().stop();
        }
    }
}