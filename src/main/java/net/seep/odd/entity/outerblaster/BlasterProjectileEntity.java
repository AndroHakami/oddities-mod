package net.seep.odd.entity.outerblaster;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import net.seep.odd.item.outerblaster.OuterBlasterFxNet;
import net.seep.odd.sound.ModSounds;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class BlasterProjectileEntity extends ExplosiveProjectileEntity implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private static final int MAX_AGE = 26;
    private static final float DIRECT_DAMAGE = 4.0f;
    private static final float SPLASH_DAMAGE = 1.5f;
    private static final float BLAST_RADIUS = 2.8f;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int life;
    private boolean detonated;

    public BlasterProjectileEntity(EntityType<? extends ExplosiveProjectileEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.hasNoGravity()) {
            this.setNoGravity(true);
        }

        syncRotationToVelocity();

        if (!this.getWorld().isClient) {
            life++;
            if (life > MAX_AGE) {
                explodeAt(this.getPos(), null);
            }
        }
    }

    public void syncRotationToVelocity() {
        Vec3d v = this.getVelocity();
        if (v.lengthSquared() > 1.0E-6) {
            this.setYaw((float) (MathHelper.atan2(v.x, v.z) * (180.0F / Math.PI)));
            this.setPitch((float) (MathHelper.atan2(v.y, v.horizontalLength()) * (180.0F / Math.PI)));
        }
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        if (detonated || this.getWorld().isClient) return;

        if (hitResult.getType() == HitResult.Type.ENTITY) {
            onEntityHit((EntityHitResult) hitResult);
        } else if (hitResult.getType() == HitResult.Type.BLOCK) {
            onBlockHit((BlockHitResult) hitResult);
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        if (detonated || this.getWorld().isClient) return;

        Entity hit = entityHitResult.getEntity();
        Entity owner = this.getOwner();

        if (hit instanceof LivingEntity living) {
            DamageSource source = this.getWorld().getDamageSources().indirectMagic(this, owner);
            living.damage(source, DIRECT_DAMAGE);
        }

        explodeAt(entityHitResult.getPos(), hit);
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        if (detonated || this.getWorld().isClient) return;
        explodeAt(blockHitResult.getPos(), null);
    }

    private void explodeAt(Vec3d impact, Entity alreadyHit) {
        World world = this.getWorld();
        if (!(world instanceof ServerWorld sw) || detonated) return;

        detonated = true;

        sw.playSound(
                null,
                impact.x, impact.y, impact.z,
                ModSounds.OUTER_BLASTER_EXPLOSION,
                SoundCategory.PLAYERS,
                1.1f,
                1.0f
        );

        sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, impact.x, impact.y, impact.z, 24, 0.20, 0.20, 0.20, 0.18);
        sw.spawnParticles(ParticleTypes.SMOKE, impact.x, impact.y, impact.z, 8, 0.10, 0.10, 0.10, 0.01);

        Box area = new Box(
                impact.x - BLAST_RADIUS, impact.y - BLAST_RADIUS, impact.z - BLAST_RADIUS,
                impact.x + BLAST_RADIUS, impact.y + BLAST_RADIUS, impact.z + BLAST_RADIUS
        );

        Entity owner = this.getOwner();

        for (LivingEntity living : sw.getEntitiesByClass(LivingEntity.class, area, LivingEntity::isAlive)) {
            if (living == owner) continue;
            if (alreadyHit != null && living == alreadyHit) continue;

            double d2 = living.squaredDistanceTo(impact);
            if (d2 > BLAST_RADIUS * BLAST_RADIUS) continue;

            float falloff = 1.0f - (float) (Math.sqrt(d2) / BLAST_RADIUS);
            float damage = 2.0f + SPLASH_DAMAGE * Math.max(0.0f, falloff);
            living.damage(sw.getDamageSources().indirectMagic(this, owner), damage);
        }

        OuterBlasterFxNet.sendImpact(sw, impact, BLAST_RADIUS * 1.6f, 11);
        this.discard();
    }

    @Override
    protected ParticleEffect getParticleType() {
        return ParticleTypes.ELECTRIC_SPARK;
    }

    @Override
    protected boolean isBurning() {
        return false;
    }

    @Override
    public boolean canHit() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Life", life);
        nbt.putBoolean("Detonated", detonated);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.life = nbt.getInt("Life");
        this.detonated = nbt.getBoolean("Detonated");
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