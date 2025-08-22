// net/seep/odd/abilities/tamer/entity/VillagerEvoEntity.java
package net.seep.odd.abilities.tamer.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class VillagerEvoEntity extends PathAwareEntity implements GeoEntity {

    // ✅ No static EntityType here. It is registered in ModEntities.register().
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public VillagerEvoEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 0;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 8.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        // Slow, heavy melee; TamerAI will handle owner-protect/targets
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.05, true));
        this.goalSelector.add(7, new LookAtEntityGoal(this, ServerPlayerEntity.class, 10.0f));
        this.goalSelector.add(8, new LookAroundGoal(this));
    }

    // GeckoLib animations
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            boolean moving = this.getVelocity().horizontalLengthSquared() > 1e-4;
            state.setAndContinue(moving ? WALK : IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
