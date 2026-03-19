package net.seep.odd.entity.bosswitch;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
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

public final class FlamingSkullEntity extends HostileEntity implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final float BLAST_RADIUS = 2.75f;
    private static final float BLAST_DAMAGE = 3.0f;
    private static final int MAX_AGE = 160;

    private @Nullable UUID targetUuid;
    private @Nullable UUID ownerUuid;
    private int life = 0;

    public FlamingSkullEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.noClip = false;
        this.experiencePoints = 0;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 2.0D)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 0.7D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.2D);
    }

    public void setTargetUuid(@Nullable UUID targetUuid) {
        this.targetUuid = targetUuid;
    }

    public void setOwnerUuid(@Nullable UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    @Override
    protected void initGoals() {
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        return new BirdNavigation(this, world);
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.hasNoGravity()) {
            this.setNoGravity(true);
        }

        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        this.life++;
        if (this.life > MAX_AGE) {
            explodeAndDiscard();
            return;
        }

        LivingEntity target = getTrackedTarget(sw);
        if (target != null && target.isAlive()) {
            Vec3d desired = target.getPos()
                    .add(0.0D, target.getStandingEyeHeight() * 0.55D, 0.0D)
                    .subtract(this.getPos());

            if (desired.lengthSquared() > 1.0E-6) {
                desired = desired.normalize().multiply(0.44D);
                Vec3d newVel = this.getVelocity().multiply(0.82D).add(desired.multiply(0.18D));
                this.setVelocity(newVel);
            }

            if (this.squaredDistanceTo(target) <= 1.35D * 1.35D) {
                explodeAndDiscard();
                return;
            }
        }

        if (this.getVelocity().lengthSquared() > 1.0E-6) {
            Vec3d vel = this.getVelocity().normalize();
            this.setYaw((float)(Math.atan2(vel.z, vel.x) * (180.0D / Math.PI)) - 90.0f);
            this.setPitch((float)(-(Math.atan2(vel.y, Math.sqrt(vel.x * vel.x + vel.z * vel.z)) * (180.0D / Math.PI))));
        }

        if (this.life > 5 && this.getWorld().getBlockCollisions(this, this.getBoundingBox().expand(0.04D)).iterator().hasNext()) {
            explodeAndDiscard();
            return;
        }

        sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 0.08D, this.getZ(),
                3, 0.06D, 0.06D, 0.06D, 0.001D);
    }

    private @Nullable LivingEntity getTrackedTarget(ServerWorld sw) {
        if (this.targetUuid == null) return null;
        Entity e = sw.getEntity(this.targetUuid);
        return e instanceof LivingEntity living ? living : null;
    }

    private @Nullable Entity getOwnerEntity(ServerWorld sw) {
        if (this.ownerUuid == null) return null;
        return sw.getEntity(this.ownerUuid);
    }

    private void explodeAndDiscard() {
        if (!(this.getWorld() instanceof ServerWorld sw)) {
            this.discard();
            return;
        }

        Entity owner = getOwnerEntity(sw);

        sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY(), this.getZ(),
                26, 0.18D, 0.18D, 0.18D, 0.025D);
        sw.spawnParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(), this.getZ(),
                10, 0.14D, 0.14D, 0.14D, 0.01D);
        this.playSound(SoundEvents.ENTITY_GENERIC_EXPLODE, 0.8f, 1.15f);

        Box box = this.getBoundingBox().expand(BLAST_RADIUS);
        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, box, living -> living.isAlive())) {
            if (owner != null && e == owner) continue;
            if (this.targetUuid != null && e.getUuid().equals(this.targetUuid)) {
                e.damage(sw.getDamageSources().indirectMagic(this, owner), BLAST_DAMAGE + 1.0f);
            } else if (e.squaredDistanceTo(this) <= BLAST_RADIUS * BLAST_RADIUS) {
                e.damage(sw.getDamageSources().indirectMagic(this, owner), BLAST_DAMAGE);
            }
        }

        this.discard();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean result = super.damage(source, amount);
        if (!this.getWorld().isClient && result && this.getHealth() <= 0.0f) {
            explodeAndDiscard();
            return true;
        }
        return result;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (this.targetUuid != null) nbt.putUuid("Target", this.targetUuid);
        if (this.ownerUuid != null) nbt.putUuid("Owner", this.ownerUuid);
        nbt.putInt("Life", this.life);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.targetUuid = nbt.containsUuid("Target") ? nbt.getUuid("Target") : null;
        this.ownerUuid = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
        this.life = nbt.getInt("Life");
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