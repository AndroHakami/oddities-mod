// FILE: src/main/java/net/seep/odd/abilities/wizard/entity/WizardSteamCloudEntity.java
package net.seep.odd.abilities.wizard.entity;

import dev.architectury.networking.fabric.SpawnEntityPacket;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.world.World;

import java.util.List;

public class WizardSteamCloudEntity extends Entity {

    public static int DURATION_TICKS = 20 * 10; // 10s
    public static float RADIUS = 4.5f;

    public WizardSteamCloudEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    @Override
    protected void initDataTracker() {}

    @Override
    public void tick() {
        super.tick();

        if (this.age >= DURATION_TICKS) {
            if (!this.getWorld().isClient) this.discard();
            return;
        }

        if (this.getWorld() instanceof ServerWorld sw) {
            // particles
            if ((this.age % 2) == 0) {
                sw.spawnParticles(ParticleTypes.CLOUD,
                        this.getX(), this.getY() + 0.2, this.getZ(),
                        14,
                        RADIUS * 0.18, 0.35, RADIUS * 0.18,
                        0.01
                );
                sw.spawnParticles(ParticleTypes.SMOKE,
                        this.getX(), this.getY() + 0.15, this.getZ(),
                        10,
                        RADIUS * 0.18, 0.25, RADIUS * 0.18,
                        0.01
                );
            }

            // blindness refresh every tick inside
            Box box = new Box(
                    this.getX() - RADIUS, this.getY() - 1.0, this.getZ() - RADIUS,
                    this.getX() + RADIUS, this.getY() + 2.0, this.getZ() + RADIUS
            );

            List<LivingEntity> list = sw.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator());
            for (LivingEntity le : list) {
                if (le.squaredDistanceTo(this) <= (RADIUS * RADIUS)) {
                    le.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0, false, true, true));
                }
            }
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {}

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {}

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return SpawnEntityPacket.create(this);
    }
}
