// src/main/java/net/seep/odd/abilities/fallingsnow/HealingSnowballEntity.java
package net.seep.odd.abilities.fallingsnow;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.seep.odd.entity.ModEntities;

import java.util.List;

public class HealingSnowballEntity extends ThrownItemEntity {
    public static final Item BALL_ITEM = Items.SNOWBALL;

    private final float heal;

    public HealingSnowballEntity(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
        this.heal = 4f;
    }

    public HealingSnowballEntity(World world, LivingEntity owner, float heal) {
        super(ModEntities.HEALING_SNOWBALL, owner, world);
        this.heal = heal;
        this.setItem(BALL_ITEM.getDefaultStack());
    }

    @Override protected Item getDefaultItem() { return BALL_ITEM; }

    private boolean isAlly(LivingEntity self, LivingEntity other) {
        if (self == null || other == null) return false;
        return other == self || other.isTeammate(self);
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        super.onEntityHit(hit);
        if (this.getWorld().isClient) return;
        Entity e = hit.getEntity();
        LivingEntity owner = (LivingEntity) getOwner();
        if (e instanceof LivingEntity le && owner != null && isAlly(owner, le)) {
            le.heal(heal);
            this.getWorld().playSound(null, le.getBlockPos(), SoundEvents.BLOCK_POWDER_SNOW_BREAK, SoundCategory.PLAYERS, 0.8f, 1.6f);
        }
        this.discard();
    }

    @Override
    protected void onCollision(HitResult hit) {
        super.onCollision(hit);
        if (this.getWorld().isClient) return;

        // tiny AoE heal for allies where it lands (radius ~2)
        LivingEntity owner = (LivingEntity) getOwner();
        if (owner != null) {
            Box aabb = this.getBoundingBox().expand(2.0);
            List<LivingEntity> list = this.getWorld().getEntitiesByClass(LivingEntity.class, aabb,
                    le -> le.isAlive() && isAlly(owner, le));
            for (LivingEntity le : list) le.heal(heal * 0.75f);
        }

        ((net.minecraft.server.world.ServerWorld) this.getWorld())
                .spawnParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY(), this.getZ(), 10, 0.25,0.25,0.25, 0.01);
        this.getWorld().playSound(null, getBlockPos(), SoundEvents.BLOCK_POWDER_SNOW_PLACE, SoundCategory.PLAYERS, 0.8f, 1.5f);
        this.discard();
    }
}
