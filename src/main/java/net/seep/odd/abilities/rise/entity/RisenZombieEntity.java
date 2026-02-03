package net.seep.odd.abilities.rise.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.control.MoveControl;
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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EntityView;
import net.minecraft.world.World;

import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

public class RisenZombieEntity extends TameableEntity {

    private static final TrackedData<String> SOURCE_TYPE_ID =
            DataTracker.registerData(RisenZombieEntity.class, TrackedDataHandlerRegistry.STRING);

    private static final TrackedData<NbtCompound> SOURCE_RENDER_NBT =
            DataTracker.registerData(RisenZombieEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);

    private static final TrackedData<Boolean> CAN_FLY =
            DataTracker.registerData(RisenZombieEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    // mapping-proof owner tracking (used for the "10 max" count + teammate logic)
    private static final TrackedData<Optional<UUID>> RISE_OWNER_UUID =
            DataTracker.registerData(RisenZombieEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);

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

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SitGoal(this));
        this.goalSelector.add(2, new FollowOwnerGoal(this, 1.15D, 10.0F, 2.0F, false));
        this.goalSelector.add(3, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.add(4, new WanderAroundFarGoal(this, 1.0D));
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

    // --- Hide name everywhere (prevents “chat feed” systems from printing them)
    @Override
    protected Text getDefaultName() {
        return Text.empty();
    }

    // --- Healing + sit toggle (owner)
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

                if (!player.getAbilities().creativeMode) stack.decrement(1);
                return ActionResult.SUCCESS;
            }

            // owner can sit-toggle
            if (this.isOwner(player)) {
                this.setSitting(!this.isSitting());
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

    // --- Source appearance
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

    // --- Rise owner uuid (mapping-proof)
    public void setRiseOwnerUuid(@Nullable UUID id) {
        this.dataTracker.set(RISE_OWNER_UUID, Optional.ofNullable(id));
    }

    public @Nullable UUID getRiseOwnerUuid() {
        return this.dataTracker.get(RISE_OWNER_UUID).orElse(null);
    }

    // --- Flying
    public void setCanFly(boolean fly) {
        this.dataTracker.set(CAN_FLY, fly);
        this.setNoGravity(fly);
        this.fallDistance = 0.0F;

        if (!this.getWorld().isClient) {
            if (fly) {
                this.moveControl = new FlightMoveControl(this, 20, true);
                this.navigation = new BirdNavigation(this, this.getWorld());
            } else {
                this.moveControl = new MoveControl(this);
                this.navigation = new MobNavigation(this, this.getWorld());
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

        if (this.isSitting()) {
            // hover-stop
            this.setVelocity(this.getVelocity().multiply(0.70));
            return;
        }

        LivingEntity follow = null;

        LivingEntity tgt = this.getTarget();
        if (tgt != null && tgt.isAlive()) follow = tgt;
        else {
            LivingEntity owner = this.getOwner();
            if (owner != null && owner.isAlive()) follow = owner;
        }

        if (follow == null) {
            this.setVelocity(this.getVelocity().multiply(0.85));
            return;
        }

        double ty = follow.getY() + follow.getStandingEyeHeight() * 0.65;
        double tx = follow.getX();
        double tz = follow.getZ();

        // MoveControl handles flight “normally”
        double distSq = this.squaredDistanceTo(tx, ty, tz);
        double speed = 1.15;
        if (distSq > 14.0 * 14.0) speed = 1.35;
        if (distSq < 2.5 * 2.5) speed = 0.95;

        try {
            this.getMoveControl().moveTo(tx, ty, tz, speed);
        } catch (Throwable ignored) {
            // ultra fallback: nudge velocity
            Vec3d to = new Vec3d(tx, ty, tz).subtract(this.getPos());
            if (to.lengthSquared() > 1.0e-6) {
                Vec3d dir = to.normalize().multiply(0.30);
                this.setVelocity(this.getVelocity().multiply(0.80).add(dir.multiply(0.20)));
            }
        }

        // Face direction to prevent “stuck” look
        Vec3d v = this.getVelocity();
        if (v.lengthSquared() > 1.0e-4) {
            float yaw = (float)(MathHelper.atan2(v.z, v.x) * 57.295776F) - 90.0F;
            this.setYaw(yaw);
            this.setHeadYaw(yaw);
        }
    }

    // --- prevent targeting other risen, EVER
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

    // --- NBT
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("RiseSourceType", this.getSourceTypeId());
        nbt.put("RiseSourceNbt", this.getSourceRenderNbt().copy());
        nbt.putBoolean("RiseCanFly", this.canFly());

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
    }

    @Override
    public EntityView method_48926() {
        // must not be null (TameableEntity uses this to resolve owner internally)
        return this.getWorld();
    }
}
