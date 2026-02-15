// FILE: src/main/java/net/seep/odd/abilities/wizard/entity/WizardMeteorEntity.java
package net.seep.odd.abilities.wizard.entity;

import dev.architectury.networking.fabric.SpawnEntityPacket;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
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

import java.util.List;
import java.util.UUID;

public class WizardMeteorEntity extends Entity {

    // ✅ 4 seconds drop time
    public static final int DROP_TICKS = 20 * 4;

    // requested: radius x3, damage x2
    public static final float IMPACT_RADIUS = 6.5f * 3.0f;     // 19.5
    public static final float IMPACT_DAMAGE = 18.0f * 2.0f;     // 36.0
    public static final double SHOCKWAVE_KB = 2.2;

    private UUID ownerId;

    private Vec3d target = null;
    private Vec3d start = null;

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

        // capture start on first tick
        if (start == null) start = this.getPos();

        // ✅ smooth descent over 4 seconds
        float t = MathHelper.clamp(this.age / (float)DROP_TICKS, 0f, 1f);

        // easing so it feels like it “speeds up”
        float ease = t * t * (3f - 2f * t); // smoothstep

        double x = MathHelper.lerp(ease, start.x, target.x);
        double y = MathHelper.lerp(ease, start.y, target.y);
        double z = MathHelper.lerp(ease, start.z, target.z);

        this.setPos(x, y, z);

        if (this.getWorld() instanceof ServerWorld sw) {
            // trail
            sw.spawnParticles(ParticleTypes.FLAME, x, y, z, 8, 0.35, 0.35, 0.35, 0.01);
            sw.spawnParticles(ParticleTypes.SMOKE, x, y, z, 6, 0.35, 0.35, 0.35, 0.01);

            // warning ring intensifies near impact
            if (this.age % 6 == 0) {
                sw.spawnParticles(ParticleTypes.LAVA, target.x, target.y + 0.15, target.z, 10, 1.8, 0.05, 1.8, 0.01);
            }

            // impact when finished
            if (this.age >= DROP_TICKS) {
                doImpact(sw);
                this.discard();
            }
        }
    }

    private void doImpact(ServerWorld sw) {
        // BOOM (no destroy)
        sw.playSound(null, target.x, target.y, target.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.25f, 0.80f);

        // big hit particles
        sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, target.x, target.y + 0.1, target.z, 1, 0, 0, 0, 0);
        sw.spawnParticles(ParticleTypes.FLAME, target.x, target.y + 0.25, target.z, 340, 6.0, 0.75, 6.0, 0.04);
        sw.spawnParticles(ParticleTypes.LAVA,  target.x, target.y + 0.35, target.z, 140, 4.5, 0.60, 4.5, 0.02);
        sw.spawnParticles(ParticleTypes.SMOKE, target.x, target.y + 0.90, target.z, 220, 6.5, 0.90, 6.5, 0.02);

        // debris feel
        sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.MAGMA_BLOCK.getDefaultState()),
                target.x, target.y + 0.20, target.z, 160, 4.2, 0.5, 4.2, 0.25);

        // ✅ VISUAL CRATER: magma + fiery blocks (using your MeteorImpactFx)
        // IMPORTANT: pass damage=0 so you don't double-damage (entity damage is handled below)
        MeteorImpactFx.apply(sw, BlockPos.ofFloored(target), IMPACT_RADIUS, 0.0f);

        // damage + shockwave (radius already x3, damage x2)
        float r = IMPACT_RADIUS;
        Box box = new Box(target.x - r, target.y - 3.0, target.z - r, target.x + r, target.y + 8.0, target.z + r);

        List<net.minecraft.entity.LivingEntity> list =
                sw.getEntitiesByClass(net.minecraft.entity.LivingEntity.class, box, e -> e.isAlive());

        for (var le : list) {
            if (ownerId != null && ownerId.equals(le.getUuid())) continue;
            if (le.squaredDistanceTo(target) > r * r) continue;

            le.damage(sw.getDamageSources().magic(), IMPACT_DAMAGE);

            Vec3d away = le.getPos().subtract(target).normalize();
            le.addVelocity(away.x * SHOCKWAVE_KB, 0.90, away.z * SHOCKWAVE_KB);
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
}
