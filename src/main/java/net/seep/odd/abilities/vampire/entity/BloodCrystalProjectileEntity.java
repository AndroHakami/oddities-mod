package net.seep.odd.abilities.vampire.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import net.seep.odd.abilities.vampire.VampireTempCrystalManager;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.item.ModItems;

public class BloodCrystalProjectileEntity extends ThrownItemEntity {

    private static final float DAMAGE = 6.0f; // 3 hearts

    public BloodCrystalProjectileEntity(EntityType<? extends BloodCrystalProjectileEntity> type, World world) {
        super(type, world);
    }

    public BloodCrystalProjectileEntity(World world, LivingEntity owner) {
        // ✅ Use YOUR registered entity type (NOT snowball)
        super(ModEntities.BLOOD_CRYSTAL_PROJECTILE, owner, world);
    }


    @Override
    protected float getGravity() {
        return 0.0f;
    }

    @Override
    public void tick() {
        super.tick();

        if (getWorld().isClient) {
            getWorld().addParticle(ParticleTypes.CRIMSON_SPORE, true, getX(), getY(), getZ(), 0, 0, 0);
        } else if ((age & 1) == 0 && getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.CRIMSON_SPORE, getX(), getY(), getZ(), 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        super.onEntityHit(hit);
        if (getWorld().isClient) return;

        Entity e = hit.getEntity();
        Entity owner = getOwner();

        if (e instanceof LivingEntity le && le.isAlive()) {
            if (owner instanceof LivingEntity ol) {
                le.damage(getWorld().getDamageSources().playerAttack((ol instanceof ServerPlayerEntity sp) ? sp : null), DAMAGE);
            } else {
                le.damage(getWorld().getDamageSources().thrown(this, owner), DAMAGE);
            }

            // speed V for 0.5s to vampire ONLY
            if (owner instanceof ServerPlayerEntity sp) {
                sp.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.SPEED, 20, 9, false, false, true));
            }

            if (getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, getX(), getY() + 0.1, getZ(), 10, 0.18, 0.10, 0.18, 0.0);
                sw.spawnParticles(ParticleTypes.CRIMSON_SPORE, getX(), getY() + 0.1, getZ(), 16, 0.20, 0.12, 0.20, 0.0);
                sw.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.PLAYERS, 0.8f, 0.7f);
            }
        }

        discard();
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (getWorld().isClient) return;

        if (hitResult.getType() == HitResult.Type.BLOCK && getWorld() instanceof ServerWorld sw) {
            BlockHitResult bhr = (BlockHitResult) hitResult;

            // ✅ IMPORTANT: use the hit face directly (tip points toward shooter)
            Direction face = bhr.getSide();
            VampireTempCrystalManager.spawnSpike(sw, bhr.getBlockPos(), face);

            sw.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.BLOCK_AMETHYST_BLOCK_PLACE, SoundCategory.PLAYERS,
                    0.9f, 0.7f);
        }

        discard();
    }
    @Override
    protected Item getDefaultItem() {
        return ModItems.BLOOD_CRYSTAL_PROJECTILE;
    }
}
