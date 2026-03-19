package net.seep.odd.entity.fatwitch;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import net.seep.odd.entity.ModEntities;

import java.util.EnumSet;

public final class FatWitchEntity extends HostileEntity implements GeoEntity {

    private static final RawAnimation IDLE   = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final TrackedData<Boolean> CASTING_ATTACK =
            DataTracker.registerData(FatWitchEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    // 1.04s windup
    private static final int ATTACK_WINDUP_TICKS = Math.round(1.04f * 20f); // 21
    // 1.96s full animation
    private static final int ATTACK_TOTAL_TICKS = Math.round(1.96f * 20f);  // 39
    // every 2 seconds
    private static final int ATTACK_COOLDOWN_TICKS = 40;
    private static final double CAST_RANGE = 14.0D;

    private int attackTicksLeft = 0;
    private int nextAttackAge = 0;

    public FatWitchEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 8;
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(CASTING_ATTACK, false);
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        return new MobNavigation(this, world);
    }

    public boolean isCastingAttack() {
        return this.dataTracker.get(CASTING_ATTACK);
    }

    private void setCastingAttack(boolean casting) {
        this.dataTracker.set(CASTING_ATTACK, casting);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 34.0D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.23D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.35D);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new FatWitchCastGoal(this));
        this.goalSelector.add(2, new HoverApproachTargetGoal(this, 1.0D));
        this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.8D));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(7, new LookAroundGoal(this));

        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.getWorld().isClient) {
            if (this.attackTicksLeft > 0) {
                this.setCastingAttack(true);
                this.attackTicksLeft--;

                LivingEntity target = this.getTarget();
                if (target != null) {
                    this.getLookControl().lookAt(target, 30.0f, 30.0f);
                }

                if (this.attackTicksLeft <= 0) {
                    this.setCastingAttack(false);
                }
            } else {
                this.setCastingAttack(false);
            }
        }
    }

    private void beginAttackCast() {
        LivingEntity target = this.getTarget();
        if (target == null) return;

        this.attackTicksLeft = ATTACK_TOTAL_TICKS;
        this.nextAttackAge = this.age + ATTACK_COOLDOWN_TICKS;
        this.setCastingAttack(true);
        this.getNavigation().stop();

        spawnSingleSigil(target);

        this.playSound(SoundEvents.ENTITY_WITCH_AMBIENT, 0.9f, 0.75f + this.getRandom().nextFloat() * 0.15f);
    }

    private void spawnSingleSigil(LivingEntity target) {
        FatWitchSigilEntity sigil = ModEntities.FAT_WITCH_SIGIL.create(this.getWorld());
        if (sigil == null) return;

        double x = Math.floor(target.getX()) + 0.5;
        double z = Math.floor(target.getZ()) + 0.5;
        double y = findGroundY(x, target.getY(), z);

        sigil.setOwner(this.getUuid());
        sigil.refreshPositionAndAngles(
                x,
                y,
                z,
                this.getRandom().nextFloat() * 360.0f,
                0.0f
        );

        this.getWorld().spawnEntity(sigil);
    }

    private double findGroundY(double x, double approxY, double z) {
        int bx = MathHelper.floor(x);
        int bz = MathHelper.floor(z);
        int startY = MathHelper.floor(approxY + 1.0);

        for (int y = startY; y >= startY - 6; y--) {
            BlockPos pos = new BlockPos(bx, y, bz);
            BlockState state = this.getWorld().getBlockState(pos);

            if (!state.getCollisionShape(this.getWorld(), pos).isEmpty()) {
                double top = state.getCollisionShape(this.getWorld(), pos).getMax(Direction.Axis.Y);
                return y + top + 0.02;
            }
        }

        return approxY + 0.02;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("AttackTicksLeft", this.attackTicksLeft);
        nbt.putInt("AttackCooldown", Math.max(0, this.nextAttackAge - this.age));
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.attackTicksLeft = nbt.getInt("AttackTicksLeft");
        this.nextAttackAge = this.age + nbt.getInt("AttackCooldown");
        this.setCastingAttack(this.attackTicksLeft > 0);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            if (this.isCastingAttack()) {
                state.setAndContinue(ATTACK);
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

    static final class HoverApproachTargetGoal extends Goal {
        private final FatWitchEntity mob;
        private final double speed;

        HoverApproachTargetGoal(FatWitchEntity mob, double speed) {
            this.mob = mob;
            this.speed = speed;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            LivingEntity target = this.mob.getTarget();
            return target != null && target.isAlive() && !this.mob.isCastingAttack();
        }

        @Override
        public boolean shouldContinue() {
            LivingEntity target = this.mob.getTarget();
            return target != null && target.isAlive() && !this.mob.isCastingAttack();
        }

        @Override
        public void tick() {
            LivingEntity target = this.mob.getTarget();
            if (target == null) return;

            this.mob.getLookControl().lookAt(target, 30.0f, 30.0f);

            double distSq = this.mob.squaredDistanceTo(target);

            if (distSq > 10.0D * 10.0D) {
                this.mob.getNavigation().startMovingTo(target, this.speed);
                return;
            }

            if (distSq < 5.0D * 5.0D) {
                double dx = this.mob.getX() - target.getX();
                double dz = this.mob.getZ() - target.getZ();
                double len = Math.sqrt(dx * dx + dz * dz);

                if (len < 1.0E-4) {
                    dx = 1.0;
                    dz = 0.0;
                    len = 1.0;
                }

                dx /= len;
                dz /= len;

                double retreatX = this.mob.getX() + dx * 3.0;
                double retreatZ = this.mob.getZ() + dz * 3.0;

                this.mob.getNavigation().startMovingTo(retreatX, this.mob.getY(), retreatZ, this.speed);
                return;
            }

            this.mob.getNavigation().stop();
        }
    }

    static final class FatWitchCastGoal extends Goal {
        private final FatWitchEntity mob;

        FatWitchCastGoal(FatWitchEntity mob) {
            this.mob = mob;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            LivingEntity target = this.mob.getTarget();
            if (target == null || !target.isAlive()) return false;
            if (this.mob.isCastingAttack()) return false;
            if (this.mob.age < this.mob.nextAttackAge) return false;
            if (!this.mob.canSee(target)) return false;
            return this.mob.squaredDistanceTo(target) <= CAST_RANGE * CAST_RANGE;
        }

        @Override
        public boolean shouldContinue() {
            return false;
        }

        @Override
        public void start() {
            this.mob.beginAttackCast();
        }
    }
}