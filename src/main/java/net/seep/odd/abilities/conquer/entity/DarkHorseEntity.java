package net.seep.odd.abilities.conquer.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.HorseEntity;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.world.World;

public class DarkHorseEntity extends HorseEntity {

    public DarkHorseEntity(EntityType<? extends HorseEntity> entityType, World world) {
        super(entityType, world);
    }

    // Attributes used by FabricDefaultAttributeRegistry.register(...)
    public static DefaultAttributeContainer.Builder createDarkHorseAttributes() {
        return AbstractHorseEntity.createBaseHorseAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.35D)
                .add(EntityAttributes.HORSE_JUMP_STRENGTH, 1.15D);
    }

    @Override
    public void tick() {
        super.tick();

        // Client-side particles so everyone sees them (including first-person)
        if (!this.getWorld().isClient) return;

        // Subtle but constant snowflake + sculk vibe
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
