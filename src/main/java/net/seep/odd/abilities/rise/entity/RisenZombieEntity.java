// FILE: src/main/java/net/seep/odd/abilities/rise/entity/RisenZombieEntity.java
package net.seep.odd.abilities.rise.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.EntityView;
import net.minecraft.world.World;

import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

public class RisenZombieEntity extends TameableEntity {

    private static final TrackedData<String> SOURCE_TYPE_ID =
            DataTracker.registerData(RisenZombieEntity.class, TrackedDataHandlerRegistry.STRING);

    private static final TrackedData<NbtCompound> SOURCE_RENDER_NBT =
            DataTracker.registerData(RisenZombieEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);

    private static final TrackedData<Boolean> CAN_FLY =
            DataTracker.registerData(RisenZombieEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final TrackedData<Optional<UUID>> RISE_OWNER_UUID =
            DataTracker.registerData(RisenZombieEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);

    // ✅ per-mob hitbox (width/height) synced to clients
    private static final TrackedData<Float> SOURCE_W =
            DataTracker.registerData(RisenZombieEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> SOURCE_H =
            DataTracker.registerData(RisenZombieEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private static final float MIN_HITBOX = 0.2f;
    private static final float MAX_HITBOX = 6.0f;

    public RisenZombieEntity(EntityType<? extends TameableEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createRisenZombieAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0D)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0D)
                .add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 1.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.1D)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 1.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0D)
                .add(EntityAttributes.GENERIC_ARMOR, 2.0D);
    }

    /* ====================== goals ====================== */

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SitGoal(this));

        // Attack should outrank follow (both ground + fly)
        this.goalSelector.add(2, new FlyMeleeGoal(this));
        this.goalSelector.add(2, new GroundMeleeGoal(this, 1.2D, true));

        this.goalSelector.add(3, new FlyFollowOwnerGoal(this));
        this.goalSelector.add(3, new GroundFollowOwnerGoal(this, 1.15D, 10.0F, 2.0F, false));

        this.goalSelector.add(4, new GroundWanderGoal(this, 1.0D));
        this.goalSelector.add(5, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.add(6, new LookAroundGoal(this));

        this.targetSelector.add(1, new TrackOwnerAttackerGoal(this));
        this.targetSelector.add(2, new AttackWithOwnerGoal(this));
        this.targetSelector.add(3, new RevengeGoal(this));
    }

    @Override
    public @Nullable PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
        return null;
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(SOURCE_TYPE_ID, "minecraft:zombie");
        this.dataTracker.startTracking(SOURCE_RENDER_NBT, new NbtCompound());
        this.dataTracker.startTracking(CAN_FLY, false);
        this.dataTracker.startTracking(RISE_OWNER_UUID, Optional.empty());

        // default zombie-ish hitbox
        this.dataTracker.startTracking(SOURCE_W, 0.6f);
        this.dataTracker.startTracking(SOURCE_H, 1.95f);
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        return new MobNavigation(this, world);
    }

    @Override
    public EntityGroup getGroup() {
        return EntityGroup.UNDEAD;
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    @Override
    protected Text getDefaultName() {
        return Text.empty();
    }

    @Override public Arm getMainArm() { return Arm.RIGHT; }

    /* ====================== hitbox ====================== */

    public void setSourceHitbox(float w, float h) {
        w = MathHelper.clamp(w, MIN_HITBOX, MAX_HITBOX);
        h = MathHelper.clamp(h, MIN_HITBOX, MAX_HITBOX);
        this.dataTracker.set(SOURCE_W, w);
        this.dataTracker.set(SOURCE_H, h);
        this.calculateDimensions();
    }

    public float getSourceHitboxW() { return this.dataTracker.get(SOURCE_W); }
    public float getSourceHitboxH() { return this.dataTracker.get(SOURCE_H); }

    @Override
    public EntityDimensions getDimensions(EntityPose pose) {
        float w = this.dataTracker.get(SOURCE_W);
        float h = this.dataTracker.get(SOURCE_H);
        return EntityDimensions.changing(w, h);
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (SOURCE_W.equals(data) || SOURCE_H.equals(data)) {
            this.calculateDimensions(); // ✅ client updates instantly too
        }
    }

    /* ====================== interact ====================== */

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);

        if (!this.getWorld().isClient) {
            // owner can feed meat to heal
            if (this.isOwner(player) && isMeat(stack) && this.getHealth() < this.getMaxHealth()) {
                float heal = 4.0F;
                try {
                    Object food = stack.getItem().getFoodComponent();
                    if (food != null) {
                        Method m = food.getClass().getMethod("getHunger");
                        Object v = m.invoke(food);
                        if (v instanceof Integer i) heal = Math.max(2.0F, i);
                    }
                } catch (Throwable ignored) {}

                this.heal(heal);

                // ✅ cute feedback
                if (this.getWorld() instanceof ServerWorld sw) {
                    sw.spawnParticles(ParticleTypes.HEART,
                            this.getX(), this.getBodyY(0.85), this.getZ(),
                            6, 0.35, 0.25, 0.35, 0.02);
                    sw.playSound(null, this.getBlockPos(),
                            SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.NEUTRAL,
                            0.8f, 1.15f);
                }

                if (!player.getAbilities().creativeMode) stack.decrement(1);
                return ActionResult.SUCCESS;
            }

            // owner can sit-toggle
            if (this.isOwner(player)) {
                this.setSitting(!this.isSitting());
                this.getNavigation().stop();
                this.setVelocity(this.getVelocity().multiply(0.2));
                return ActionResult.SUCCESS;
            }
        }

        return super.interactMob(player, hand);
    }

    private static boolean isMeat(ItemStack stack) {
        try {
            Object food = stack.getItem().getFoodComponent();
            if (food == null) return false;
            Method m = food.getClass().getMethod("isMeat");
            Object v = m.invoke(food);
            return v instanceof Boolean b && b;
        } catch (Throwable t) {
            return false;
        }
    }

    /* ====================== appearance ====================== */

    public void setSourceTypeId(String id) {
        this.dataTracker.set(SOURCE_TYPE_ID, (id == null || id.isEmpty()) ? "minecraft:zombie" : id);
    }

    public String getSourceTypeId() {
        return this.dataTracker.get(SOURCE_TYPE_ID);
    }

    public void setSourceRenderNbt(NbtCompound nbt) {
        this.dataTracker.set(SOURCE_RENDER_NBT, nbt == null ? new NbtCompound() : nbt);
    }

    public NbtCompound getSourceRenderNbt() {
        return this.dataTracker.get(SOURCE_RENDER_NBT);
    }

    public void setRiseOwnerUuid(@Nullable UUID id) {
        this.dataTracker.set(RISE_OWNER_UUID, Optional.ofNullable(id));
    }

    public @Nullable UUID getRiseOwnerUuid() {
        return this.dataTracker.get(RISE_OWNER_UUID).orElse(null);
    }

    /* ====================== flying ====================== */

    public void setCanFly(boolean fly) {
        this.dataTracker.set(CAN_FLY, fly);

        this.setNoGravity(fly);
        this.fallDistance = 0.0F;

        if (!this.getWorld().isClient) {
            if (fly) {
                this.moveControl = new FlightMoveControl(this, 24, true);
                this.navigation = new BirdNavigation(this, this.getWorld());
                this.getNavigation().stop();
                this.setVelocity(this.getVelocity().multiply(0.15));
            } else {
                this.moveControl = new MoveControl(this);
                this.navigation = new MobNavigation(this, this.getWorld());
                this.getNavigation().stop();
            }
        }
    }

    public boolean canFly() {
        return this.dataTracker.get(CAN_FLY);
    }

    @Override
    public void tickMovement() {
        super.tickMovement();

        if (!this.canFly()) return;

        this.setNoGravity(true);
        this.fallDistance = 0.0F;

        // If sitting, “hover stop”
        if (this.isSitting()) {
            this.getNavigation().stop();
            this.setVelocity(this.getVelocity().multiply(0.6));
        }

        // IMPORTANT:
        // do NOT force yaw/headYaw to velocity here.
        // The MoveControl + LookControl from Fly goals handle rotation smoothly.
    }

    /* ====================== target rules ====================== */

    @Override
    public boolean canTarget(LivingEntity target) {
        if (target instanceof RisenZombieEntity) return false;

        UUID owner = this.getRiseOwnerUuid();
        if (target != null && owner != null && target.getUuid().equals(owner)) return false;

        return super.canTarget(target);
    }

    @Override
    public boolean isTeammate(Entity other) {
        if (other instanceof RisenZombieEntity) return true;
        return super.isTeammate(other);
    }

    /* ====================== NBT ====================== */

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("RiseSourceType", this.getSourceTypeId());
        nbt.put("RiseSourceNbt", this.getSourceRenderNbt().copy());
        nbt.putBoolean("RiseCanFly", this.canFly());

        // ✅ persist hitbox
        nbt.putFloat("RiseHitW", this.dataTracker.get(SOURCE_W));
        nbt.putFloat("RiseHitH", this.dataTracker.get(SOURCE_H));

        UUID oid = this.getRiseOwnerUuid();
        if (oid != null) nbt.putUuid("RiseOwner", oid);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("RiseSourceType")) this.setSourceTypeId(nbt.getString("RiseSourceType"));
        if (nbt.contains("RiseSourceNbt")) this.setSourceRenderNbt(nbt.getCompound("RiseSourceNbt"));
        if (nbt.contains("RiseCanFly")) this.setCanFly(nbt.getBoolean("RiseCanFly"));
        if (nbt.containsUuid("RiseOwner")) this.setRiseOwnerUuid(nbt.getUuid("RiseOwner"));

        // ✅ restore hitbox (and recalc)
        if (nbt.contains("RiseHitW")) this.dataTracker.set(SOURCE_W, MathHelper.clamp(nbt.getFloat("RiseHitW"), MIN_HITBOX, MAX_HITBOX));
        if (nbt.contains("RiseHitH")) this.dataTracker.set(SOURCE_H, MathHelper.clamp(nbt.getFloat("RiseHitH"), MIN_HITBOX, MAX_HITBOX));
        this.calculateDimensions();
    }

    @Override
    public EntityView method_48926() {
        return this.getWorld();
    }

    /* ====================== goal impls ====================== */

    /** Ground follow only when not flying. */
    private static final class GroundFollowOwnerGoal extends FollowOwnerGoal {
        private final RisenZombieEntity mob;
        GroundFollowOwnerGoal(RisenZombieEntity mob, double speed, float minDistance, float maxDistance, boolean leavesWater) {
            super(mob, speed, minDistance, maxDistance, leavesWater);
            this.mob = mob;
        }
        @Override public boolean canStart() { return !mob.canFly() && super.canStart(); }
        @Override public boolean shouldContinue() { return !mob.canFly() && super.shouldContinue(); }
    }

    /** Ground melee only when not flying. */
    private static final class GroundMeleeGoal extends MeleeAttackGoal {
        private final RisenZombieEntity mob;
        GroundMeleeGoal(RisenZombieEntity mob, double speed, boolean pauseWhenIdle) {
            super(mob, speed, pauseWhenIdle);
            this.mob = mob;
        }
        @Override public boolean canStart() { return !mob.canFly() && super.canStart(); }
        @Override public boolean shouldContinue() { return !mob.canFly() && super.shouldContinue(); }
    }

    /** Ground wandering only when not flying. */
    private static final class GroundWanderGoal extends WanderAroundFarGoal {
        private final RisenZombieEntity mob;
        GroundWanderGoal(RisenZombieEntity mob, double speed) {
            super(mob, speed);
            this.mob = mob;
        }
        @Override public boolean canStart() { return !mob.canFly() && super.canStart(); }
        @Override public boolean shouldContinue() { return !mob.canFly() && super.shouldContinue(); }
    }

    /** Smooth fly-follow (no navigation/path jitter). */
    private static final class FlyFollowOwnerGoal extends Goal {
        private final RisenZombieEntity mob;
        FlyFollowOwnerGoal(RisenZombieEntity mob) {
            this.mob = mob;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (!mob.canFly()) return false;
            if (mob.isSitting()) return false;
            LivingEntity owner = mob.getOwner();
            if (owner == null || !owner.isAlive()) return false;
            if (mob.getTarget() != null && mob.getTarget().isAlive()) return false;
            return mob.squaredDistanceTo(owner) > 6.0 * 6.0;
        }

        @Override
        public boolean shouldContinue() {
            if (!mob.canFly()) return false;
            if (mob.isSitting()) return false;
            LivingEntity owner = mob.getOwner();
            if (owner == null || !owner.isAlive()) return false;
            if (mob.getTarget() != null && mob.getTarget().isAlive()) return false;
            return mob.squaredDistanceTo(owner) > 3.5 * 3.5;
        }

        @Override
        public void tick() {
            LivingEntity owner = mob.getOwner();
            if (owner == null) return;

            double tx = owner.getX();
            double ty = owner.getY() + owner.getStandingEyeHeight() * 0.65;
            double tz = owner.getZ();

            double d2 = mob.squaredDistanceTo(tx, ty, tz);
            double speed = (d2 > 18.0 * 18.0) ? 1.35 : 1.15;

            mob.getLookControl().lookAt(owner, 30.0F, 30.0F);
            mob.getMoveControl().moveTo(tx, ty, tz, speed);
        }
    }

    /** Smooth flying melee (approach + attack) */
    private static final class FlyMeleeGoal extends Goal {
        private final RisenZombieEntity mob;
        private int attackCd = 0;

        FlyMeleeGoal(RisenZombieEntity mob) {
            this.mob = mob;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (!mob.canFly()) return false;
            if (mob.isSitting()) return false;
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean shouldContinue() {
            if (!mob.canFly()) return false;
            if (mob.isSitting()) return false;
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) return;

            if (attackCd > 0) attackCd--;

            double tx = t.getX();
            double ty = t.getY() + t.getStandingEyeHeight() * 0.55;
            double tz = t.getZ();

            mob.getLookControl().lookAt(t, 45.0F, 45.0F);
            mob.getMoveControl().moveTo(tx, ty, tz, 1.25);

            double d2 = mob.squaredDistanceTo(t);
            double reach = 2.35;
            if (d2 <= reach * reach && attackCd <= 0) {
                mob.tryAttack(t);
                attackCd = 12;
            }
        }
    }
}