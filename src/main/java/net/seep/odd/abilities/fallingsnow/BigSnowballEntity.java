// src/main/java/net/seep/odd/abilities/fallingsnow/BigSnowballEntity.java
package net.seep.odd.abilities.fallingsnow;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import net.seep.odd.entity.ModEntities;

public class BigSnowballEntity extends ThrownItemEntity {
    private final float damage;
    private final int slowTicks;
    private final int slowLevel;
    private final double knockback;

    public BigSnowballEntity(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
        this.damage = 8f; this.slowTicks = 80; this.slowLevel = 0; this.knockback = 1.0;
    }

    public BigSnowballEntity(World world, LivingEntity owner, float damage, int slowTicks, int slowLevel, double knockback) {
        super(ModEntities.BIG_SNOWBALL, owner, world);
        this.damage = damage;
        this.slowTicks = slowTicks;
        this.slowLevel = slowLevel;
        this.knockback = knockback;
    }

    @Override protected Item getDefaultItem() { return Items.SNOW_BLOCK; } // render as a block item

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        super.onEntityHit(hit);
        Entity e = hit.getEntity();
        if (!this.getWorld().isClient && e instanceof LivingEntity le) {
            var src = this.getWorld().getDamageSources().thrown(this, this.getOwner());
            le.damage(src, damage); // armor-respecting
            le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, slowTicks, slowLevel));
            var dir = le.getPos().subtract(this.getPos()).normalize();
            le.addVelocity(dir.x * knockback, 0.15, dir.z * knockback);
            le.velocityModified = true;
        }
        this.getWorld().playSound(null, getBlockPos(), SoundEvents.BLOCK_SNOW_HIT, SoundCategory.PLAYERS, 1f, 0.9f);
        this.discard();
    }

    @Override
    protected void onCollision(HitResult hit) {
        super.onCollision(hit);
        if (!this.getWorld().isClient) {
            this.getWorld().playSound(null, getBlockPos(), SoundEvents.BLOCK_SNOW_HIT, SoundCategory.PLAYERS, 0.9f, 1.0f);
            this.discard();
        }
    }
}
