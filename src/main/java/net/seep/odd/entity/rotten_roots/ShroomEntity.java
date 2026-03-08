// FILE: src/main/java/net/seep/odd/entity/rotten_roots/ShroomEntity.java
package net.seep.odd.entity.rotten_roots;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.village.TradeOffer;
import net.minecraft.world.World;
import net.seep.odd.Oddities;
import org.jetbrains.annotations.Nullable;

public final class ShroomEntity extends AbstractShroomMerchantEntity {

    public ShroomEntity(EntityType<? extends ShroomEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28D);
    }

    @Override
    protected Identifier tradeProfileId() {
        return new Identifier(Oddities.MOD_ID, "shroom");
    }

    @Override
    protected double wanderSpeed() {
        return 0.75; // normal shroom wander
    }

    @Override
    protected void afterUsing(TradeOffer offer) {

    }

    @Override
    public @Nullable PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
        return null;
    }
}