// src/main/java/net/seep/odd/abilities/fallingsnow/HealingSnowballEntity.java
package net.seep.odd.abilities.fallingsnow;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
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
        this.setItem(BALL_ITEM.getDefaultStack());
    }

    public HealingSnowballEntity(World world, LivingEntity owner, float heal) {
        super(ModEntities.HEALING_SNOWBALL, owner, world);
        this.heal = heal;
        this.setItem(BALL_ITEM.getDefaultStack());
    }

    @Override
    protected Item getDefaultItem() {
        return BALL_ITEM;
    }

    private void healTarget(ServerWorld sw, LivingEntity target, float amount) {
        if (amount <= 0f || target == null || !target.isAlive()) return;

        float before = target.getHealth();
        target.heal(amount);

        // Feedback only if health actually increased
        if (target.getHealth() > before + 1.0e-4f) {
            sw.spawnParticles(ParticleTypes.HEART,
                    target.getX(), target.getBodyY(0.7), target.getZ(),
                    6, 0.25, 0.25, 0.25, 0.02);
        }

        sw.playSound(null, target.getBlockPos(),
                SoundEvents.BLOCK_POWDER_SNOW_BREAK, SoundCategory.PLAYERS,
                0.8f, 1.6f);
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        // Don't call super -> avoids any vanilla "snowball hit" behavior
        if (this.getWorld().isClient) return;

        Entity e = hit.getEntity();
        if (e instanceof LivingEntity target && this.getWorld() instanceof ServerWorld sw) {
            healTarget(sw, target, this.heal);
        }

        this.discard();
    }

    @Override
    protected void onCollision(HitResult hit) {
        // If we collided with an entity, let onEntityHit handle it (avoid double logic)
        if (hit.getType() == HitResult.Type.ENTITY) {
            if (hit instanceof EntityHitResult ehr) onEntityHit(ehr);
            return;
        }

        if (this.getWorld().isClient) return;

        if (!(this.getWorld() instanceof ServerWorld sw)) {
            this.discard();
            return;
        }

        // Small AoE heal where it lands (radius ~2)
        Box aabb = this.getBoundingBox().expand(2.0);
        List<LivingEntity> list = this.getWorld().getEntitiesByClass(
                LivingEntity.class, aabb,
                le -> le.isAlive()
        );

        for (LivingEntity le : list) {
            healTarget(sw, le, this.heal * 0.75f);
        }

        sw.spawnParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY(), this.getZ(),
                10, 0.25, 0.25, 0.25, 0.01);

        sw.playSound(null, this.getBlockPos(),
                SoundEvents.BLOCK_POWDER_SNOW_PLACE, SoundCategory.PLAYERS,
                0.8f, 1.5f);

        this.discard();
    }
}
