package net.seep.odd.abilities.shift.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
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

import java.util.Optional;
import java.util.UUID;

public final class DecoyEntity extends PathAwareEntity implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation RUN  = RawAnimation.begin().thenLoop("run");

    private static final TrackedData<Boolean> RUNNING =
            DataTracker.registerData(DecoyEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Optional<UUID>> OWNER_UUID =
            DataTracker.registerData(DecoyEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private Vec3d runDir = Vec3d.ZERO;
    private int runTicksLeft = 0;
    private int maxLifeTicks = 20 * 20;
    private int livedTicks = 0;

    public DecoyEntity(EntityType<? extends DecoyEntity> type, World world) {
        super(type, world);
        this.setStepHeight(1.1F);
        this.experiencePoints = 0;
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 2.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.35D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        MobNavigation nav = new MobNavigation(this, world);
        nav.setCanSwim(true);
        nav.setCanPathThroughDoors(true);
        nav.setCanEnterOpenDoors(true);
        return nav;
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(RUNNING, false);
        this.dataTracker.startTracking(OWNER_UUID, Optional.empty());
    }

    public void setOwner(ServerPlayerEntity player) {
        this.dataTracker.set(OWNER_UUID, Optional.of(player.getUuid()));
    }

    @Nullable
    public ServerPlayerEntity getOwnerPlayer() {
        UUID ownerUuid = this.dataTracker.get(OWNER_UUID).orElse(null);
        if (ownerUuid == null || this.getWorld().isClient) return null;
        return this.getWorld().getServer() == null ? null : this.getWorld().getServer().getPlayerManager().getPlayer(ownerUuid);
    }

    public void startRunning(Vec3d direction, int runTicks, int maxLifeTicks) {
        Vec3d flat = new Vec3d(direction.x, 0.0D, direction.z);
        if (flat.lengthSquared() < 1.0E-4D) {
            flat = Vec3d.fromPolar(0.0F, this.getYaw());
            flat = new Vec3d(flat.x, 0.0D, flat.z);
        }

        this.runDir = flat.normalize();
        this.runTicksLeft = Math.max(0, runTicks);
        this.maxLifeTicks = Math.max(1, maxLifeTicks);
        this.livedTicks = 0;
        this.dataTracker.set(RUNNING, this.runTicksLeft > 0);

        float yaw = (float) (MathHelper.atan2(this.runDir.z, this.runDir.x) * (180.0D / Math.PI)) - 90.0F;
        this.setYaw(yaw);
        this.setHeadYaw(yaw);
        this.setBodyYaw(yaw);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient) {
            return;
        }

        this.livedTicks++;
        if (this.livedTicks >= this.maxLifeTicks) {
            this.discard();
            return;
        }

        ServerPlayerEntity owner = this.getOwnerPlayer();
        if (owner == null || !owner.isAlive()) {
            this.discard();
            return;
        }

        if (this.runTicksLeft > 0) {
            this.runTicksLeft--;
            this.dataTracker.set(RUNNING, true);

            float yaw = (float) (MathHelper.atan2(this.runDir.z, this.runDir.x) * (180.0D / Math.PI)) - 90.0F;
            this.setYaw(yaw);
            this.setHeadYaw(yaw);
            this.setBodyYaw(yaw);

            double speed = 0.46D;
            if (this.horizontalCollision && this.isOnGround()) {
                this.setVelocity(this.runDir.x * speed, 0.42D, this.runDir.z * speed);
            } else {
                this.setVelocity(this.runDir.x * speed, this.getVelocity().y, this.runDir.z * speed);
            }
            this.velocityModified = true;
            this.getNavigation().stop();
        } else {
            this.dataTracker.set(RUNNING, false);
            Vec3d vel = this.getVelocity();
            this.setVelocity(vel.x * 0.6D, vel.y, vel.z * 0.6D);
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (this.getWorld().isClient) return true;

        LivingEntity attacker = resolveAttacker(source);
        if (attacker != null) {
            attacker.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.GLOWING,
                    20 * 5,
                    0,
                    true, false, true
            ));
        }

        this.discard();
        return true;
    }

    @Nullable
    private static LivingEntity resolveAttacker(DamageSource source) {
        if (source.getAttacker() instanceof LivingEntity living) {
            return living;
        }
        if (source.getSource() instanceof ProjectileEntity projectile && projectile.getOwner() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushAway(Entity entity) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "decoy.controller", 0, state -> {
            state.setAndContinue(this.dataTracker.get(RUNNING) ? RUN : IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        UUID ownerUuid = this.dataTracker.get(OWNER_UUID).orElse(null);
        if (ownerUuid != null) {
            nbt.putUuid("Owner", ownerUuid);
        }

        nbt.putDouble("RunDirX", this.runDir.x);
        nbt.putDouble("RunDirY", this.runDir.y);
        nbt.putDouble("RunDirZ", this.runDir.z);
        nbt.putInt("RunTicksLeft", this.runTicksLeft);
        nbt.putInt("MaxLifeTicks", this.maxLifeTicks);
        nbt.putInt("LivedTicks", this.livedTicks);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("Owner")) {
            this.dataTracker.set(OWNER_UUID, Optional.of(nbt.getUuid("Owner")));
        }
        this.runDir = new Vec3d(
                nbt.getDouble("RunDirX"),
                nbt.getDouble("RunDirY"),
                nbt.getDouble("RunDirZ")
        );
        this.runTicksLeft = Math.max(0, nbt.getInt("RunTicksLeft"));
        this.maxLifeTicks = Math.max(1, nbt.getInt("MaxLifeTicks"));
        this.livedTicks = Math.max(0, nbt.getInt("LivedTicks"));
        this.dataTracker.set(RUNNING, this.runTicksLeft > 0);
    }

    @Override
    public boolean collidesWith(Entity other) {
        return other instanceof PlayerEntity || other instanceof LivingEntity;
    }
}
