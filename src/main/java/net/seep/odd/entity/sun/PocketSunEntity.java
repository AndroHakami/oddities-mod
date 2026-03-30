package net.seep.odd.entity.sun;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class PocketSunEntity extends ExplosiveProjectileEntity implements GeoEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private static final TrackedData<Boolean> ARMED =
            DataTracker.registerData(PocketSunEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> HELD_VISUAL =
            DataTracker.registerData(PocketSunEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> CHARGE_PROGRESS =
            DataTracker.registerData(PocketSunEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int life = 0;

    public PocketSunEntity(EntityType<? extends ExplosiveProjectileEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(ARMED, false);
        this.dataTracker.startTracking(HELD_VISUAL, false);
        this.dataTracker.startTracking(CHARGE_PROGRESS, 0.0f);
    }

    public void setArmed(boolean armed) {
        this.dataTracker.set(ARMED, armed);
    }

    public boolean isArmed() {
        return this.dataTracker.get(ARMED);
    }

    public void setHeldVisual(boolean heldVisual) {
        this.dataTracker.set(HELD_VISUAL, heldVisual);
    }

    public boolean isHeldVisual() {
        return this.dataTracker.get(HELD_VISUAL);
    }

    public void setChargeProgress(float progress) {
        float clamped = MathHelper.clamp(progress, 0.0f, 1.0f);
        if (Math.abs(clamped - this.dataTracker.get(CHARGE_PROGRESS)) > 0.0001f) {
            this.dataTracker.set(CHARGE_PROGRESS, clamped);
            this.calculateDimensions();
        }
    }

    public float getChargeProgress() {
        return this.dataTracker.get(CHARGE_PROGRESS);
    }

    public float getVisualScale() {
        return 0.75f + (getChargeProgress() * 1.8f);
    }

    public float getBlastRadius() {
        return 2.6f + (getChargeProgress() * 3.0f);
    }

    public float getImpactDamage() {
        return 8.0f + (getChargeProgress() * 12.0f);
    }

    public float getCarryDamagePerTick() {
        return 1.0f + (getChargeProgress() * 2.5f);
    }

    public float getCarryPush() {
        return 0.45f + (getChargeProgress() * 0.65f);
    }

    public void setNoClipState(boolean value) {
        this.noClip = value;
    }

    public void freezeInPlace() {
        this.setVelocity(Vec3d.ZERO);
        this.powerX = 0.0;
        this.powerY = 0.0;
        this.powerZ = 0.0;
        this.scheduleVelocityUpdate();
    }

    public void launch(Vec3d dir, double speed) {
        if (dir.lengthSquared() < 1.0E-6) {
            dir = new Vec3d(0.0, 0.0, 1.0);
        } else {
            dir = dir.normalize();
        }

        this.setVelocity(dir.multiply(speed));
        this.powerX = dir.x * 0.1;
        this.powerY = dir.y * 0.1;
        this.powerZ = dir.z * 0.1;
        this.velocityModified = true;
        this.scheduleVelocityUpdate();
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (CHARGE_PROGRESS.equals(data)) {
            this.calculateDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(EntityPose pose) {
        float s = getVisualScale();
        float size = 0.70f * s;
        return EntityDimensions.changing(size, size);
    }

    private static boolean canPocketSunMove(LivingEntity entity) {
        if (entity == null) return false;

        double resistance = entity.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);

        // only immune if they have full knockback resistance
        return resistance < 1.0D;
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.hasNoGravity()) {
            this.setNoGravity(true);
        }

        if (!this.getWorld().isClient) {
            if (++life > 200) {
                this.discard();
                return;
            }

            if (!isArmed()) {
                this.setVelocity(Vec3d.ZERO);
                this.powerX = 0.0;
                this.powerY = 0.0;
                this.powerZ = 0.0;
            } else {
                dragVictims();
            }
        }
    }

    private void dragVictims() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        Entity owner = this.getOwner();
        Vec3d dir = this.getVelocity();
        if (dir.lengthSquared() < 1.0E-6) return;
        dir = dir.normalize();

        Box box = this.getBoundingBox().expand(0.8 + getChargeProgress());
        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, box, ent -> ent.isAlive() && ent != owner)) {
            e.damage(sw.getDamageSources().indirectMagic(this, owner), getCarryDamagePerTick());

            if (!canPocketSunMove(e)) continue;

            Vec3d push = dir.multiply(getCarryPush()).add(0.0, 0.05, 0.0);
            e.addVelocity(push.x, push.y, push.z);
            e.velocityModified = true;
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        if (!isArmed()) return;

        Entity hit = entityHitResult.getEntity();
        Entity owner = this.getOwner();

        if (hit instanceof LivingEntity living && hit != owner && this.getWorld() instanceof ServerWorld sw) {
            if (canPocketSunMove(living)) {
                Vec3d dir = this.getVelocity().lengthSquared() < 1.0E-6
                        ? new Vec3d(0, 0, 1)
                        : this.getVelocity().normalize();

                living.addVelocity(dir.x * getCarryPush(), dir.y * getCarryPush() * 0.35, dir.z * getCarryPush());
                living.velocityModified = true;
            }

            living.damage(sw.getDamageSources().indirectMagic(this, owner), getCarryDamagePerTick());
        }
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        if (!isArmed()) return;
        if (hitResult instanceof EntityHitResult) return;
        if (!(hitResult instanceof BlockHitResult)) return;

        if (!this.getWorld().isClient) {
            explodeSun();
            this.discard();
        }
    }

    private void explodeSun() {
        World w = this.getWorld();
        if (!(w instanceof ServerWorld sw)) return;

        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();
        float radius = getBlastRadius();
        float damage = getImpactDamage();

        sw.spawnParticles(ParticleTypes.FLAME, x, y, z, 64, 0.55, 0.55, 0.55, 0.02);
        sw.spawnParticles(ParticleTypes.END_ROD, x, y, z, 28, 0.45, 0.45, 0.45, 0.02);
        sw.spawnParticles(ParticleTypes.EXPLOSION, x, y, z, 3, 0.05, 0.05, 0.05, 0.01);
        sw.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE,
                net.minecraft.sound.SoundCategory.PLAYERS, 1.1f, 1.25f);

        Box box = this.getBoundingBox().expand(radius);
        Entity owner = this.getOwner();

        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, box, ent -> ent.isAlive())) {
            if (owner != null && e == owner) continue;

            double d2 = e.squaredDistanceTo(x, y, z);
            if (d2 > (radius * radius)) continue;

            float falloff = 1.0f - (float) (Math.sqrt(d2) / radius);
            float dealt = damage * Math.max(0.25f, falloff);
            e.damage(sw.getDamageSources().indirectMagic(this, owner), dealt);

            if (!canPocketSunMove(e)) continue;

            Vec3d kb = e.getPos().subtract(this.getPos());
            if (kb.lengthSquared() < 1.0E-6) kb = new Vec3d(0, 0.25, 0);
            kb = kb.normalize()
                    .multiply((0.9 + getChargeProgress() * 1.2) * Math.max(0.45, falloff))
                    .add(0, 0.2, 0);

            e.addVelocity(kb.x, kb.y, kb.z);
            e.velocityModified = true;
        }
    }

    @Override
    public boolean canHit() {
        return isArmed();
    }

    @Override
    public boolean isAttackable() {
        return isArmed();
    }

    @Override
    public float getTargetingMargin() {
        return isArmed() ? (0.20f + getChargeProgress() * 0.80f) : 0.0f;
    }

    @Override
    protected ParticleEffect getParticleType() {
        return ParticleTypes.FLAME;
    }

    @Override
    protected boolean isBurning() {
        return false;
    }

    @Override
    public boolean handleAttack(Entity attacker) {
        if (!isArmed() || attacker == null) return false;

        if (!this.getWorld().isClient) {
            Vec3d dir = attacker instanceof LivingEntity living
                    ? living.getRotationVec(1.0f)
                    : this.getPos().subtract(attacker.getPos());

            if (dir.lengthSquared() < 1.0E-6) {
                dir = new Vec3d(0.0, 0.0, 1.0);
            } else {
                dir = dir.normalize();
            }

            this.setOwner(attacker);
            this.setVelocity(dir.multiply(0.95));
            this.powerX = dir.x * 0.1;
            this.powerY = dir.y * 0.1;
            this.powerZ = dir.z * 0.1;
            this.velocityModified = true;
            this.scheduleVelocityUpdate();
            this.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.3f);
        }

        return true;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (!isArmed()) return false;
        Entity attacker = source.getAttacker();
        if (attacker != null) return handleAttack(attacker);
        return super.damage(source, amount);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Armed", isArmed());
        nbt.putBoolean("HeldVisual", isHeldVisual());
        nbt.putFloat("ChargeProgress", getChargeProgress());
        nbt.putInt("Life", life);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setArmed(nbt.getBoolean("Armed"));
        setHeldVisual(nbt.getBoolean("HeldVisual"));
        setChargeProgress(nbt.getFloat("ChargeProgress"));
        this.life = nbt.getInt("Life");
        this.calculateDimensions();
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
        return cache;
    }
}