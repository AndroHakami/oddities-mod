package net.seep.odd.entity.zerosuit;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;

import net.seep.odd.sound.ModSounds;
import net.seep.odd.status.ModStatusEffects;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public final class ZeroGrenadeEntity extends Entity implements GeoEntity {
    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("idle");

    private static final TrackedData<Boolean> ARMED =
            DataTracker.registerData(ZeroGrenadeEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> STUCK =
            DataTracker.registerData(ZeroGrenadeEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> FUSE =
            DataTracker.registerData(ZeroGrenadeEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final int FUSE_AFTER_TRIGGER_TICKS = 15; // 0.75s
    private static final int MAX_LIFE_TICKS = 20 * 10;
    private static final double GRAVITY = 0.045D;
    private static final double AIR_DRAG = 0.985D;
    private static final double FIRST_BOUNCE_DAMPING = 0.58D;
    private static final double HIT_EXPAND = 0.28D;
    private static final float EXPLOSION_POWER = 1.0f;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private UUID ownerUuid;
    private int lifeTicks;

    private UUID stuckTargetUuid;
    private double stuckOffsetX;
    private double stuckOffsetY;
    private double stuckOffsetZ;

    // one-bounce-only state
    private boolean hasBounced;
    private boolean settled;

    public ZeroGrenadeEntity(EntityType<? extends ZeroGrenadeEntity> type, World world) {
        super(type, world);
        this.setNoGravity(false);
        this.noClip = false;
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(ARMED, false);
        this.dataTracker.startTracking(STUCK, false);
        this.dataTracker.startTracking(FUSE, -1);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, this::animPredicate));
    }

    private PlayState animPredicate(AnimationState<ZeroGrenadeEntity> state) {
        state.setAnimation(ANIM_IDLE);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    public void setOwner(Entity owner) {
        this.ownerUuid = owner != null ? owner.getUuid() : null;
    }

    public Entity getOwner() {
        if (this.ownerUuid == null || this.getWorld() == null) return null;
        if (this.getWorld() instanceof ServerWorld sw) return sw.getEntity(this.ownerUuid);
        return this.getWorld().getPlayerByUuid(this.ownerUuid);
    }

    public void setLaunchVelocity(Vec3d velocity) {
        this.setVelocity(velocity);
        this.velocityDirty = true;
        this.updateFacingFromVelocity(velocity);

        this.setStuck(false);
        this.setSettled(false);
        this.setNoGravity(false);
        this.hasBounced = false;
    }

    public boolean isArmed() {
        return this.dataTracker.get(ARMED);
    }

    public boolean isStuck() {
        return this.dataTracker.get(STUCK);
    }

    private boolean isSettled() {
        return this.settled;
    }

    private void setArmed(boolean armed) {
        this.dataTracker.set(ARMED, armed);
    }

    private void setStuck(boolean stuck) {
        this.dataTracker.set(STUCK, stuck);
    }

    private void setSettled(boolean settled) {
        this.settled = settled;
    }

    private int getFuseTicks() {
        return this.dataTracker.get(FUSE);
    }

    private void setFuseTicks(int ticks) {
        this.dataTracker.set(FUSE, ticks);
    }

    private boolean canHit(Entity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity == this || entity == this.getOwner()) return false;
        if (!entity.isAttackable()) return false;

        if (entity instanceof PlayerEntity player && player.isSpectator()) {
            return false;
        }
        return true;
    }

    private void updateFacingFromVelocity(Vec3d vel) {
        if (vel.lengthSquared() <= 1.0e-6) return;

        float yaw = (float) (MathHelper.atan2(vel.z, vel.x) * 57.2957763671875D) - 90.0f;
        float pitch = (float) (-(MathHelper.atan2(vel.y, Math.sqrt(vel.x * vel.x + vel.z * vel.z)) * 57.2957763671875D));

        this.setYaw(yaw);
        this.setPitch(pitch);
    }

    private void playBounceSound() {
        if (this.getWorld() instanceof ServerWorld sw) {
            sw.playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.ZERO_BOUNCE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
    }

    private void startFuse() {
        if (this.getFuseTicks() >= 0) return;

        this.setArmed(true);
        this.setFuseTicks(FUSE_AFTER_TRIGGER_TICKS);

        if (this.getWorld() instanceof ServerWorld sw) {
            sw.playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.ZERO_TICK, SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
    }

    private void armFromBounce() {
        this.playBounceSound();
        this.startFuse();
    }

    private void stickTo(Entity target, Vec3d hitPos) {
        if (target == null) return;

        this.stuckTargetUuid = target.getUuid();
        Vec3d base = target.getPos();
        Vec3d offset = hitPos.subtract(base);

        this.stuckOffsetX = offset.x;
        this.stuckOffsetY = offset.y;
        this.stuckOffsetZ = offset.z;

        this.setSettled(false);
        this.setStuck(true);
        this.setNoGravity(true);
        this.setVelocity(Vec3d.ZERO);
        this.velocityDirty = true;
        this.setPosition(hitPos.x, hitPos.y, hitPos.z);

        this.playBounceSound();
        this.startFuse();

        if (target instanceof LivingEntity living) {
            living.addStatusEffect(new StatusEffectInstance(
                    ModStatusEffects.GRAVITY_SUSPEND, 20 * 1, 0, false, true, true
            ));
        }
    }

    private void settleAgainstBlock(BlockHitResult hit) {
        Direction side = hit.getSide();
        Vec3d normal = new Vec3d(side.getOffsetX(), side.getOffsetY(), side.getOffsetZ()).normalize();
        Vec3d hitPos = hit.getPos();

        this.setPosition(
                hitPos.x + normal.x * 0.06D,
                hitPos.y + normal.y * 0.06D,
                hitPos.z + normal.z * 0.06D
        );
        this.setVelocity(Vec3d.ZERO);
        this.velocityDirty = true;

        this.setSettled(true);
        this.setStuck(false);
        this.setNoGravity(true);

        this.playBounceSound();
        this.startFuse();
    }

    private void handleBlockBounce(BlockHitResult hit, Vec3d attemptedVelocity) {
        // first block hit = actual bounce
        if (!this.hasBounced) {
            Direction side = hit.getSide();
            Vec3d normal = new Vec3d(side.getOffsetX(), side.getOffsetY(), side.getOffsetZ()).normalize();

            Vec3d reflected = attemptedVelocity.subtract(normal.multiply(2.0D * attemptedVelocity.dotProduct(normal)));
            reflected = reflected.multiply(FIRST_BOUNCE_DAMPING);

            if (side.getAxis() == Direction.Axis.Y && reflected.y < 0.12D) {
                reflected = new Vec3d(reflected.x, 0.12D, reflected.z);
            }

            Vec3d hitPos = hit.getPos();
            this.setPosition(
                    hitPos.x + normal.x * 0.06D,
                    hitPos.y + normal.y * 0.06D,
                    hitPos.z + normal.z * 0.06D
            );
            this.setVelocity(reflected);
            this.velocityDirty = true;
            this.updateFacingFromVelocity(reflected);

            this.hasBounced = true;
            this.setSettled(false);
            this.setNoGravity(false);
            this.armFromBounce();
            return;
        }

        // any later block hit = stop dead, don't roll around
        this.settleAgainstBlock(hit);
    }

    public void detonate() {
        if (this.getWorld() == null) return;

        if (this.getWorld().isClient) {
            this.discard();
            return;
        }

        ServerWorld sw = (ServerWorld) this.getWorld();

        sw.spawnParticles(ParticleTypes.FLASH, this.getX(), this.getY(), this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        sw.spawnParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);

        sw.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.95f, 1.0f);

        Explosion explosion = new Explosion(
                sw, this, null, null,
                this.getX(), this.getY(), this.getZ(),
                EXPLOSION_POWER, false,
                Explosion.DestructionType.KEEP
        );
        explosion.collectBlocksAndDamageEntities();
        explosion.affectWorld(true);

        this.discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld() == null) return;

        if (this.getWorld().isClient) {
            return;
        }

        this.lifeTicks++;
        if (this.lifeTicks >= MAX_LIFE_TICKS) {
            this.detonate();
            return;
        }

        if (this.getFuseTicks() >= 0) {
            int fuse = this.getFuseTicks() - 1;
            this.setFuseTicks(fuse);
            if (fuse <= 0) {
                this.detonate();
                return;
            }
        }

        if (this.isStuck()) {
            ServerWorld sw = (ServerWorld) this.getWorld();
            Entity stuckTarget = this.stuckTargetUuid != null ? sw.getEntity(this.stuckTargetUuid) : null;
            if (stuckTarget == null || !stuckTarget.isAlive()) {
                this.detonate();
                return;
            }

            Vec3d base = stuckTarget.getPos();
            this.setPosition(
                    base.x + this.stuckOffsetX,
                    base.y + this.stuckOffsetY,
                    base.z + this.stuckOffsetZ
            );
            this.setVelocity(Vec3d.ZERO);
            this.velocityDirty = true;
            return;
        }

        if (this.isSettled()) {
            this.setVelocity(Vec3d.ZERO);
            this.velocityDirty = true;

            if (this.getWorld() instanceof ServerWorld sw && (this.age & 1) == 0) {
                sw.spawnParticles(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(),
                        1, 0.015D, 0.015D, 0.015D, 0.001D);
            }

            this.fallDistance = 0.0f;
            return;
        }

        Vec3d nextVelocity = this.getVelocity().multiply(AIR_DRAG).add(0.0D, -GRAVITY, 0.0D);
        Vec3d start = this.getPos();
        Vec3d end = start.add(nextVelocity);

        BlockHitResult blockHit = this.getWorld().raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                this
        ));

        Vec3d endForEntity = blockHit.getType() != HitResult.Type.MISS ? blockHit.getPos() : end;

        Box entityBox = this.getBoundingBox().stretch(endForEntity.subtract(start)).expand(HIT_EXPAND);
        EntityHitResult entityHit = ProjectileUtil.getEntityCollision(
                this.getWorld(),
                this,
                start,
                endForEntity,
                entityBox,
                this::canHit
        );

        if (entityHit != null) {
            this.stickTo(entityHit.getEntity(), entityHit.getPos());
            return;
        }

        if (blockHit.getType() != HitResult.Type.MISS) {
            this.handleBlockBounce(blockHit, nextVelocity);
            return;
        }

        this.setVelocity(nextVelocity);
        this.move(MovementType.SELF, nextVelocity);
        this.velocityDirty = true;
        this.updateFacingFromVelocity(nextVelocity);

        if (this.horizontalCollision || this.verticalCollision || this.isInsideWall()) {
            BlockPos pos = this.getBlockPos();
            this.handleBlockBounce(new BlockHitResult(this.getPos(), Direction.UP, pos, false), nextVelocity);
            return;
        }

        if (this.getWorld() instanceof ServerWorld sw && (this.age & 1) == 0) {
            sw.spawnParticles(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(),
                    1, 0.015D, 0.015D, 0.015D, 0.001D);
        }

        this.fallDistance = 0.0f;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) this.ownerUuid = nbt.getUuid("Owner");
        this.lifeTicks = nbt.getInt("Life");
        this.setArmed(nbt.getBoolean("Armed"));
        this.setStuck(nbt.getBoolean("Stuck"));
        this.setFuseTicks(nbt.getInt("Fuse"));

        this.hasBounced = nbt.getBoolean("HasBounced");
        this.settled = nbt.getBoolean("Settled");

        if (nbt.containsUuid("StuckTarget")) this.stuckTargetUuid = nbt.getUuid("StuckTarget");
        this.stuckOffsetX = nbt.getDouble("StuckOffsetX");
        this.stuckOffsetY = nbt.getDouble("StuckOffsetY");
        this.stuckOffsetZ = nbt.getDouble("StuckOffsetZ");

        this.setNoGravity(this.isStuck() || this.isSettled());
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (this.ownerUuid != null) nbt.putUuid("Owner", this.ownerUuid);
        nbt.putInt("Life", this.lifeTicks);
        nbt.putBoolean("Armed", this.isArmed());
        nbt.putBoolean("Stuck", this.isStuck());
        nbt.putInt("Fuse", this.getFuseTicks());

        nbt.putBoolean("HasBounced", this.hasBounced);
        nbt.putBoolean("Settled", this.isSettled());

        if (this.stuckTargetUuid != null) nbt.putUuid("StuckTarget", this.stuckTargetUuid);
        nbt.putDouble("StuckOffsetX", this.stuckOffsetX);
        nbt.putDouble("StuckOffsetY", this.stuckOffsetY);
        nbt.putDouble("StuckOffsetZ", this.stuckOffsetZ);
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }

    @Override
    public boolean isCollidable() {
        return true;
    }

    public boolean collides() {
        return true;
    }
}