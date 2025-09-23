package net.seep.odd.entity.outerman;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
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
import net.minecraft.world.World;
import net.seep.odd.item.ModItems;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

public final class OuterManEntity extends PathAwareEntity implements GeoEntity {

    /* ---------- GeckoLib ---------- */
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation IDLE   = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation RUN    = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("leap"); // quick hit

    // Client-synced short attack flag so the attack animation always shows
    private static final TrackedData<Integer> ATTACK_TIME =
            DataTracker.registerData(OuterManEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private int attackTicks; // server authority, mirrored to ATTACK_TIME

    public OuterManEntity(EntityType<? extends OuterManEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 3;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 12.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.36D)   // base walk speed
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.0D)     // low damage
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0D)
                .add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 0.2D);
    }

    /* ---------- Data tracker ---------- */
    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(ATTACK_TIME, 0);
    }
    private void setAttackAnim(int ticks) {
        this.attackTicks = ticks;
        this.dataTracker.set(ATTACK_TIME, ticks);
    }
    private int getAttackAnim() {
        return this.dataTracker.get(ATTACK_TIME);
    }

    /* ---------- Goals ---------- */
    @Override
    protected void initGoals() {
        // Fast, relentless melee
        this.goalSelector.add(1, new RapidMeleeGoal(this, 1.6, true)); // runs fast while attacking
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true,
                p -> p.isAlive() && !p.isSpectator()));

        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 12f));
        this.goalSelector.add(9, new LookAroundGoal(this));
    }

    /** Melee goal tuned to swing very frequently (little damage each). */
    static final class RapidMeleeGoal extends MeleeAttackGoal {
        RapidMeleeGoal(PathAwareEntity mob, double speed, boolean pauseWhenIdle) {
            super(mob, speed, pauseWhenIdle);
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }
        // Shorter attack interval than vanilla (roughly 7–8 ticks).
        @Override protected int getTickCount(int ticks) {
            return Math.max(7, ticks / 3);
        }
    }

    /* ---------- Tick ---------- */
    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient && attackTicks > 0) {
            attackTicks--;
            this.dataTracker.set(ATTACK_TIME, attackTicks);
        }
        // sprint visual while chasing
        this.setSprinting(this.getTarget() != null && this.distanceTo(this.getTarget()) > 2.0F);
    }

    /* ---------- Damage swing → play attack anim ---------- */
    @Override
    public boolean tryAttack(Entity target) {
        boolean hit = super.tryAttack(target);
        if (hit) setAttackAnim(10); // short client-synced pulse
        return hit;
    }

    /* ---------- GeckoLib ---------- */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "outerman.controller", 0, state -> {
            if (getAttackAnim() > 0) {
                state.setAndContinue(ATTACK);
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
        return cache;
    }

    /* ---------- Drops (temporary; you can move to a loot table) ---------- */
    @Override
    public void onDeath(net.minecraft.entity.damage.DamageSource source) {
        super.onDeath(source);
        if (!this.getWorld().isClient && this.getWorld() instanceof ServerWorld sw) {
            // 1–2 pearls
            int count = this.random.nextInt(2) + 1;
            for (int i = 0; i < count; i++) {
                this.dropStack(new ItemStack(ModItems.ALIEN_PEARL));
            }
        }
    }

    /* ---------- NBT ---------- */
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("AttackTime", attackTicks);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        attackTicks = nbt.getInt("AttackTime");
    }
}
