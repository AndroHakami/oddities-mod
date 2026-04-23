
package net.seep.odd.entity.librarian;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.quest.QuestManager;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class LibrarianEntity extends PathAwareEntity implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public LibrarianEntity(EntityType<? extends LibrarianEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 0;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 9999.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 10.0F));
        this.goalSelector.add(9, new LookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isOnGround()) {
            this.setVelocity(Vec3d.ZERO);
        } else {
            this.setVelocity(0.0D, this.getVelocity().y, 0.0D);
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushAway(Entity entity) {
    }

    @Override
    public void takeKnockback(double strength, double x, double z) {
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (player.getWorld().isClient) {
            return ActionResult.SUCCESS;
        }

        QuestManager.openLibrarianScreen(player, this);
        return ActionResult.CONSUME;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "librarian.controller", 0, state -> {
            state.setAndContinue(IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
