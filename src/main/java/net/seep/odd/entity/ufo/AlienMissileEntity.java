package net.seep.odd.entity.ufo;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class AlienMissileEntity extends Entity implements GeoEntity {
    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("idle");

    private static final float CRUISE_SPEED = 0.72f;
    private static final float MAX_SPEED = 1.28f;
    private static final float ACCEL_PER_TICK = 0.045f;
    private static final float TURN_RATE = 0.18f;

    private static final int MAX_LIFETIME = 180;
    private static final float EXPLOSION_RADIUS = 4.5f;
    private static final int ARM_DELAY_TICKS = 10;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int ownerId = -1;
    private int targetId = -1;
    private int launchedLifetime = 0;
    private int armDelayTicks = ARM_DELAY_TICKS;

    private float currentSpeed = CRUISE_SPEED;
    private boolean deflected = false;

    public AlienMissileEntity(EntityType<? extends AlienMissileEntity> type, World world) {
        super(type, world);
        this.noClip = false;
        this.intersectionChecked = true;
        this.ignoreCameraFrustum = true;
    }

    public void setOwnerAndTarget(Entity owner, LivingEntity target) {
        this.ownerId = owner != null ? owner.getId() : -1;
        this.targetId = target != null ? target.getId() : -1;
        this.launchedLifetime = 0;
        this.armDelayTicks = ARM_DELAY_TICKS;
        this.currentSpeed = CRUISE_SPEED;
        this.deflected = false;
    }

    public float getRenderScale() {
        return 1.0f;
    }

    @Override
    protected void initDataTracker() {
    }

    @Override
    public boolean hasNoGravity() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean canHit() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.isRemoved()) return;

        this.launchedLifetime++;
        if (this.launchedLifetime > MAX_LIFETIME) {
            explode();
            return;
        }

        Vec3d velocity = this.getVelocity();

        if (velocity.lengthSquared() < 1.0E-4) {
            float yawRad = (this.getYaw() + 90.0f) * MathHelper.RADIANS_PER_DEGREE;
            velocity = new Vec3d(Math.cos(yawRad), 0.0, Math.sin(yawRad)).normalize().multiply(CRUISE_SPEED);
        }

        Vec3d currentDir = velocity.normalize();
        Vec3d newDir = currentDir;

        if (!this.deflected) {
            Entity targetEntity = this.getWorld().getEntityById(this.targetId);

            if (targetEntity instanceof LivingEntity living && living.isAlive() && !living.isSpectator()) {
                Vec3d aimPoint = living.getPos().add(0.0, living.getStandingEyeHeight() * 0.55, 0.0);
                Vec3d desiredDir = aimPoint.subtract(this.getPos());

                if (desiredDir.lengthSquared() > 1.0E-4) {
                    desiredDir = desiredDir.normalize();
                    newDir = currentDir.lerp(desiredDir, TURN_RATE).normalize();
                }
            }

            this.currentSpeed = Math.min(MAX_SPEED, this.currentSpeed + ACCEL_PER_TICK);
        } else {
            this.currentSpeed = Math.max(this.currentSpeed, MAX_SPEED * 0.92f);
        }

        velocity = newDir.multiply(this.currentSpeed);

        Vec3d start = this.getPos();
        Vec3d end = start.add(velocity);

        spawnSmokeTrail(newDir);

        if (this.armDelayTicks > 0) {
            this.armDelayTicks--;
            this.setVelocity(velocity);
            this.setPosition(end);
            updateRotationFromVelocity(velocity);
            return;
        }

        BlockHitResult blockHit = this.getWorld().raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                this
        ));

        if (blockHit.getType() != HitResult.Type.MISS) {
            this.setPosition(blockHit.getPos());
            explode();
            return;
        }

        Box box = this.getBoundingBox().stretch(velocity).expand(0.15);
        EntityHitResult entityHit = ProjectileUtil.getEntityCollision(
                this.getWorld(),
                this,
                start,
                end,
                box,
                e -> e.isAlive() && e.canHit() && e.getId() != this.ownerId && e != this
        );

        if (entityHit != null) {
            this.setPosition(entityHit.getPos());
            explode();
            return;
        }

        this.setVelocity(velocity);
        this.setPosition(end);
        updateRotationFromVelocity(velocity);
    }

    private void spawnSmokeTrail(Vec3d dir) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        Vec3d back = dir.multiply(-0.90);
        Vec3d base = this.getPos().add(back);

        sw.spawnParticles(ParticleTypes.SMOKE, base.x, base.y, base.z, 3, 0.10, 0.10, 0.10, 0.01);
        sw.spawnParticles(ParticleTypes.LARGE_SMOKE, base.x, base.y, base.z, 1, 0.04, 0.04, 0.04, 0.0);
    }

    private void updateRotationFromVelocity(Vec3d velocity) {
        if (velocity.lengthSquared() > 1.0E-4) {
            float yaw = (float)(MathHelper.atan2(velocity.z, velocity.x) * (180.0F / Math.PI)) - 90.0f;
            float horiz = (float)Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            float pitch = (float)(-(Math.atan2(velocity.y, horiz) * 180.0F / Math.PI));

            this.setYaw(yaw);
            this.setBodyYaw(yaw);
            this.setHeadYaw(yaw);
            this.setPitch(pitch);
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (this.getWorld().isClient) {
            return true;
        }

        Vec3d newDir = null;

        Entity attacker = source.getAttacker();
        if (attacker instanceof LivingEntity living) {
            Vec3d look = living.getRotationVec(1.0f);
            if (look.lengthSquared() > 1.0E-4) {
                newDir = look.normalize();
                this.ownerId = living.getId();
            }
        }

        if (newDir == null && source.getSource() != null) {
            Vec3d fromHit = this.getPos().subtract(source.getSource().getPos());
            if (fromHit.lengthSquared() > 1.0E-4) {
                newDir = fromHit.normalize();
                this.ownerId = source.getSource().getId();
            }
        }

        if (newDir == null) {
            Vec3d v = this.getVelocity();
            newDir = v.lengthSquared() > 1.0E-4 ? v.normalize().multiply(-1.0) : new Vec3d(0.0, 0.0, 1.0);
        }

        this.deflected = true;
        this.targetId = -1;
        this.armDelayTicks = Math.max(this.armDelayTicks, 3);
        this.currentSpeed = Math.max(this.currentSpeed, MAX_SPEED * 0.95f);
        this.setVelocity(newDir.normalize().multiply(this.currentSpeed));
        this.velocityModified = true;
        updateRotationFromVelocity(this.getVelocity());

        return true;
    }

    private void explode() {
        if (this.getWorld().isClient) return;

        if (this.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY(), this.getZ(), 1, 0, 0, 0, 0);
        }

        this.getWorld().playSound(
                null,
                this.getX(), this.getY(), this.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.HOSTILE,
                1.0f,
                0.95f
        );

        Box box = this.getBoundingBox().expand(EXPLOSION_RADIUS);
        for (LivingEntity e : this.getWorld().getEntitiesByClass(LivingEntity.class, box, ent -> ent.isAlive() && ent.getId() != this.ownerId)) {
            double dist = this.squaredDistanceTo(e);
            double maxDist = EXPLOSION_RADIUS * EXPLOSION_RADIUS;
            if (dist > maxDist) continue;

            float damage = (float) (10.0 * (1.0 - Math.sqrt(dist) / EXPLOSION_RADIUS));
            if (damage > 0.0f) {
                e.damage(this.getDamageSources().generic(), damage);
            }
        }

        this.discard();
    }


    public boolean damage(net.minecraft.entity.damage.DamageSource source, float amount, boolean bypass) {
        return super.damage(source, amount);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "alien_missile.controller", 0, state -> {
            state.setAndContinue(ANIM_IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.ownerId = nbt.getInt("OwnerId");
        this.targetId = nbt.getInt("TargetId");
        this.launchedLifetime = nbt.getInt("LaunchedLifetime");
        this.armDelayTicks = nbt.contains("ArmDelayTicks") ? nbt.getInt("ArmDelayTicks") : ARM_DELAY_TICKS;
        this.currentSpeed = nbt.contains("CurrentSpeed") ? nbt.getFloat("CurrentSpeed") : CRUISE_SPEED;
        this.deflected = nbt.getBoolean("Deflected");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("OwnerId", this.ownerId);
        nbt.putInt("TargetId", this.targetId);
        nbt.putInt("LaunchedLifetime", this.launchedLifetime);
        nbt.putInt("ArmDelayTicks", this.armDelayTicks);
        nbt.putFloat("CurrentSpeed", this.currentSpeed);
        nbt.putBoolean("Deflected", this.deflected);
    }
}