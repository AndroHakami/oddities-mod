package net.seep.odd.entity.zerosuit;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.UUID;

public class ZeroSuitMissileEntity extends Entity implements GeoEntity {

    /* ======================= geckolib ======================= */
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

    /* ======================= data tracker ======================= */
    private static final TrackedData<Float> ROLL =
            DataTracker.registerData(ZeroSuitMissileEntity.class, TrackedDataHandlerRegistry.FLOAT);

    /* ======================= state ======================= */
    private UUID ownerUuid = null;

    private float targetYaw;
    private float targetPitch;
    private float targetRoll;

    private int lifeTicks = 0;

    private boolean clientHasTargets = false;

    /* ======================= tuning ======================= */
    private static final double THRUST = 0.22;
    private static final double DRAG = 0.985;
    private static final double MAX_SPEED = 1.25;
    private static final double MIN_SPEED = 0.35;
    private static final float TURN_RATE = 6.5f;
    private static final int   MAX_LIFE = 20 * 12;

    // Exhaust visuals
    private static final double EXHAUST_BACK = 0.62;
    private static final double EXHAUST_UP   = 0.06;
    private static final double BOOSTER_SIDE = 0.20;

    public ZeroSuitMissileEntity(EntityType<? extends ZeroSuitMissileEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.noClip = false;
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(ROLL, 0f);
    }

    /* ======================= owner ======================= */
    public void setOwner(Entity owner) {
        this.ownerUuid = (owner != null) ? owner.getUuid() : null;
    }

    public Entity getOwner() {
        if (ownerUuid == null || getWorld() == null) return null;
        return getWorld().getPlayerByUuid(ownerUuid);
    }

    public void initFromOwner(PlayerEntity owner) {
        if (owner == null) return;

        this.targetYaw = MathHelper.wrapDegrees(owner.getYaw());
        this.targetPitch = MathHelper.clamp(owner.getPitch(), -80f, 80f);
        this.targetRoll = 0f;
        setRollTracked(0f);

        this.setYaw(this.targetYaw);
        this.setPitch(this.targetPitch);

        Vec3d fwd = Vec3d.fromPolar(this.targetPitch, this.targetYaw).normalize();
        this.setVelocity(fwd.multiply(Math.max(MIN_SPEED, 0.55)));
    }

    /* ======================= roll helpers ======================= */
    public float getRoll() {
        return this.dataTracker.get(ROLL);
    }

    private void setRollTracked(float roll) {
        roll = MathHelper.clamp(roll, -55f, 55f);
        float cur = this.dataTracker.get(ROLL);
        if (Math.abs(cur - roll) > 0.05f) this.dataTracker.set(ROLL, roll);
    }

    public void clientSetVisualRoll(float roll) {
        if (getWorld() != null && getWorld().isClient) {
            setRollTracked(roll);
        }
    }

    private Vec3d computeForwardVel(Vec3d forward) {
        double fwdSpeed = getVelocity().dotProduct(forward);

        if (fwdSpeed < MIN_SPEED) fwdSpeed = MIN_SPEED;

        fwdSpeed = Math.min(fwdSpeed + THRUST, MAX_SPEED);
        fwdSpeed *= DRAG;

        if (fwdSpeed > MAX_SPEED) fwdSpeed = MAX_SPEED;

        return forward.multiply(fwdSpeed);
    }

    /* ======================= steering API ======================= */
    public void serverSetRotation(float yaw, float pitch, float roll) {
        this.targetYaw = MathHelper.wrapDegrees(yaw);
        this.targetPitch = MathHelper.clamp(pitch, -80f, 80f);
        this.targetRoll = MathHelper.clamp(roll, -55f, 55f);
        setRollTracked(this.targetRoll);
    }

    public void clientSetDesiredRotation(float yaw, float pitch, float roll) {
        if (getWorld() == null || !getWorld().isClient) return;
        this.targetYaw = MathHelper.wrapDegrees(yaw);
        this.targetPitch = MathHelper.clamp(pitch, -80f, 80f);
        this.targetRoll = MathHelper.clamp(roll, -55f, 55f);
        this.clientHasTargets = true;
    }

    /* ======================= explode ======================= */
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
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 1.0f);

        Explosion ex = new Explosion(
                sw,
                this,
                null,
                null,
                getX(), getY(), getZ(),
                2.8f,
                false,
                Explosion.DestructionType.KEEP
        );
        ex.collectBlocksAndDamageEntities();
        ex.affectWorld(true);

        this.discard();
    }

    /* ======================= tick ======================= */
    @Override
    public void tick() {
        super.tick();
        if (getWorld() == null) return;

        if (getWorld().isClient) {
            if (clientHasTargets) clientPredictRotationOnlyStep();
            return;
        }

        serverAuthoritativeStep();
    }

    private void applyTurnStep() {
        float curYaw = getYaw();
        float curPitch = getPitch();

        float dy = MathHelper.wrapDegrees(targetYaw - curYaw);
        float dp = targetPitch - curPitch;

        float stepYaw = MathHelper.clamp(dy, -TURN_RATE, TURN_RATE);
        float stepPitch = MathHelper.clamp(dp, -TURN_RATE, TURN_RATE);

        curYaw += stepYaw;
        curPitch = MathHelper.clamp(curPitch + stepPitch, -80f, 80f);

        setYaw(curYaw);
        setPitch(curPitch);

        float rollNow = getRoll();
        float rollNew = MathHelper.lerp(0.18f, rollNow, targetRoll);
        setRollTracked(rollNew);
    }

    private void clientPredictRotationOnlyStep() {
        applyTurnStep();
    }

    private void serverAuthoritativeStep() {
        lifeTicks++;
        if (lifeTicks >= MAX_LIFE) {
            detonate();
            return;
        }

        applyTurnStep();

        Vec3d forward = Vec3d.fromPolar(getPitch(), getYaw()).normalize();
        Vec3d vel = computeForwardVel(forward);
        setVelocity(vel);

        Vec3d start = getPos();
        Vec3d end = start.add(vel);

        HitResult blockHit = getWorld().raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                this
        ));

        if (blockHit.getType() == HitResult.Type.BLOCK) {
            Vec3d hp = blockHit.getPos();
            Vec3d back = vel.lengthSquared() > 1.0e-6 ? vel.normalize().multiply(-0.08) : Vec3d.ZERO;
            setPos(hp.x + back.x, hp.y + back.y, hp.z + back.z);
            detonate();
            return;
        }

        this.move(MovementType.SELF, vel);

        // Rocket exhaust (server-side so everyone sees it)
        if (getWorld() instanceof ServerWorld sw) {
            spawnRocketTrail(sw, vel);
        }

        this.fallDistance = 0f;
    }

    private void spawnRocketTrail(ServerWorld sw, Vec3d vel) {
        double sp2 = vel.lengthSquared();
        if (sp2 < 1.0e-6) return;

        Vec3d dir = vel.normalize();

        double sp = Math.sqrt(sp2);
        float t = (float) MathHelper.clamp((sp - MIN_SPEED) / (MAX_SPEED - MIN_SPEED), 0.0, 1.0);

        int flameMain = 2 + (int) (4 * t);
        int smokeMain = 1 + (int) (2 * t);
        int spark     = (t > 0.65f) ? 1 : 0;

        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d right = dir.crossProduct(up);
        if (right.lengthSquared() < 1.0e-6) {
            right = dir.crossProduct(new Vec3d(1, 0, 0));
        }
        right = right.normalize();

        Vec3d tail = getPos()
                .subtract(dir.multiply(EXHAUST_BACK))
                .add(0, EXHAUST_UP, 0);

        Vec3d tailL = tail.add(right.multiply(BOOSTER_SIDE));
        Vec3d tailR = tail.subtract(right.multiply(BOOSTER_SIDE));

        sw.spawnParticles(ParticleTypes.FLAME, tail.x, tail.y, tail.z,
                flameMain, 0.03, 0.03, 0.03, 0.01 + 0.02 * t);
        sw.spawnParticles(ParticleTypes.SMOKE, tail.x, tail.y, tail.z,
                smokeMain, 0.05, 0.05, 0.05, 0.005 + 0.01 * t);

        if (spark > 0) {
            sw.spawnParticles(ParticleTypes.LAVA, tail.x, tail.y, tail.z,
                    1, 0.02, 0.02, 0.02, 0.01);
        }

        sw.spawnParticles(ParticleTypes.FLAME, tailL.x, tailL.y, tailL.z,
                1 + (int)(2 * t), 0.02, 0.02, 0.02, 0.01 + 0.02 * t);
        sw.spawnParticles(ParticleTypes.FLAME, tailR.x, tailR.y, tailR.z,
                1 + (int)(2 * t), 0.02, 0.02, 0.02, 0.01 + 0.02 * t);

        if ((this.age & 1) == 0) {
            sw.spawnParticles(ParticleTypes.SMOKE, tailL.x, tailL.y, tailL.z,
                    1, 0.03, 0.03, 0.03, 0.004);
            sw.spawnParticles(ParticleTypes.SMOKE, tailR.x, tailR.y, tailR.z,
                    1, 0.03, 0.03, 0.03, 0.004);
        }
    }

    /* ======================= NBT ======================= */
    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) ownerUuid = nbt.getUuid("Owner");
        targetYaw = MathHelper.wrapDegrees(nbt.getFloat("TYaw"));
        targetPitch = nbt.getFloat("TPitch");
        targetRoll = nbt.getFloat("TRoll");
        lifeTicks = nbt.getInt("Life");

        if (getWorld() != null && !getWorld().isClient) {
            setRollTracked(targetRoll);
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (ownerUuid != null) nbt.putUuid("Owner", ownerUuid);
        nbt.putFloat("TYaw", MathHelper.wrapDegrees(targetYaw));
        nbt.putFloat("TPitch", targetPitch);
        nbt.putFloat("TRoll", targetRoll);
        nbt.putInt("Life", lifeTicks);
    }

    public boolean collides() { return true; }

    @Override
    public boolean isCollidable() { return true; }
}
