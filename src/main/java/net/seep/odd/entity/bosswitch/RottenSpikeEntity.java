package net.seep.odd.entity.bosswitch;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class RottenSpikeEntity extends Entity {
    public static final int CHARGE_TICKS = 16;
    private static final int MAX_LIFE = 60;

    private static final float DAMAGE = 5.0f;
    private static final double SPEED = 0.82D;
    private static final double HIT_KNOCKBACK_XZ = 2.05D;
    private static final double HIT_KNOCKBACK_Y = 0.46D;

    private static final TrackedData<Integer> ORIGIN_X =
            DataTracker.registerData(RottenSpikeEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> ORIGIN_Y =
            DataTracker.registerData(RottenSpikeEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> ORIGIN_Z =
            DataTracker.registerData(RottenSpikeEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private @Nullable UUID ownerUuid;

    private double targetX;
    private double targetZ;
    private double groundY;

    private boolean launched = false;
    private int life = 0;

    private final Set<UUID> alreadyHit = new HashSet<>();

    public RottenSpikeEntity(EntityType<? extends RottenSpikeEntity> type, World world) {
        super(type, world);
        this.noClip = false;
        this.setNoGravity(true);
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(ORIGIN_X, 0);
        this.dataTracker.startTracking(ORIGIN_Y, 0);
        this.dataTracker.startTracking(ORIGIN_Z, 0);
    }

    public void setOwnerUuid(@Nullable UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public void setOriginBlock(BlockPos pos) {
        this.dataTracker.set(ORIGIN_X, pos.getX());
        this.dataTracker.set(ORIGIN_Y, pos.getY());
        this.dataTracker.set(ORIGIN_Z, pos.getZ());
    }

    public BlockPos getOriginBlockPos() {
        return new BlockPos(
                this.dataTracker.get(ORIGIN_X),
                this.dataTracker.get(ORIGIN_Y),
                this.dataTracker.get(ORIGIN_Z)
        );
    }

    public void setLaunchTarget(Vec3d target) {
        this.targetX = target.x;
        this.targetZ = target.z;
        this.groundY = target.y;
    }

    public float getChargeProgress(float tickDelta) {
        return Math.min(1.0f, (this.age + tickDelta) / (float) CHARGE_TICKS);
    }

    public boolean isLaunchedVisual() {
        return this.launched;
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.hasNoGravity()) {
            this.setNoGravity(true);
        }

        this.life++;

        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        // stay glued to chosen floor height
        this.setPosition(this.getX(), this.groundY, this.getZ());

        if (!this.launched) {
            this.setVelocity(Vec3d.ZERO);

            if (this.life == 1) {
                this.playSound(SoundEvents.BLOCK_ROOTED_DIRT_BREAK, 1.0f, 0.72f);
            }

            if (this.life % 2 == 0) {
                spawnRiseParticles(sw);
            }

            if (this.life >= CHARGE_TICKS) {
                launch(sw);
            }

            return;
        }

        Vec3d vel = this.getVelocity();
        vel = new Vec3d(vel.x, 0.0D, vel.z);
        this.setVelocity(vel);

        this.move(MovementType.SELF, vel);
        this.setPosition(this.getX(), this.groundY, this.getZ());

        if (this.life > MAX_LIFE) {
            burstAndDiscard(sw);
            return;
        }

        if (this.horizontalCollision) {
            burstAndDiscard(sw);
            return;
        }

        double dx = this.targetX - this.getX();
        double dz = this.targetZ - this.getZ();
        if ((dx * dx + dz * dz) <= 3.2D * 3.2D) {
            burstAndDiscard(sw);
            return;
        }

        if (this.life % 2 == 0) {
            spawnSlideParticles(sw, vel);
        }

        damageEntities(sw);
    }

    private void launch(ServerWorld sw) {
        Vec3d to = new Vec3d(this.targetX - this.getX(), 0.0D, this.targetZ - this.getZ());
        Vec3d dir = to.lengthSquared() > 1.0E-6 ? to.normalize() : new Vec3d(0.0D, 0.0D, 1.0D);

        this.launched = true;
        this.setVelocity(dir.multiply(SPEED));
        this.velocityModified = true;

        this.playSound(SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1.05f, 0.75f);
        this.playSound(SoundEvents.BLOCK_MUD_BREAK, 0.95f, 0.85f);

        BlockState burst = sampleOriginState(0, 0, 0);
        sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, burst),
                this.getX(), this.getY() + 1.0D, this.getZ(),
                20, 0.8D, 0.55D, 0.8D, 0.04D);
    }

    private void spawnRiseParticles(ServerWorld sw) {
        BlockState low = sampleOriginState(this.random.nextInt(3) - 1, -1, this.random.nextInt(3) - 1);
        BlockState top = sampleOriginState(this.random.nextInt(3) - 1, 0, this.random.nextInt(3) - 1);

        sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, low),
                this.getX(), this.getY() + 0.25D, this.getZ(),
                8, 0.9D, 0.15D, 0.9D, 0.01D);

        sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, top),
                this.getX(), this.getY() + 1.55D, this.getZ(),
                5, 0.7D, 0.10D, 0.7D, 0.01D);
    }

    private void spawnSlideParticles(ServerWorld sw, Vec3d vel) {
        Vec3d flat = new Vec3d(vel.x, 0.0D, vel.z);
        if (flat.lengthSquared() < 1.0E-6) {
            flat = new Vec3d(0.0D, 0.0D, -1.0D);
        } else {
            flat = flat.normalize();
        }

        Vec3d back = flat.multiply(-1.2D);

        BlockState trailA = sampleOriginState(this.random.nextInt(3) - 1, -1, this.random.nextInt(3) - 1);
        BlockState trailB = sampleOriginState(this.random.nextInt(3) - 1, 0, this.random.nextInt(3) - 1);

        sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, trailA),
                this.getX() + back.x, this.getY() + 0.15D, this.getZ() + back.z,
                10, 0.9D, 0.08D, 0.9D, 0.02D);

        sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, trailB),
                this.getX() + back.x * 0.55D, this.getY() + 1.25D, this.getZ() + back.z * 0.55D,
                4, 0.55D, 0.05D, 0.55D, 0.01D);
    }

    private BlockState sampleOriginState(int ox, int oy, int oz) {
        BlockPos origin = this.getOriginBlockPos();
        BlockPos pos = origin.add(ox, oy, oz);

        BlockState state = this.getWorld().getBlockState(pos);
        if (!state.isAir()) return state;

        state = this.getWorld().getBlockState(pos.down());
        if (!state.isAir()) return state;

        state = this.getWorld().getBlockState(pos.down(2));
        if (!state.isAir()) return state;

        return Blocks.DIRT.getDefaultState();
    }

    private void damageEntities(ServerWorld sw) {
        Entity owner = this.ownerUuid == null ? null : sw.getEntity(this.ownerUuid);
        Box hitBox = this.getBoundingBox().expand(0.15D);

        boolean hitSomething = false;

        for (LivingEntity living : sw.getEntitiesByClass(LivingEntity.class, hitBox, e -> e.isAlive())) {
            if (owner != null && living == owner) continue;
            if (this.alreadyHit.contains(living.getUuid())) continue;

            this.alreadyHit.add(living.getUuid());
            hitSomething = true;

            living.damage(sw.getDamageSources().indirectMagic(this, owner), DAMAGE);

            Vec3d dir = living.getPos().subtract(this.getPos());
            Vec3d flat = new Vec3d(dir.x, 0.0D, dir.z);

            if (flat.lengthSquared() < 1.0E-6) {
                flat = this.getVelocity().lengthSquared() > 1.0E-6
                        ? new Vec3d(this.getVelocity().x, 0.0D, this.getVelocity().z)
                        : new Vec3d(1.0D, 0.0D, 0.0D);
            }

            flat = flat.normalize();

            living.takeKnockback(2.35D, -flat.x, -flat.z);
            living.addVelocity(flat.x * HIT_KNOCKBACK_XZ, HIT_KNOCKBACK_Y, flat.z * HIT_KNOCKBACK_XZ);
            living.velocityModified = true;
        }

        if (hitSomething) {
            burstAndDiscard(sw);
        }
    }

    private void burstAndDiscard(ServerWorld sw) {
        BlockState burstA = sampleOriginState(0, -1, 0);
        BlockState burstB = sampleOriginState(0, 0, 0);

        sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, burstA),
                this.getX(), this.getY() + 0.95D, this.getZ(),
                24, 1.15D, 0.65D, 1.15D, 0.03D);

        sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, burstB),
                this.getX(), this.getY() + 1.45D, this.getZ(),
                10, 0.75D, 0.35D, 0.75D, 0.02D);

        this.playSound(SoundEvents.BLOCK_MUD_BREAK, 1.0f, 0.75f);
        this.playSound(SoundEvents.BLOCK_ROOTED_DIRT_BREAK, 1.0f, 0.80f);
        this.discard();
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) {
            this.ownerUuid = nbt.getUuid("Owner");
        } else {
            this.ownerUuid = null;
        }

        this.dataTracker.set(ORIGIN_X, nbt.getInt("OriginX"));
        this.dataTracker.set(ORIGIN_Y, nbt.getInt("OriginY"));
        this.dataTracker.set(ORIGIN_Z, nbt.getInt("OriginZ"));

        this.targetX = nbt.getDouble("TargetX");
        this.targetZ = nbt.getDouble("TargetZ");
        this.groundY = nbt.getDouble("GroundY");
        this.launched = nbt.getBoolean("Launched");
        this.life = nbt.getInt("Life");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (this.ownerUuid != null) {
            nbt.putUuid("Owner", this.ownerUuid);
        }

        BlockPos origin = this.getOriginBlockPos();
        nbt.putInt("OriginX", origin.getX());
        nbt.putInt("OriginY", origin.getY());
        nbt.putInt("OriginZ", origin.getZ());

        nbt.putDouble("TargetX", this.targetX);
        nbt.putDouble("TargetZ", this.targetZ);
        nbt.putDouble("GroundY", this.groundY);
        nbt.putBoolean("Launched", this.launched);
        nbt.putInt("Life", this.life);
    }


    public boolean collides() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}