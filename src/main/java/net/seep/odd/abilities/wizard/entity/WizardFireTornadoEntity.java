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

    public static float EXPLOSION_POWER = 2.2f;

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

        int layers = MathHelper.ceil(12 * scale);
        double t = this.age * (0.24 + 0.10 * ramp);

        for (int layer = 0; layer < layers; layer++) {
            double yy = y0 + 0.15 + layer * 0.23;
            double rad = (0.22 + layer * 0.07) * scale * (0.35 + 0.65 * ramp);

            for (int k = 0; k < 2; k++) {
                double a = t + layer * 0.55 + k * Math.PI;
                double px = baseX + Math.cos(a) * rad;
                double pz = baseZ + Math.sin(a) * rad;

                sw.spawnParticles(ParticleTypes.FLAME, px, yy, pz, 1, 0.03, 0.04, 0.03, 0.01);
                if ((this.age + layer + k) % 3 == 0) {
                    sw.spawnParticles(ParticleTypes.SMOKE, px, yy, pz, 1, 0.03, 0.04, 0.03, 0.01);
                }
                if ((this.age + layer + k) % 6 == 0) {
                    sw.spawnParticles(ParticleTypes.LAVA, px, yy, pz, 1, 0.02, 0.02, 0.02, 0.01);
                }
            }
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld() instanceof ServerWorld sw) {
            // ✅ fiery sound alongside wind
            if (this.age % 10 == 0) {
                sw.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.AMBIENT,
                        0.75f, 0.85f + this.random.nextFloat() * 0.25f);
            }
            if (this.age % 22 == 0) {
                sw.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.AMBIENT,
                        0.55f, 0.75f + this.random.nextFloat() * 0.20f);
            }

            float r = getEffectiveRadius();
            double h = getEffectiveHeight();

            Box box = new Box(this.getX()-r, this.getY()-1, this.getZ()-r, this.getX()+r, this.getY()+h, this.getZ()+r);
            List<LivingEntity> list = sw.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive());
            for (LivingEntity le : list) {
                if (ownerId != null && ownerId.equals(le.getUuid())) continue;
                le.setOnFireFor(2);
            }

            if (this.age == (DURATION_TICKS - 1)) {
                sw.createExplosion(null, this.getX(), this.getY() + 0.2, this.getZ(), EXPLOSION_POWER, World.ExplosionSourceType.NONE);
            }
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) { super.readCustomDataFromNbt(nbt); }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) { super.writeCustomDataToNbt(nbt); }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return SpawnEntityPacket.create(this);
    }
}