package net.seep.odd.entity.dragoness;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
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

import java.util.UUID;

public final class UfoProtectorEntity extends HostileEntity implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private static final TrackedData<Integer> PARENT_ID =
            DataTracker.registerData(UfoProtectorEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> ORBIT_INDEX_TRACKED =
            DataTracker.registerData(UfoProtectorEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> ORBIT_COUNT_TRACKED =
            DataTracker.registerData(UfoProtectorEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private @Nullable UUID parentUuid;
    private int orbitIndex;
    private int orbitCount = 6;

    public UfoProtectorEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
        this.ignoreCameraFrustum = true;
        this.experiencePoints = 0;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 18.0D)
                .add(EntityAttributes.GENERIC_ARMOR, 2.0D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0D);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(PARENT_ID, -1);
        this.dataTracker.startTracking(ORBIT_INDEX_TRACKED, 0);
        this.dataTracker.startTracking(ORBIT_COUNT_TRACKED, 6);
    }

    public void setParent(DragonessEntity parent, int orbitIndex, int orbitCount) {
        this.parentUuid = parent.getUuid();
        this.orbitIndex = orbitIndex;
        this.orbitCount = Math.max(1, orbitCount);

        this.dataTracker.set(PARENT_ID, parent.getId());
        this.dataTracker.set(ORBIT_INDEX_TRACKED, orbitIndex);
        this.dataTracker.set(ORBIT_COUNT_TRACKED, Math.max(1, orbitCount));
    }

    public boolean hasParent(UUID uuid) {
        return this.parentUuid != null && this.parentUuid.equals(uuid);
    }

    @Override
    public void tick() {
        super.tick();

        this.setNoGravity(true);
        this.fallDistance = 0.0f;

        this.orbitIndex = this.dataTracker.get(ORBIT_INDEX_TRACKED);
        this.orbitCount = Math.max(1, this.dataTracker.get(ORBIT_COUNT_TRACKED));

        DragonessEntity parent = getParentDragoness();

        if (parent == null || !parent.isAlive()
                || (parent.getAttackType() != DragonessAttackType.CHILL_LOOP
                && parent.getAttackType() != DragonessAttackType.CHILL_STANCE)) {
            if (!this.getWorld().isClient()) {
                this.discard();
            }
            return;
        }

        double t = (this.age + this.orbitIndex * 7.0D) * 0.10D;
        double angle = t + (Math.PI * 2.0D * this.orbitIndex / (double) this.orbitCount);
        double radius = 6.72D + Math.sin(t * 1.9D + this.orbitIndex * 0.7D) * 2.3D;
        double bob = 2.8D + Math.sin(t * 1.65D + this.orbitIndex) * 1.25D;

        LivingEntity focus = parent.getTarget();
        if (focus != null && !focus.isAlive()) {
            focus = null;
        }
        Vec3d center = parent.getPos().add(0.0D, 3.0D, 0.0D);
        if (focus != null) {
            Vec3d toward = focus.getPos().subtract(parent.getPos());
            Vec3d flat = new Vec3d(toward.x, 0.0D, toward.z);
            if (flat.lengthSquared() > 1.0E-6D) {
                center = center.add(flat.normalize().multiply(1.6D));
            }
        }
        Vec3d desired = center.add(Math.cos(angle) * radius, bob, Math.sin(angle) * radius);
        Vec3d move = desired.subtract(this.getPos());

        this.setVelocity(move.multiply(0.30D));
        this.velocityModified = true;
        this.setPosition(this.getPos().add(this.getVelocity()));

        if (!this.getWorld().isClient() && this.age % 4 == 0) {
            damageBeamColumn();
        }
    }

    private void damageBeamColumn() {
        Box beamBox = new Box(
                this.getX() - 0.55D, this.getY() - 24.0D, this.getZ() - 0.55D,
                this.getX() + 0.55D, this.getY(), this.getZ() + 0.55D
        );

        for (LivingEntity living : this.getWorld().getEntitiesByClass(
                LivingEntity.class,
                beamBox,
                e -> e.isAlive() && !(e instanceof UfoProtectorEntity) && !(e instanceof DragonessEntity)
        )) {
            if (living instanceof PlayerEntity player && player.isSpectator()) continue;
            living.damage(this.getDamageSources().mobAttack(this), 3.5f);
        }
    }

    private @Nullable DragonessEntity getParentDragoness() {
        int parentId = this.dataTracker.get(PARENT_ID);

        Entity byId = parentId >= 0 ? this.getWorld().getEntityById(parentId) : null;
        if (byId instanceof DragonessEntity dragoness) {
            return dragoness;
        }

        if (!(this.getWorld() instanceof ServerWorld serverWorld)) {
            return null;
        }

        if (this.parentUuid == null) {
            return null;
        }

        Entity byUuid = serverWorld.getEntity(this.parentUuid);
        return byUuid instanceof DragonessEntity dragoness ? dragoness : null;
    }

    public float getSpawnScale(float tickDelta) {
        return MathHelper.clamp((this.age + tickDelta) / 10.0f, 0.0f, 1.0f);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hasNoGravity() {
        return true;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BLOCK_BEACON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.BLOCK_GLASS_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BLOCK_GLASS_BREAK;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (this.parentUuid != null) nbt.putUuid("Parent", this.parentUuid);
        nbt.putInt("OrbitIndex", this.orbitIndex);
        nbt.putInt("OrbitCount", this.orbitCount);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.parentUuid = nbt.containsUuid("Parent") ? nbt.getUuid("Parent") : null;
        this.orbitIndex = nbt.getInt("OrbitIndex");
        this.orbitCount = Math.max(1, nbt.getInt("OrbitCount"));

        this.dataTracker.set(ORBIT_INDEX_TRACKED, this.orbitIndex);
        this.dataTracker.set(ORBIT_COUNT_TRACKED, this.orbitCount);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            state.setAndContinue(IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}