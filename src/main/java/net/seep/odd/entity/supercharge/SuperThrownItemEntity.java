package net.seep.odd.entity.supercharge;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import net.seep.odd.abilities.power.SuperChargePower;

import java.util.concurrent.ThreadLocalRandom;

public class SuperThrownItemEntity extends ThrownItemEntity {

    // Note: spin is controlled in the renderer using the entity id as a seed.
    public SuperThrownItemEntity(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
    }

    public SuperThrownItemEntity(World world, LivingEntity owner, ItemStack stack) {
        super(SuperEntities.THROWN_ITEM, owner, world);
        // sync to clients
        this.setItem(stack.copyWithCount(1));
    }

    @Override protected Item getDefaultItem() { return Items.SNOWBALL; }

    @Override
    public void tick() {
        super.tick();

        // Very light WAX_ON trail – server side so it syncs to all clients
        if (!getWorld().isClient) {
            if ((this.age % 1) == 0) {
                var sw = (ServerWorld) getWorld();

                // a couple subtle particles with slight opposite velocity (streak)
                double vx = -getVelocity().x * 0.05;
                double vy = -getVelocity().y * 0.05;
                double vz = -getVelocity().z * 0.05;

                sw.spawnParticles(ParticleTypes.WAX_ON, getX(), getY(), getZ(),
                        1, 0.01, 0.01, 0.01, 0.001);
                // thin line feel
                if ((this.age & 1) == 0) {
                    sw.spawnParticles(ParticleTypes.WAX_ON, getX(), getY(), getZ(),
                            1, vx, vy, vz, 0.0);
                }
            }
        }
    }

    @Override
    protected void onCollision(HitResult hit) {
        super.onCollision(hit);
        if (!getWorld().isClient) {
            ItemStack thrown = this.getStack();
            float power = SuperChargePower.explosionPowerFor(thrown);
            boolean breakBlocks = SuperChargePower.breaksBlocksFor(thrown);
            getWorld().createExplosion(
                    this,
                    getX(), getY(), getZ(),
                    power,
                    breakBlocks ? World.ExplosionSourceType.TNT
                            : World.ExplosionSourceType.NONE
            );
            discard();
        }
    }
}
