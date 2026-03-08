// FILE: src/main/java/net/seep/odd/entity/rotten_roots/AbstractShroomMerchantEntity.java
package net.seep.odd.entity.rotten_roots;

import net.minecraft.entity.ai.goal.EscapeDangerGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.village.TradeOfferList;
import net.minecraft.world.World;
import net.seep.odd.Oddities;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base class: GeckoLib + MerchantEntity (opens trade UI like a villager/trader).
 * Trades are loaded from datapacks via ShroomTrades (JSON).
 */
public abstract class AbstractShroomMerchantEntity extends MerchantEntity implements GeoEntity {

    /* ---------- GeckoLib ---------- */
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");

    /* ---------- Trades cache / datapack reload support ---------- */
    private int lastTradesVersion = -1;

    protected AbstractShroomMerchantEntity(net.minecraft.entity.EntityType<? extends MerchantEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 3;
    }

    /** Which trade profile this merchant uses (e.g. odd:shroom, odd:elder_shroom). */
    protected abstract Identifier tradeProfileId();

    /** Goal wander speed (elder is slower). */
    protected abstract double wanderSpeed();

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new EscapeDangerGoal(this, 1.15));
        this.goalSelector.add(5, new WanderAroundFarGoal(this, wanderSpeed()));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 10f));
        this.goalSelector.add(9, new LookAroundGoal(this));
    }

    /* ---------- Trading UI ---------- */

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (this.getWorld().isClient) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;

        // build trades (or rebuild if datapack reloaded)
        this.fillRecipes();

        this.setCustomer(sp);
        // 1 = “level” shown in UI; we don't use levels, but it must be >= 1
        this.sendOffers(sp, this.getDisplayName(), 1);

        return ActionResult.CONSUME;
    }

    @Override
    protected void fillRecipes() {
        ensureTradesUpToDate();
    }

    private void ensureTradesUpToDate() {
        int v = ShroomTrades.version();
        TradeOfferList offers = this.getOffers();

        if (offers == null) return;

        // Only (re)build if empty OR datapack reload happened.
        if (offers.isEmpty() || lastTradesVersion != v) {
            offers.clear();
            offers.addAll(ShroomTrades.buildOffers(tradeProfileId(), this.random));
            lastTradesVersion = v;
        }
    }

    @Override
    public boolean isLeveledMerchant() {
        return false;
    }

    @Override
    public int getExperience() {
        return 0;
    }

    @Override
    public SoundEvent getYesSound() {
        return SoundEvents.ENTITY_VILLAGER_YES;
    }

    @Override
    public Text getDisplayName() {
        // uses entity name / lang key as usual
        return super.getDisplayName();
    }

    @Override
    @Nullable
    public PlayerEntity getCustomer() {
        return super.getCustomer();
    }

    /* ---------- GeckoLib controller ---------- */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "shroom.controller", 0, state -> {
            if (state.isMoving()) {
                state.setAndContinue(WALK);
            } else {
                state.setAndContinue(IDLE);
            }
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}