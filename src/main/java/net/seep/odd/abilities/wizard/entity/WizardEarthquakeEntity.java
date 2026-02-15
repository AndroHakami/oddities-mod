// FILE: src/main/java/net/seep/odd/abilities/wizard/entity/WizardEarthquakeEntity.java
package net.seep.odd.abilities.wizard.entity;

import dev.architectury.networking.fabric.SpawnEntityPacket;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.world.World;

import net.seep.odd.abilities.power.WizardPower;

import java.util.List;
import java.util.UUID;

public class WizardEarthquakeEntity extends Entity {

    public static int DURATION_TICKS = 20 * 4;
    public static float RADIUS = 30f;

    private static final int DAMAGE_INTERVAL = 10;
    private static final float DAMAGE_PER_HIT = 1.0f;

    private UUID ownerId;

    public WizardEarthquakeEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    public void setOwnerId(UUID id) { this.ownerId = id; }

    @Override protected void initDataTracker() {}

    @Override
    public void tick() {
        super.tick();

        if (this.age >= DURATION_TICKS) {
            if (!this.getWorld().isClient) this.discard();
            return;
        }

        if (this.getWorld() instanceof ServerWorld sw) {
            if ((this.age % 2) == 0) {
                sw.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        this.getX(), this.getY() + 0.1, this.getZ(),
                        8,
                        RADIUS * 0.10, 0.05, RADIUS * 0.10,
                        0.01
                );
            }

            if ((this.age % DAMAGE_INTERVAL) == 0) {
                Box box = new Box(
                        this.getX() - RADIUS, this.getY() - 2.0, this.getZ() - RADIUS,
                        this.getX() + RADIUS, this.getY() + 3.0, this.getZ() + RADIUS
                );

                List<LivingEntity> list = sw.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive());
                DamageSource src = sw.getDamageSources().magic();

                for (LivingEntity le : list) {
                    if (ownerId != null && ownerId.equals(le.getUuid())) continue;
                    if (le.squaredDistanceTo(this) > (RADIUS * RADIUS)) continue;

                    le.damage(src, DAMAGE_PER_HIT);
                    le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 1, false, true, true));

                    // screen shake only for players hit
                    if (le instanceof ServerPlayerEntity sp) {
                        WizardPower.sendScreenShake(sp, 14, 1.0f);
                    }
                }
            }
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) ownerId = nbt.getUuid("Owner");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (ownerId != null) nbt.putUuid("Owner", ownerId);
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return SpawnEntityPacket.create(this);
    }
}
