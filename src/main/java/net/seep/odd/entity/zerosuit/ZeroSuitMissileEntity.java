package net.seep.odd.entity.zerosuit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;

import net.seep.odd.sound.ModSounds;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public class ZeroSuitMissileEntity extends Entity implements GeoEntity {

    private static final RawAnimation ANIM_FLY  = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, this::animPredicate));
    }

    private PlayState animPredicate(AnimationState<ZeroSuitMissileEntity> state) {
        if (this.getVelocity().lengthSquared() > 0.0025) state.setAnimation(ANIM_FLY);
        else state.setAnimation(ANIM_IDLE);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    // kept for renderer compatibility (always 0)
    private static final TrackedData<Float> ROLL =
            DataTracker.registerData(ZeroSuitMissileEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private UUID ownerUuid = null;
    private int lifeTicks = 0;

    // client-only looping sound holder
    private Object rocketFlySound = null;

    // tuning
    private static final int MAX_LIFE = 20 * 5;         // 5s
    private static final double DRAG = 0.995;           // slight drag
    private static final float EXPLOSION_POWER = 1.35f; // reduced damage

    // extra padding for entity raycast
    private static final double HIT_EXPAND = 0.35;

    public ZeroSuitMissileEntity(EntityType<? extends ZeroSuitMissileEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.noClip = false;
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(ROLL, 0f);
    }

    public float getRoll() {
        return this.dataTracker.get(ROLL);
    }

    public void setOwner(Entity owner) {
        this.ownerUuid = (owner != null) ? owner.getUuid() : null;
    }

    public Entity getOwner() {
        if (ownerUuid == null || getWorld() == null) return null;
        return getWorld().getPlayerByUuid(ownerUuid);
    }

    /** Fired projectile: set initial velocity + rotation from owner look. */
    public void initFromOwner(PlayerEntity owner, float speed) {
        if (owner == null) return;

        this.setYaw(owner.getYaw());
        this.setPitch(owner.getPitch());

        Vec3d dir = owner.getRotationVector().normalize();
        this.setVelocity(dir.multiply(Math.max(0.1, speed)));
    }

    private boolean canHit(Entity e) {
        if (e == null) return false;
        if (!e.isAlive()) return false;
        if (!e.isAttackable()) return false; // ✅ IMPORTANT (works for mobs)
        if (e == this) return false;

        Entity owner = getOwner();
        if (owner != null && e == owner) return false;

        if (e instanceof PlayerEntity pe) {
            if (pe.isSpectator()) return false;
            if (pe.getAbilities().creativeMode) return false;
        }
        return true;
    }

    public void detonate() {
        if (getWorld() == null) return;

        if (getWorld().isClient) {
            this.discard();
            return;
        }

        ServerWorld sw = (ServerWorld) getWorld();

        sw.spawnParticles(ParticleTypes.FLASH, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        sw.spawnParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 1, 0, 0, 0, 0);

        sw.playSound(null, getX(), getY(), getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.9f, 1.1f);

        Explosion ex = new Explosion(
                sw, this, null, null,
                getX(), getY(), getZ(),
                EXPLOSION_POWER, false,
                Explosion.DestructionType.KEEP
        );
        ex.collectBlocksAndDamageEntities();
        ex.affectWorld(true);

        this.discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld() == null) return;

        if (getWorld().isClient) {
            clientTickRocketSound();
            return;
        }

        lifeTicks++;
        if (lifeTicks >= MAX_LIFE) {
            detonate();
            return;
        }

        Vec3d vel = getVelocity().multiply(DRAG);
        Vec3d start = getPos();
        Vec3d end = start.add(vel);

        // keep facing velocity (visual)
        if (vel.lengthSquared() > 1.0e-6) {
            float yaw = (float)(MathHelper.atan2(vel.z, vel.x) * 57.2957763671875) - 90.0f;
            float pitch = (float)(-(MathHelper.atan2(vel.y, Math.sqrt(vel.x * vel.x + vel.z * vel.z)) * 57.2957763671875));
            setYaw(yaw);
            setPitch(pitch);
        }

        // -------- BLOCK raycast
        BlockHitResult bhr = getWorld().raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                this
        ));

        Vec3d endForEntity = end;
        double blockDist2 = Double.POSITIVE_INFINITY;

        if (bhr.getType() != HitResult.Type.MISS) {
            endForEntity = bhr.getPos();
            blockDist2 = start.squaredDistanceTo(endForEntity);
        }

        // -------- ENTITY raycast (clamped to block distance)
        Box box = this.getBoundingBox().stretch(endForEntity.subtract(start)).expand(HIT_EXPAND);
        EntityHitResult ehr = ProjectileUtil.getEntityCollision(
                getWorld(),
                this,
                start,
                endForEntity,
                box,
                this::canHit
        );

        if (ehr != null) {
            Vec3d hp = ehr.getPos();
            Vec3d back = vel.lengthSquared() > 1.0e-6 ? vel.normalize().multiply(-0.06) : Vec3d.ZERO;
            setPos(hp.x + back.x, hp.y + back.y, hp.z + back.z);
            detonate();
            return;
        }

        if (bhr.getType() != HitResult.Type.MISS) {
            Vec3d hp = bhr.getPos();
            Vec3d back = vel.lengthSquared() > 1.0e-6 ? vel.normalize().multiply(-0.06) : Vec3d.ZERO;
            setPos(hp.x + back.x, hp.y + back.y, hp.z + back.z);
            detonate();
            return;
        }

        // move
        setVelocity(vel);
        move(MovementType.SELF, vel);

        // ✅ catches “angled slide / graze” contacts that raycast can miss
        if (this.horizontalCollision || this.verticalCollision || this.isInsideWall()) {
            detonate();
            return;
        }

        if (getWorld() instanceof ServerWorld sw) {
            if ((this.age & 1) == 0) {
                sw.spawnParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 1, 0.02, 0.02, 0.02, 0.01);
                sw.spawnParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 1, 0.03, 0.03, 0.03, 0.002);
            }
        }

        fallDistance = 0f;
    }

    /* ======================= CLIENT: rocket loop sound ======================= */

    @Environment(EnvType.CLIENT)
    private void clientTickRocketSound() {
        if (this.isRemoved()) {
            clientStopRocketSound();
            return;
        }

        if (rocketFlySound == null) {
            var mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc == null || mc.getSoundManager() == null) return;

            RocketFlyLoop snd = new RocketFlyLoop(this);
            rocketFlySound = snd;
            mc.getSoundManager().play(snd);
        }
    }

    @Environment(EnvType.CLIENT)
    private void clientStopRocketSound() {
        if (rocketFlySound instanceof RocketFlyLoop loop) loop.stopNow();
        rocketFlySound = null;
    }

    @Environment(EnvType.CLIENT)
    private static final class RocketFlyLoop extends net.minecraft.client.sound.MovingSoundInstance {
        private final ZeroSuitMissileEntity missile;
        private boolean stopped = false;

        RocketFlyLoop(ZeroSuitMissileEntity missile) {
            super(ModSounds.ROCKET_FLY, SoundCategory.PLAYERS, net.minecraft.util.math.random.Random.create());
            this.missile = missile;
            this.repeat = true;
            this.repeatDelay = 0;
            this.volume = 0.55f;
            this.pitch = 1.0f;
            this.x = missile.getX();
            this.y = missile.getY();
            this.z = missile.getZ();
            this.attenuationType = net.minecraft.client.sound.SoundInstance.AttenuationType.LINEAR;
        }

        @Override
        public void tick() {
            if (missile == null || missile.isRemoved() || !missile.isAlive()) { stopped = true; return; }
            this.x = missile.getX(); this.y = missile.getY(); this.z = missile.getZ();

            double sp = missile.getVelocity().length();
            float t = (float) MathHelper.clamp(sp / 2.0, 0.0, 1.0);
            this.volume = 0.35f + 0.45f * t;
            this.pitch  = 0.95f + 0.10f * t;
        }

        @Override public boolean isDone() { return stopped; }
        void stopNow() { stopped = true; }
    }

    /* ======================= NBT ======================= */
    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) ownerUuid = nbt.getUuid("Owner");
        lifeTicks = nbt.getInt("Life");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (ownerUuid != null) nbt.putUuid("Owner", ownerUuid);
        nbt.putInt("Life", lifeTicks);
    }

    public boolean collides() { return true; }
    @Override public boolean isCollidable() { return true; }
}