// FILE: src/main/java/net/seep/odd/abilities/wizard/entity/WizardFireProjectileEntity.java
package net.seep.odd.abilities.wizard.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

public class WizardFireProjectileEntity extends ThrownItemEntity {

    public static float DAMAGE = 5.0f;  // 2 hearts
    public static int FIRE_TICKS = 80;  // 4 seconds

    public WizardFireProjectileEntity(EntityType<? extends WizardFireProjectileEntity> type, World world) {
        super(type, world);
    }

    public WizardFireProjectileEntity(EntityType<? extends WizardFireProjectileEntity> type, LivingEntity owner, World world) {
        super(type, owner, world);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.FIRE_CHARGE;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld() instanceof ServerWorld sw) {
            if ((this.age % 2) == 0) {
                sw.spawnParticles(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(),
                        2, 0.03, 0.03, 0.03, 0.0);
            }
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult ehr) {
        super.onEntityHit(ehr);

        Entity hit = ehr.getEntity();
        Entity owner = this.getOwner();

        DamageSource src = this.getWorld().getDamageSources().thrown(this, owner instanceof LivingEntity le ? le : null);
        if (hit instanceof LivingEntity living) {
            living.damage(src, DAMAGE);
            living.setOnFireFor(FIRE_TICKS / 20);
        }
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (!this.getWorld().isClient) this.discard();
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}
