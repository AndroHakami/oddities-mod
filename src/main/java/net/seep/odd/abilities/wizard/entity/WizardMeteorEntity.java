package net.seep.odd.abilities.wizard.entity;

import dev.architectury.networking.fabric.SpawnEntityPacket;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.seep.odd.abilities.wizard.MeteorImpactFx;
import net.seep.odd.mixin.BlockDisplayEntityInvoker;

import java.util.List;
import java.util.UUID;

public class WizardMeteorEntity extends Entity {

    // ✅ much faster drop (missile)
    public static final int DROP_TICKS = 25;

    public static final float IMPACT_RADIUS = 4.5f * 3.0f;
    public static final float IMPACT_DAMAGE = 10.0f * 2.0f;
    public static final double SHOCKWAVE_KB = 5.2;

    private UUID ownerId;

    private Vec3d target = null;
    private Vec3d start = null;

    // “big rock cluster”
    private static final Vec3d[] ROCK_OFFSETS = new Vec3d[] {
            new Vec3d(0.0,  0.0,  0.0),
            new Vec3d(0.45, 0.15, 0.10),
            new Vec3d(-0.40,0.10, 0.25),
            new Vec3d(0.20, 0.35,-0.35),
            new Vec3d(-0.25,-0.05,-0.25),
            new Vec3d(0.35, -0.10,0.35),
            new Vec3d(-0.10,0.55, 0.05),
            new Vec3d(0.10, 0.60,-0.10)
    };

    private DisplayEntity.BlockDisplayEntity[] rocks;
    private boolean spawned = false;

    public WizardMeteorEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    public void setOwnerId(UUID id) { this.ownerId = id; }
    public void setTarget(Vec3d at) { this.target = at; }

    @Override
    protected void initDataTracker() {}

    @Override
    public void tick() {
        super.tick();

        if (target == null) {
            if (!this.getWorld().isClient) this.discard();
            return;
        }

        if (start == null) start = this.getPos();

        if (this.getWorld() instanceof ServerWorld sw) {
            if (!spawned) {
                spawned = true;
                spawnRocks(sw);
            }

            // ✅ accelerate hard (missile)
            float t = MathHelper.clamp(this.age / (float)DROP_TICKS, 0f, 1f);
            float ease = t * t * t;

            double x = MathHelper.lerp(ease, start.x, target.x);
            double y = MathHelper.lerp(ease, start.y, target.y);
            double z = MathHelper.lerp(ease, start.z, target.z);

            this.setPos(x, y, z);
            tickRocksFollow();

            // heavy trail
            sw.spawnParticles(ParticleTypes.SMOKE, x, y, z, 12, 0.25, 0.25, 0.25, 0.01);
            sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.BLACKSTONE.getDefaultState()),
                    x, y, z, 14, 0.30, 0.30, 0.30, 0.18);
            sw.spawnParticles(ParticleTypes.ASH, x, y, z, 10, 0.25, 0.25, 0.25, 0.01);

            if (this.age >= DROP_TICKS) {
                doImpact(sw);
                cleanupRocks();
                this.discard();
            }
        }
    }

    private void spawnRocks(ServerWorld sw) {
        rocks = new DisplayEntity.BlockDisplayEntity[ROCK_OFFSETS.length];

        for (int i = 0; i < ROCK_OFFSETS.length; i++) {
            DisplayEntity.BlockDisplayEntity bd = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, sw);

            var state = switch (sw.random.nextInt(4)) {
                case 0 -> Blocks.BLACKSTONE.getDefaultState();
                case 1 -> Blocks.BASALT.getDefaultState();
                case 2 -> Blocks.MAGMA_BLOCK.getDefaultState();
                default -> Blocks.DEEPSLATE.getDefaultState();
            };

            ((BlockDisplayEntityInvoker)(Object)bd).odd$setBlockState(state);

            bd.setNoGravity(true);
            bd.setInvulnerable(true);
            bd.setSilent(true);

            Vec3d off = ROCK_OFFSETS[i];
            bd.refreshPositionAndAngles(
                    this.getX() + off.x,
                    this.getY() + off.y,
                    this.getZ() + off.z,
                    this.getYaw(),
                    this.getPitch()
            );

            sw.spawnEntity(bd);
            rocks[i] = bd;
        }
    }

    private void tickRocksFollow() {
        if (rocks == null) return;

        for (int i = 0; i < rocks.length; i++) {
            DisplayEntity.BlockDisplayEntity bd = rocks[i];
            if (bd == null || !bd.isAlive()) continue;

            Vec3d off = ROCK_OFFSETS[i];
            bd.refreshPositionAndAngles(
                    this.getX() + off.x,
                    this.getY() + off.y,
                    this.getZ() + off.z,
                    this.getYaw(),
                    this.getPitch()
            );
        }
    }

    private void cleanupRocks() {
        if (rocks == null) return;
        for (var bd : rocks) {
            if (bd != null && bd.isAlive()) bd.discard();
        }
        rocks = null;
    }

    private void doImpact(ServerWorld sw) {
        sw.playSound(null, target.x, target.y, target.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.35f, 0.75f);

        sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, target.x, target.y + 0.1, target.z, 1, 0, 0, 0, 0);
        sw.spawnParticles(ParticleTypes.FLAME, target.x, target.y + 0.25, target.z, 220, 4.0, 0.55, 4.0, 0.04);
        sw.spawnParticles(ParticleTypes.LAVA,  target.x, target.y + 0.35, target.z, 110, 3.0, 0.55, 3.0, 0.02);
        sw.spawnParticles(ParticleTypes.SMOKE, target.x, target.y + 0.90, target.z, 170, 5.0, 0.80, 5.0, 0.02);

        sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.BLACKSTONE.getDefaultState()),
                target.x, target.y + 0.20, target.z, 220, 4.0, 0.6, 4.0, 0.28);

        MeteorImpactFx.apply(sw, BlockPos.ofFloored(target), IMPACT_RADIUS, 0.0f);

        float r = IMPACT_RADIUS;
        Box box = new Box(target.x - r, target.y - 3.0, target.z - r, target.x + r, target.y + 8.0, target.z + r);

        List<net.minecraft.entity.LivingEntity> list =
                sw.getEntitiesByClass(net.minecraft.entity.LivingEntity.class, box, e -> e.isAlive());

        for (var le : list) {
            if (ownerId != null && ownerId.equals(le.getUuid())) continue;
            if (le.squaredDistanceTo(target) > r * r) continue;

            le.damage(sw.getDamageSources().magic(), IMPACT_DAMAGE);

            Vec3d away = le.getPos().subtract(target).normalize();
            le.addVelocity(away.x * SHOCKWAVE_KB, 0.75, away.z * SHOCKWAVE_KB);
            le.velocityModified = true;
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) ownerId = nbt.getUuid("Owner");
        if (nbt.contains("tx")) target = new Vec3d(nbt.getDouble("tx"), nbt.getDouble("ty"), nbt.getDouble("tz"));
        if (nbt.contains("sx")) start = new Vec3d(nbt.getDouble("sx"), nbt.getDouble("sy"), nbt.getDouble("sz"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (ownerId != null) nbt.putUuid("Owner", ownerId);
        if (target != null) {
            nbt.putDouble("tx", target.x); nbt.putDouble("ty", target.y); nbt.putDouble("tz", target.z);
        }
        if (start != null) {
            nbt.putDouble("sx", start.x); nbt.putDouble("sy", start.y); nbt.putDouble("sz", start.z);
        }
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return SpawnEntityPacket.create(this);
    }

    @Override
    public void remove(RemovalReason reason) {
        cleanupRocks();
        super.remove(reason);
    }
}