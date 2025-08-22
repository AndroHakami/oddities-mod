package net.seep.odd.abilities.tamer.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.world.World;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class VillagerEvo1Entity extends PathAwareEntity implements GeoEntity {
    public static final EntityType<VillagerEvo1Entity> TYPE = FabricEntityTypeBuilder
            .create(SpawnGroup.MISC, VillagerEvo1Entity::new)
            .dimensions(EntityDimensions.fixed(0.7f, 2.0f)) // a bit bigger
            .trackRangeBlocks(64)
            .trackedUpdateRate(1)
            .build();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public VillagerEvo1Entity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 0;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 50.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.27)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 10.0)
                .add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 2.2) // heavy knock
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        // Slow but heavy melee
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.05, true));
        this.goalSelector.add(7, new LookAtEntityGoal(this, net.minecraft.server.network.ServerPlayerEntity.class, 10.0f));
        this.goalSelector.add(8, new LookAroundGoal(this));
        // TamerAI will set target/follow protection goals after spawn/evolution
    }

    // GeckoLib animator: idle/walk from your BB model
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

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
