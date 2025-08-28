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

/**
 * Villager Evo base entity. It exposes a public triggerAttackAnimation(int)
 * used by HeadButterBehavior to sync GeckoLib's "attack" clip.
 *
 * NOTE: Species-specific move logic comes from SpeciesGoals/behaviors;
 * this class only provides animations + some locomotion helpers.
 */
public class VillagerEvoEntity extends PathAwareEntity implements GeoEntity {

    // No static EntityType here; registered in ModEntities.register()
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // attack animation state controlled by behaviors (e.g., HeadButterBehavior)
    private int attackAnimTicks = 0;    // remaining ticks of the attack animation
    @SuppressWarnings("FieldCanBeLocal")
    private int attackAnimTotal = 0;    // total duration for the current play

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
        // Only generic look goals here; species moves are injected by SpeciesGoals via TamerAI
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(7, new LookAtEntityGoal(this, ServerPlayerEntity.class, 10.0f));
        this.goalSelector.add(8, new LookAroundGoal(this));
    }

    // GeckoLib animations
    private static final RawAnimation IDLE   = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK   = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().then("attack", Animation.LoopType.PLAY_ONCE);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            if (attackAnimTicks > 0) {
                state.setAndContinue(ATTACK);
            } else {
                boolean moving = this.getVelocity().horizontalLengthSquared() > 1e-4;
                state.setAndContinue(moving ? WALK : IDLE);
            }
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient && attackAnimTicks > 0) {
            attackAnimTicks--;
        }
    }

    /** Called by behaviors to play the "attack" clip for a fixed number of ticks. */
    public void triggerAttackAnimation(int ticks) {
        this.attackAnimTotal = ticks;
        this.attackAnimTicks = ticks;
    }
}