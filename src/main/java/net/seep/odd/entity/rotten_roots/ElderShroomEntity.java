// FILE: src/main/java/net/seep/odd/entity/rotten_roots/ElderShroomEntity.java
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

public final class ElderShroomEntity extends AbstractShroomMerchantEntity {

    public ElderShroomEntity(EntityType<? extends ElderShroomEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 34.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.20D); // slower overall
    }

    @Override
    protected Identifier tradeProfileId() {
        return new Identifier(Oddities.MOD_ID, "elder_shroom");
    }

    @Override
    protected double wanderSpeed() {
        return 0.65; // slower wander AI too
    }

    @Override
    protected void afterUsing(TradeOffer offer) {

    }

    @Override
    public @Nullable PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
        return null;
    }
}