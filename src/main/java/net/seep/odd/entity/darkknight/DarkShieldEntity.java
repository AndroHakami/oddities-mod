package net.seep.odd.entity.darkknight;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.abilities.darkknight.DarkKnightRuntime;
import net.seep.odd.abilities.power.DarkKnightPower;
import net.seep.odd.entity.ModEntities;
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

public class DarkShieldEntity extends PathAwareEntity implements GeoEntity {

    public static EntityType<DarkShieldEntity> buildType() {
        return FabricEntityTypeBuilder
                .<DarkShieldEntity>create(SpawnGroup.MISC, DarkShieldEntity::new)
                .dimensions(EntityDimensions.fixed(1.15F, 1.15F))
                .trackRangeChunks(8)
                .trackedUpdateRate(1)
                .build();
    }

    private static final TrackedData<Optional<UUID>> OWNER_UUID =
            DataTracker.registerData(DarkShieldEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Optional<UUID>> PROTECTED_UUID =
            DataTracker.registerData(DarkShieldEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Integer> TARGET_ID =
            DataTracker.registerData(DarkShieldEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private static final double MAX_OWNER_DISTANCE = 150.0D;
    private static final double MAX_OWNER_DISTANCE_SQ = MAX_OWNER_DISTANCE * MAX_OWNER_DISTANCE;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private float orbitAngle;
    private float snapAngle;
    private int snapTicks;

    public DarkShieldEntity(EntityType<? extends DarkShieldEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
        this.setSilent(true);
        this.setInvulnerable(true);
        this.setPathfindingPenalty(PathNodeType.WATER, 0.0F);
    }

    public DarkShieldEntity(ServerWorld world) {
        this(ModEntities.DARK_SHIELD, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, DarkKnightRuntime.MAX_SHIELD_HEALTH)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0D);
    }

    public void bootstrap(ServerPlayerEntity owner, LivingEntity protectedTarget, float startingHealth) {
        this.setOwnerUuid(owner.getUuid());
        this.setProtectedUuid(protectedTarget.getUuid());
        this.setTargetEntityId(protectedTarget.getId());
        this.setShieldHealth(startingHealth);

        Vec3d look = protectedTarget.getRotationVec(1.0F);
        this.orbitAngle = (float) Math.atan2(look.z, look.x);

        Vec3d spawnPos = computeDesiredPosition(protectedTarget, this.orbitAngle);
        this.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, protectedTarget.getYaw(), 0.0F);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(OWNER_UUID, Optional.empty());
        this.dataTracker.startTracking(PROTECTED_UUID, Optional.empty());
        this.dataTracker.startTracking(TARGET_ID, 0);
    }

    @Override
    protected void initGoals() {
        // no AI
    }

    @Override
    public void tick() {
        super.tick();
        this.noClip = true;
        this.setNoGravity(true);

        if (this.getWorld().isClient()) {
            return;
        }

        LivingEntity owner = getOwnerEntity();
        if (owner == null || !owner.isAlive()) {
            this.discard();
            return;
        }

        // auto recall if shield is too far from owner
        if (this.squaredDistanceTo(owner) > MAX_OWNER_DISTANCE_SQ) {
            recallAndDiscard(false); // preserve current hp so it can recharge while recalled
            return;
        }

        LivingEntity protectedTarget = getProtectedEntity();
        if (protectedTarget == null || !protectedTarget.isAlive()) {
            protectedTarget = owner;
            placeOn(owner);
        }

        this.setTargetEntityId(protectedTarget.getId());
        orbitAround(protectedTarget);
    }

    private void orbitAround(LivingEntity target) {
        if (this.snapTicks > 0) {
            float diff = MathHelper.wrapDegrees((this.snapAngle - this.orbitAngle) * 57.295776F) / 57.295776F;
            this.orbitAngle += diff * 0.35F;
            this.snapTicks--;
        } else {
            this.orbitAngle += DarkKnightPower.ORBIT_SPEED_RADIANS;
        }

        Vec3d oldPos = this.getPos();
        Vec3d desiredPos = computeDesiredPosition(target, this.orbitAngle);
        Vec3d delta = desiredPos.subtract(oldPos);

        this.setPosition(desiredPos.x, desiredPos.y, desiredPos.z);
        this.setVelocity(delta);

        Vec3d center = target.getPos().add(0.0D, target.getHeight() * 0.62D, 0.0D);
        Vec3d outward = desiredPos.subtract(center);
        if (outward.lengthSquared() > 1.0E-4D) {
            outward = outward.normalize();

            float yaw = (float) (Math.atan2(outward.z, outward.x) * 57.295776D) - 90.0F;
            float tilt = Math.min(28.0F, (float) delta.length() * 85.0F);

            this.setYaw(yaw);
            this.prevYaw = yaw;
            this.bodyYaw = yaw;
            this.headYaw = yaw;
            this.setPitch(-tilt);
            this.prevPitch = -tilt;
        }
    }

    private Vec3d computeDesiredPosition(LivingEntity target, float angle) {
        Vec3d center = target.getPos().add(0.0D, target.getHeight() * 0.62D, 0.0D);
        double x = center.x + (Math.cos(angle) * DarkKnightPower.ORBIT_RADIUS);
        double z = center.z + (Math.sin(angle) * DarkKnightPower.ORBIT_RADIUS);
        double y = center.y + DarkKnightPower.ORBIT_HEIGHT_OFFSET;
        return new Vec3d(x, y, z);
    }

    public void placeOn(LivingEntity newProtectedTarget) {
        if (this.getWorld().isClient()) {
            return;
        }

        UUID ownerUuid = getOwnerUuid();
        UUID oldProtectedUuid = getProtectedUuid();

        this.setProtectedUuid(newProtectedTarget.getUuid());
        this.setTargetEntityId(newProtectedTarget.getId());

        if (ownerUuid != null) {
            DarkKnightRuntime.setProtected(ownerUuid, oldProtectedUuid, newProtectedTarget.getUuid());
        }

        snapTowardPosition(newProtectedTarget.getPos());
    }

    public float absorbDamage(float amount, DamageSource source) {
        if (this.getWorld().isClient() || amount <= 0.0F || !this.isAlive()) {
            return 0.0F;
        }

        float current = this.getShieldHealth();
        if (current <= 0.0F) {
            return 0.0F;
        }

        // Block the entire hit, even if this breaks the shield.
        float newHealth = current - amount;
        this.setShieldHealth(newHealth);
        this.snapTowardDamage(source);

        this.getWorld().playSound(
                null,
                this.getX(), this.getY(), this.getZ(),
                SoundEvents.BLOCK_AMETHYST_CLUSTER_HIT,
                SoundCategory.PLAYERS,
                0.9F,
                0.92F + (this.random.nextFloat() * 0.22F)
        );

        if (newHealth <= 0.0F) {
            shatter();
        }

        // Return full amount so the protected target takes none of this hit.
        return amount;
    }

    private void shatter() {
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) {
            this.discard();
            return;
        }

        this.getWorld().playSound(
                null,
                this.getX(), this.getY(), this.getZ(),
                SoundEvents.BLOCK_GLASS_BREAK,
                SoundCategory.PLAYERS,
                1.0F,
                0.85F
        );

        UUID ownerUuid = getOwnerUuid();
        if (ownerUuid != null) {
            Entity entity = serverWorld.getEntity(ownerUuid);
            if (entity instanceof ServerPlayerEntity ownerPlayer) {
                DarkKnightPower.onShieldBroken(ownerPlayer);
            }
        }

        recallAndDiscard(true); // broken = recalled empty
    }

    public void recallAndDiscard(boolean depletedOrBroken) {
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) {
            this.discard();
            return;
        }

        UUID ownerUuid = getOwnerUuid();
        UUID protectedUuid = getProtectedUuid();

        if (ownerUuid != null) {
            DarkKnightRuntime.stopActive(
                    ownerUuid,
                    protectedUuid,
                    depletedOrBroken ? 0.0F : this.getShieldHealth(),
                    serverWorld.getTime()
            );
        }

        this.discard();
    }

    private void snapTowardDamage(DamageSource source) {
        Entity attacker = source.getAttacker();
        if (attacker == null) {
            attacker = source.getSource();
        }
        if (attacker != null) {
            snapTowardPosition(attacker.getPos());
        }
    }

    private void snapTowardPosition(Vec3d pos) {
        LivingEntity protectedTarget = getProtectedEntity();
        if (protectedTarget == null) {
            return;
        }

        Vec3d center = protectedTarget.getPos().add(0.0D, protectedTarget.getHeight() * 0.62D, 0.0D);
        Vec3d dir = pos.subtract(center);
        if (dir.lengthSquared() <= 1.0E-4D) {
            return;
        }

        this.snapAngle = (float) Math.atan2(dir.z, dir.x);
        this.snapTicks = 8;
    }

    @Nullable
    public LivingEntity getOwnerEntity() {
        UUID uuid = getOwnerUuid();
        if (uuid == null) {
            return null;
        }
        Entity entity = findEntityByUuid(uuid);
        return entity instanceof LivingEntity living ? living : null;
    }

    @Nullable
    public LivingEntity getProtectedEntity() {
        UUID uuid = getProtectedUuid();
        if (uuid == null) {
            return null;
        }
        Entity entity = findEntityByUuid(uuid);
        return entity instanceof LivingEntity living ? living : null;
    }

    @Nullable
    private Entity findEntityByUuid(UUID uuid) {
        if (this.getWorld() instanceof ServerWorld serverWorld) {
            return serverWorld.getEntity(uuid);
        }
        return null;
    }

    @Nullable
    public UUID getOwnerUuid() {
        return this.dataTracker.get(OWNER_UUID).orElse(null);
    }

    public void setOwnerUuid(@Nullable UUID uuid) {
        this.dataTracker.set(OWNER_UUID, Optional.ofNullable(uuid));
    }

    @Nullable
    public UUID getProtectedUuid() {
        return this.dataTracker.get(PROTECTED_UUID).orElse(null);
    }

    public void setProtectedUuid(@Nullable UUID uuid) {
        this.dataTracker.set(PROTECTED_UUID, Optional.ofNullable(uuid));
    }

    public float getShieldHealth() {
        return this.getHealth();
    }

    public void setShieldHealth(float health) {
        this.setHealth(MathHelper.clamp(health, 0.0F, DarkKnightRuntime.MAX_SHIELD_HEALTH));
    }

    public int getTrackedTargetId() {
        return this.dataTracker.get(TARGET_ID);
    }

    public int getTrackedProtectedId() {
        return this.dataTracker.get(TARGET_ID);
    }

    public void setTargetEntityId(int id) {
        this.dataTracker.set(TARGET_ID, id);
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
    public boolean isAttackable() {
        return false;
    }


    public boolean collides() {
        return false;
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    protected void pushOutOfBlocks(double x, double y, double z) {}

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    @Override
    public boolean shouldRender(double distance) {
        return true;
    }

    @Override
    public EntitySpawnS2CPacket createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);

        UUID ownerUuid = getOwnerUuid();
        UUID protectedUuid = getProtectedUuid();

        if (ownerUuid != null) {
            nbt.putUuid("Owner", ownerUuid);
        }
        if (protectedUuid != null) {
            nbt.putUuid("Protected", protectedUuid);
        }

        nbt.putFloat("StoredHealth", this.getShieldHealth());
        nbt.putFloat("OrbitAngle", this.orbitAngle);
        nbt.putInt("TrackedTargetId", this.getTrackedTargetId());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);

        if (nbt.containsUuid("Owner")) {
            setOwnerUuid(nbt.getUuid("Owner"));
        }
        if (nbt.containsUuid("Protected")) {
            setProtectedUuid(nbt.getUuid("Protected"));
        }

        if (nbt.contains("StoredHealth")) {
            this.setShieldHealth(nbt.getFloat("StoredHealth"));
        }
        if (nbt.contains("OrbitAngle")) {
            this.orbitAngle = nbt.getFloat("OrbitAngle");
        }
        if (nbt.contains("TrackedTargetId")) {
            this.setTargetEntityId(nbt.getInt("TrackedTargetId"));
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, state -> {
            state.setAndContinue(IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}