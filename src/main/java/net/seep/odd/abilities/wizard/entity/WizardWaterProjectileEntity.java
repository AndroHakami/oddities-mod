package net.seep.odd.abilities.wizard.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

public class WizardWaterProjectileEntity extends ThrownItemEntity {

    public static int SPEED_DURATION_TICKS = 20 * 30;
    public static int SPEED_LEVEL = 1;

    public WizardWaterProjectileEntity(EntityType<? extends WizardWaterProjectileEntity> type, World world) {
        super(type, world);
    }

    public WizardWaterProjectileEntity(EntityType<? extends WizardWaterProjectileEntity> type, LivingEntity owner, World world) {
        super(type, owner, world);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.PRISMARINE_CRYSTALS;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld() instanceof ServerWorld sw && (this.age % 2) == 0) {
            sw.spawnParticles(ParticleTypes.SPLASH, this.getX(), this.getY(), this.getZ(),
                    2, 0.06, 0.06, 0.06, 0.0);
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult ehr) {
        super.onEntityHit(ehr);
        Entity hit = ehr.getEntity();
        if (hit instanceof LivingEntity living) {
            living.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, SPEED_DURATION_TICKS, SPEED_LEVEL, false, true, true));
            living.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 1, 2, false, false, false));
        }
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);

        if (this.getWorld() instanceof ServerWorld sw) {
            // ✅ impact sound when it lands
            sw.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ENTITY_SPLASH_POTION_BREAK, SoundCategory.PLAYERS, 0.85f, 1.25f);
            sw.spawnParticles(ParticleTypes.SPLASH, this.getX(), this.getY(), this.getZ(),
                    14, 0.18, 0.10, 0.18, 0.02);
        }

        if (!this.getWorld().isClient) this.discard();
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}