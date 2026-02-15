// FILE: src/main/java/net/seep/odd/abilities/wizard/entity/WizardFireTornadoEntity.java
package net.seep.odd.abilities.wizard.entity;

import dev.architectury.networking.fabric.SpawnEntityPacket;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.world.World;

import java.util.List;

public class WizardFireTornadoEntity extends WizardTornadoEntity {

    // requested “power” bump: explosion scales with 3x feel
    public static float EXPLOSION_POWER = 2.2f * 3.0f;

    public WizardFireTornadoEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    @Override
    protected void spawnTornadoParticles(ServerWorld sw) {
        float scale = getScale();
        float ramp  = getRamp();

        double baseX = this.getX();
        double baseZ = this.getZ();
        double y0 = this.getY();

        int layers = MathHelper.ceil(6 * scale);
        double t = this.age * (0.20 + 0.10 * ramp);

        for (int layer = 0; layer < layers; layer++) {
            double yy = y0 + 0.2 + layer * 0.45;
            double rad = (0.38 + layer * 0.20) * scale * (0.35 + 0.65 * ramp);

            double a = t + layer * 0.85;
            double px = baseX + Math.cos(a) * rad;
            double pz = baseZ + Math.sin(a) * rad;

            sw.spawnParticles(ParticleTypes.FLAME, px, yy, pz, 1, 0.05, 0.08, 0.05, 0.01);

            if ((this.age + layer) % 2 == 0) {
                sw.spawnParticles(ParticleTypes.SMOKE, px, yy, pz, 1, 0.06, 0.08, 0.06, 0.01);
            }

            if ((this.age + layer) % 4 == 0) {
                sw.spawnParticles(ParticleTypes.LAVA, px, yy, pz, 1, 0.03, 0.05, 0.03, 0.01);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld() instanceof ServerWorld sw) {
            // extra fire ambience
            if (this.age % 14 == 0) {
                sw.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.AMBIENT,
                        0.95f, 0.85f + this.random.nextFloat() * 0.25f);
            }

            // burn anything inside (radius now 3x in base)
            float r = getEffectiveRadius();
            double h = getEffectiveHeight();

            Box box = new Box(this.getX()-r, this.getY()-1, this.getZ()-r, this.getX()+r, this.getY()+h, this.getZ()+r);
            List<LivingEntity> list = sw.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive());
            for (LivingEntity le : list) {
                if (ownerId != null && ownerId.equals(le.getUuid())) continue;
                le.setOnFireFor(2);
            }

            // explode right before death (bigger)
            if (this.age == (DURATION_TICKS - 1)) {
                sw.createExplosion(null, this.getX(), this.getY() + 0.2, this.getZ(), EXPLOSION_POWER, World.ExplosionSourceType.NONE);
            }
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return SpawnEntityPacket.create(this);
    }
}
