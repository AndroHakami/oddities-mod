package net.seep.odd.abilities.wizard.entity;

import dev.architectury.networking.fabric.SpawnEntityPacket;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

public class WizardTornadoEntity extends Entity {

    public static int DURATION_TICKS = 20 * 5;

    // ✅ smaller base radius
    public static float SUCK_RADIUS = 5.4f;

    public static double SPEED = 0.16;

    public static double PULL_STRENGTH = 0.85;
    public static double DAMP = 0.42;

    private static final double BASE_HEIGHT = 3.0;

    private Vec3d dir = Vec3d.ZERO;
    protected UUID ownerId;

    public WizardTornadoEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    public void setDirection(Vec3d d) { this.dir = d.normalize(); }
    public void setOwnerId(UUID id) { this.ownerId = id; }

    @Override protected void initDataTracker() {}

    // ✅ slightly smaller overall, but dense
    protected float getScale() { return 1.9f; }
    protected float getRamp()  { return MathHelper.clamp(this.age / 18.0f, 0f, 1f); }
    protected float getEffectiveRadius() { return SUCK_RADIUS * getScale(); }
    protected double getEffectiveHeight() { return BASE_HEIGHT * getScale(); }

    @Override
    public void tick() {
        super.tick();

        // stationary
        this.setVelocity(Vec3d.ZERO);
        this.velocityDirty = true;

        if (this.age >= DURATION_TICKS) {
            if (!this.getWorld().isClient) this.discard();
            return;
        }

        if (this.getWorld() instanceof ServerWorld sw) {
            // ✅ short wind “whoosh” so it doesn't ring out forever
            if (this.age % 14 == 0) {
                sw.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ITEM_TRIDENT_RIPTIDE_1, SoundCategory.AMBIENT,
                        0.65f, 0.85f + this.random.nextFloat() * 0.20f);
            }

            spawnTornadoParticles(sw);

            float radius = getEffectiveRadius();
            double height = getEffectiveHeight();
            float ramp = getRamp();

            Box box = new Box(
                    this.getX() - radius, this.getY() - 1.0, this.getZ() - radius,
                    this.getX() + radius, this.getY() + height, this.getZ() + radius
            );

            List<LivingEntity> list = sw.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive());
            for (LivingEntity le : list) {
                if (ownerId != null && ownerId.equals(le.getUuid())) continue;
                if (le.isSpectator()) continue;

                Vec3d toCenter = new Vec3d(this.getX() - le.getX(), 0.0, this.getZ() - le.getZ());
                double dist = Math.max(0.35, toCenter.length());

                double strength = PULL_STRENGTH * getScale() * (0.25 + 0.75 * ramp);
                Vec3d pull = toCenter.normalize().multiply((strength / dist) * 0.25);

                Vec3d v = le.getVelocity();
                le.setVelocity(v.x * DAMP, v.y * 0.85, v.z * DAMP);

                // ✅ NO upward lift anymore
                le.addVelocity(pull.x, 0.0, pull.z);
                le.velocityModified = true;
            }
        }
    }

    // ✅ denser / tighter particles
    protected void spawnTornadoParticles(ServerWorld sw) {
        float scale = getScale();
        float ramp = getRamp();

        double baseX = this.getX();
        double baseZ = this.getZ();
        double y0 = this.getY();

        int layers = MathHelper.ceil(12 * scale);
        double spin = this.age * (0.22 + 0.10 * ramp);

        for (int layer = 0; layer < layers; layer++) {
            double yy = y0 + 0.15 + layer * 0.23;

            // tighter radius growth
            double rad = (0.22 + layer * 0.07) * scale;
            rad *= (0.35 + 0.65 * ramp);

            // multiple points per layer = denser column
            for (int k = 0; k < 2; k++) {
                double a = spin + layer * 0.55 + k * (Math.PI);
                double px = baseX + Math.cos(a) * rad;
                double pz = baseZ + Math.sin(a) * rad;

                sw.spawnParticles(ParticleTypes.CLOUD, px, yy, pz, 1, 0.02, 0.03, 0.02, 0.005);

                if ((this.age + layer + k) % 4 == 0) {
                    sw.spawnParticles(ParticleTypes.CRIT, px, yy, pz, 1, 0.01, 0.01, 0.01, 0.01);
                }
            }
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) ownerId = nbt.getUuid("Owner");
        if (nbt.contains("dx")) dir = new Vec3d(nbt.getDouble("dx"), nbt.getDouble("dy"), nbt.getDouble("dz"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (ownerId != null) nbt.putUuid("Owner", ownerId);
        nbt.putDouble("dx", dir.x);
        nbt.putDouble("dy", dir.y);
        nbt.putDouble("dz", dir.z);
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return SpawnEntityPacket.create(this);
    }
}