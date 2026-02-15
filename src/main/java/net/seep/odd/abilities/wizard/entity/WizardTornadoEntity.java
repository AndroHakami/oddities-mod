// FILE: src/main/java/net/seep/odd/abilities/wizard/entity/WizardTornadoEntity.java
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

    // base tuning (we multiply by 3x in runtime)
    public static float SUCK_RADIUS = 3.8f;

    // old movement kept for compatibility (but tornado no longer moves)
    public static double SPEED = 0.16;

    public static double PULL_STRENGTH = 0.55;
    public static double DAMP = 0.45;

    private static final double BASE_HEIGHT = 3.2;

    private Vec3d dir = Vec3d.ZERO;
    protected UUID ownerId;

    public WizardTornadoEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    public void setDirection(Vec3d d) { this.dir = d.normalize(); }
    public void setOwnerId(UUID id) { this.ownerId = id; }

    @Override protected void initDataTracker() {}

    protected float getScale() { return 3.0f; } // requested 3x
    protected float getRamp()  { return MathHelper.clamp(this.age / 30.0f, 0f, 1f); } // “takes a moment”
    protected float getEffectiveRadius() { return SUCK_RADIUS * getScale(); }
    protected double getEffectiveHeight() { return BASE_HEIGHT * getScale(); }

    @Override
    public void tick() {
        super.tick();

        // stationary: ensure no drift
        this.setVelocity(Vec3d.ZERO);
        this.velocityDirty = true;

        if (this.age >= DURATION_TICKS) {
            if (this.getWorld() instanceof ServerWorld sw) launchEntitiesUp(sw);
            if (!this.getWorld().isClient) this.discard();
            return;
        }

        if (this.getWorld() instanceof ServerWorld sw) {
            // windy sound (everyone nearby)
            if (this.age % 12 == 0) {
                sw.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.AMBIENT,
                        0.85f, 0.75f + this.random.nextFloat() * 0.35f);
            }

            spawnTornadoParticles(sw);

            float radius = getEffectiveRadius();
            double height = getEffectiveHeight();
            float ramp = getRamp();

            // suck/pull entities (power ramps)
            Box box = new Box(
                    this.getX() - radius, this.getY() - 1.0, this.getZ() - radius,
                    this.getX() + radius, this.getY() + height, this.getZ() + radius
            );

            List<LivingEntity> list = sw.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive());
            for (LivingEntity le : list) {
                if (ownerId != null && ownerId.equals(le.getUuid())) continue;
                if (le.isSpectator()) continue;

                Vec3d toCenter = new Vec3d(this.getX() - le.getX(), 0.0, this.getZ() - le.getZ());
                double dist = Math.max(0.45, toCenter.length());

                // ramped strength: starts weak -> becomes nasty
                double strength = PULL_STRENGTH * getScale() * (0.15 + 0.85 * ramp);

                Vec3d pull = toCenter.normalize().multiply((strength / dist) * 0.22);

                // damp sideways speed so they “stick”
                Vec3d v = le.getVelocity();
                le.setVelocity(v.x * DAMP, v.y, v.z * DAMP);

                // lift also ramps
                double lift = 0.08 * getScale() * (0.10 + 0.90 * ramp);

                le.addVelocity(pull.x, lift, pull.z);
                le.velocityModified = true;
            }
        }
    }

    protected void spawnTornadoParticles(ServerWorld sw) {
        float scale = getScale();
        float ramp = getRamp();

        double baseX = this.getX();
        double baseZ = this.getZ();
        double y0 = this.getY();

        int layers = MathHelper.ceil(6 * scale); // 18 layers at 3x
        double spin = this.age * (0.18 + 0.10 * ramp);

        for (int layer = 0; layer < layers; layer++) {
            double yy = y0 + 0.2 + layer * 0.45;
            double rad = (0.35 + layer * 0.18) * scale;

            // early ramp: smaller/softer column
            rad *= (0.35 + 0.65 * ramp);

            double a = spin + layer * 0.8;
            double px = baseX + Math.cos(a) * rad;
            double pz = baseZ + Math.sin(a) * rad;

            sw.spawnParticles(ParticleTypes.CLOUD, px, yy, pz, 1, 0.06, 0.08, 0.06, 0.01);

            if ((this.age + layer) % 3 == 0) {
                sw.spawnParticles(ParticleTypes.CRIT, px, yy, pz, 1, 0.02, 0.02, 0.02, 0.01);
            }
        }
    }

    private void launchEntitiesUp(ServerWorld sw) {
        float radius = getEffectiveRadius();
        double height = getEffectiveHeight();

        Box box = new Box(
                this.getX() - radius, this.getY() - 1.0, this.getZ() - radius,
                this.getX() + radius, this.getY() + height, this.getZ() + radius
        );

        List<LivingEntity> list = sw.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive());
        for (LivingEntity le : list) {
            if (ownerId != null && ownerId.equals(le.getUuid())) continue;
            le.addVelocity(0.0, 1.25 * getScale(), 0.0); // power x3
            le.velocityModified = true;
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
