// src/main/java/net/seep/odd/abilities/conquer/entity/DarkHorseEntity.java
package net.seep.odd.abilities.conquer.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.seep.odd.abilities.power.ConquerPower;

import java.util.UUID;

public class DarkHorseEntity extends HorseEntity {

    public DarkHorseEntity(EntityType<? extends HorseEntity> entityType, World world) {
        super(entityType, world);
        this.setBreedingAge(0);
    }

    public static DefaultAttributeContainer.Builder createDarkHorseAttributes() {
        return AbstractHorseEntity.createBaseHorseAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.35D)
                .add(EntityAttributes.HORSE_JUMP_STRENGTH, 1.15D);
    }

    @Override
    public boolean isBaby() {
        return false;
    }

    /** Saddleless riding: treat as always saddled. */
    @Override
    public boolean isSaddled() {
        return true;
    }

    @Override
    public boolean canBeSaddled() {
        return false;
    }

    /** Only the owner can ride Milo (remove check if you want public riding). */
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        UUID owner = this.getOwnerUuid();

        if (owner != null && !owner.equals(player.getUuid())) {
            if (!player.getWorld().isClient) {
                player.sendMessage(Text.literal("Milo ignores you."), true);
            }
            return ActionResult.SUCCESS;
        }

        if (!player.isSneaking()) {
            if (!this.getWorld().isClient) {
                player.startRiding(this);
            }
            return ActionResult.SUCCESS;
        }

        return super.interactMob(player, hand);
    }

    /** If Milo is killed, apply 120s cooldown and wipe stored snapshot. */
    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);

        if (this.getWorld().isClient) return;
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        UUID owner = this.getOwnerUuid();
        if (owner != null) {
            ConquerPower.onMiloKilled(owner, sw);
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.getWorld().isClient) return;

        for (int i = 0; i < 2; i++) {
            double ox = (this.random.nextDouble() - 0.5D) * 0.8D;
            double oy = this.random.nextDouble() * 0.6D + 0.3D;
            double oz = (this.random.nextDouble() - 0.5D) * 0.8D;

            this.getWorld().addParticle(ParticleTypes.SNOWFLAKE,
                    this.getX() + ox, this.getY() + oy, this.getZ() + oz,
                    0.0D, 0.02D, 0.0D);

            this.getWorld().addParticle(ParticleTypes.SCULK_SOUL,
                    this.getX() + ox, this.getY() + oy, this.getZ() + oz,
                    0.0D, 0.01D, 0.0D);
        }
    }
}
