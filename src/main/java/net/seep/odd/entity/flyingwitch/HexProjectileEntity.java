package net.seep.odd.entity.flyingwitch;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class HexProjectileEntity extends ExplosiveProjectileEntity implements GeoEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final float BLAST_RADIUS = 3.0f;
    private static final float MAGIC_DAMAGE = 4.0f;
    private static final int MAX_AGE = 200;

    private boolean armed = false;
    private boolean heldVisual = false;
    private int life = 0;

    public HexProjectileEntity(EntityType<? extends ExplosiveProjectileEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public void setArmed(boolean armed) {
        this.armed = armed;
    }

    public boolean isArmed() {
        return this.armed;
    }

    public void setHeldVisual(boolean heldVisual) {
        this.heldVisual = heldVisual;
    }

    public boolean isHeldVisual() {
        return this.heldVisual;
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.hasNoGravity()) {
            this.setNoGravity(true);
        }

        if (!this.getWorld().isClient) {
            if (++life > MAX_AGE) {
                this.discard();
                return;
            }

            // Held charge state: visible, but harmless and stationary
            if (!armed) {
                this.setVelocity(Vec3d.ZERO);
                this.powerX = 0.0;
                this.powerY = 0.0;
                this.powerZ = 0.0;
            }
        }
    }

    @Override
    public boolean canHit() {
        return armed;
    }

    @Override
    public boolean isAttackable() {
        return armed;
    }


    public boolean collides() {
        return armed;
    }

    @Override
    public float getTargetingMargin() {
        return armed ? 0.35f : 0.0f;
    }

    private void deflectFrom(Entity newOwner, Vec3d dir) {
        if (dir.lengthSquared() < 1.0E-6) {
            dir = new Vec3d(0.0, 0.0, 1.0);
        } else {
            dir = dir.normalize();
        }

        this.setOwner(newOwner);
        this.setVelocity(dir.multiply(0.95));
        this.powerX = dir.x * 0.1;
        this.powerY = dir.y * 0.1;
        this.powerZ = dir.z * 0.1;

        this.setYaw(newOwner.getYaw());
        this.setPitch(newOwner.getPitch());

        this.velocityModified = true;
        this.scheduleVelocityUpdate();
    }

    private void deflectFromAttacker(Entity attacker) {
        Vec3d dir;

        if (attacker instanceof LivingEntity living) {
            dir = living.getRotationVec(1.0f);
        } else {
            dir = this.getPos().subtract(attacker.getPos());
        }

        deflectFrom(attacker, dir);
        this.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.15f);
    }

    @Override
    public boolean handleAttack(Entity attacker) {
        if (!armed || attacker == null) {
            return false;
        }

        if (!this.getWorld().isClient) {
            deflectFromAttacker(attacker);
        }

        return true;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (!armed) return false;

        Entity attacker = source.getAttacker();
        if (attacker != null) {
            if (!this.getWorld().isClient) {
                deflectFromAttacker(attacker);
            }
            return true;
        }

        return super.damage(source, amount);
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        if (!armed) return;

        if (!this.getWorld().isClient) {
            explodeMagic();
            this.discard();
        }
    }

    private boolean isBlockedByShield(LivingEntity target) {
        if (!target.isBlocking()) return false;

        Vec3d look = target.getRotationVec(1.0f);
        Vec3d flatLook = new Vec3d(look.x, 0.0, look.z);
        if (flatLook.lengthSquared() < 1.0E-6) return false;
        flatLook = flatLook.normalize();

        Vec3d toExplosion = new Vec3d(
                this.getX() - target.getX(),
                0.0,
                this.getZ() - target.getZ()
        );

        if (toExplosion.lengthSquared() < 1.0E-6) return true;
        toExplosion = toExplosion.normalize();

        return flatLook.dotProduct(toExplosion) > 0.0;
    }

    private void explodeMagic() {
        World w = this.getWorld();
        if (!(w instanceof ServerWorld sw)) return;

        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();

        sw.spawnParticles(ParticleTypes.WITCH, x, y, z, 36, 0.35, 0.35, 0.35, 0.05);
        sw.spawnParticles(ParticleTypes.PORTAL, x, y, z, 18, 0.25, 0.25, 0.25, 0.02);

        Box box = this.getBoundingBox().expand(BLAST_RADIUS);
        Entity owner = this.getOwner();

        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, box, ent -> ent.isAlive())) {
            if (owner != null && e == owner) continue;

            double d2 = e.squaredDistanceTo(x, y, z);
            if (d2 > (BLAST_RADIUS * BLAST_RADIUS)) continue;

            if (isBlockedByShield(e)) {
                e.playSound(SoundEvents.ITEM_SHIELD_BLOCK, 1.0f, 0.9f + sw.random.nextFloat() * 0.2f);
                continue;
            }

            e.damage(sw.getDamageSources().indirectMagic(this, owner), MAGIC_DAMAGE);
        }
    }

    @Override
    protected ParticleEffect getParticleType() {
        return ParticleTypes.WITCH;
    }

    @Override
    protected boolean isBurning() {
        return false;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Armed", armed);
        nbt.putBoolean("HeldVisual", heldVisual);
        nbt.putInt("Life", life);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.armed = nbt.getBoolean("Armed");
        this.heldVisual = nbt.getBoolean("HeldVisual");
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
        return cache;
    }

    public void noClip(boolean b) {
        this.noClip = b;
    }

    public void setNoClip(boolean b) {
        this.noClip = b;
    }
}