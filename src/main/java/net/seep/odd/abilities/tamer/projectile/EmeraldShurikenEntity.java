// net/seep/odd/abilities/tamer/projectile/EmeraldShurikenEntity.java
package net.seep.odd.abilities.tamer.projectile;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.particle.ParticleTypes;

import net.seep.odd.entity.ModEntities;
import net.seep.odd.item.ModItems;

/**
 * Light, fast shuriken projectile. Damage scales mildly with velocity.
 * Rendered with a flat item sprite (use a custom item if you want a unique icon).
 */
public class EmeraldShurikenEntity extends ThrownItemEntity {
    public static final float BASE_DAMAGE = 3.0f;

    public EmeraldShurikenEntity(EntityType<? extends EmeraldShurikenEntity> type, World world) {
        super(type, world);
        if (!world.isClient) {
            // Brighter: glowing outline so it reads better in dark areas
            this.setGlowing(true);
        }
    }

    public EmeraldShurikenEntity(World world, LivingEntity owner) {
        super(ModEntities.EMERALD_SHURIKEN, owner, world);
        if (!world.isClient) {
            this.setGlowing(true);
        }
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.EMERALD_SHURIKEN;
    }

    // Tiny trail for visibility
    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient) {
            ServerWorld sw = (ServerWorld) this.getWorld();

            // Small, frequent particles; offset is tiny so it hugs the flight path
            double off = 0.02;
            sw.spawnParticles(ParticleTypes.GLOW, this.getX(), this.getY(), this.getZ(),
                    1, off, off, off, 0.0);
            sw.spawnParticles(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(),
                    1, off, off, off, 0.0);
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        super.onEntityHit(hit);
        if (!(hit.getEntity() instanceof LivingEntity le)) {
            discard();
            return;
        }
        float v = (float) this.getVelocity().length();   // ~0..3ish typically
        float dmg = BASE_DAMAGE + v * 2.0f;
        var src = (getOwner() instanceof LivingEntity o)
                ? this.getDamageSources().mobProjectile(this, o)
                : this.getDamageSources().thrown(this, getOwner());

        le.damage(src, dmg);

        // tiny directional knock
        le.takeKnockback(0.2, -this.getVelocity().x, -this.getVelocity().z);

        // a little burst on hit
        if (!this.getWorld().isClient) {
            ServerWorld sw = (ServerWorld) this.getWorld();
            sw.spawnParticles(ParticleTypes.GLOW, this.getX(), this.getY(), this.getZ(),
                    6, 0.1, 0.1, 0.1, 0.0);
            sw.spawnParticles(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(),
                    6, 0.1, 0.1, 0.1, 0.0);
        }

        this.discard();
    }

    @Override
    protected void onBlockHit(BlockHitResult hit) {
        super.onBlockHit(hit);
        if (!this.getWorld().isClient) {
            // impact hint + little puff
            ServerWorld sw = (ServerWorld) this.getWorld();
            sw.spawnParticles(ParticleTypes.GLOW, this.getX(), this.getY(), this.getZ(),
                    4, 0.08, 0.08, 0.08, 0.0);
            sw.spawnParticles(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(),
                    4, 0.08, 0.08, 0.08, 0.0);

            this.getWorld().sendEntityStatus(this, (byte)3); // vanilla impact status
            this.discard();
        }
    }

    @Override
    protected float getGravity() {
        // Flatter arc than a snowball so it feels "shuriken-y"
        return 0.01f;
    }
}
