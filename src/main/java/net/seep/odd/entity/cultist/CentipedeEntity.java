package net.seep.odd.entity.cultist;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;

import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;

import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.SpiderNavigation;

import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;

import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;

import net.minecraft.nbt.NbtCompound;

import net.minecraft.server.network.ServerPlayerEntity;

import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import net.minecraft.world.World;

import net.seep.odd.status.ModStatusEffects;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class CentipedeEntity extends PathAwareEntity implements GeoEntity {

    /* ---------- GeckoLib ---------- */
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle"); // used for moving too

    /* ---------- Climbing (spider-style) ---------- */
    private static final TrackedData<Byte> CLIMBING_FLAGS =
            DataTracker.registerData(CentipedeEntity.class, TrackedDataHandlerRegistry.BYTE);

    private static final byte CLIMBING_BIT = 0x01;

    /* ---------- Tuning ---------- */
    private static final float HIT_DAMAGE = 2.0F; // 1 heart
    private static final int WITHER_TICKS = 60;   // 3s
    private static final int POISON_TICKS = 60;   // 3s
    private static final int GLOW_TICKS   = 140;  // 7s

    public CentipedeEntity(EntityType<? extends CentipedeEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 2;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 2.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.30D) // normal-ish
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 0.0D)   // we apply our own damage
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(CLIMBING_FLAGS, (byte) 0);
    }

    /* ✅ REQUIRED: SpiderNavigation so it can path UP walls */
    @Override
    protected EntityNavigation createNavigation(World world) {
        return new SpiderNavigation(this, world);
    }

    /* ✅ REQUIRED: this is what movement checks for spider-climbing */
    @Override
    public boolean isClimbing() {
        return isClimbingWall();
    }

    private boolean isClimbingWall() {
        return (this.dataTracker.get(CLIMBING_FLAGS) & CLIMBING_BIT) != 0;
    }

    private void setClimbingWall(boolean climbing) {
        byte b = this.dataTracker.get(CLIMBING_FLAGS);
        if (climbing) b |= CLIMBING_BIT;
        else b &= ~CLIMBING_BIT;
        this.dataTracker.set(CLIMBING_FLAGS, b);
    }

    /* ---------- Goals ---------- */
    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.15, true));

        this.targetSelector.add(1, new ActiveTargetGoal<>(
                this,
                PlayerEntity.class,
                true,
                living -> (living instanceof PlayerEntity p) && isValidVictim(p)
        ));

        this.goalSelector.add(3, new WanderAroundFarGoal(this, 0.9));
        this.goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 10f));
        this.goalSelector.add(8, new LookAroundGoal(this));
    }

    /* ---------- Climbing flag updates + optional tilt ---------- */
    @Override
    public void tickMovement() {
        super.tickMovement();

        if (!this.getWorld().isClient) {
            // same logic spiders use: if you're pushing into a wall, you're "climbing"
            setClimbingWall(this.horizontalCollision);
        }

        // Optional visual pitch tilt when climbing (helps it look like it's going up)
        if (this.isClimbing()) {
            double vy = this.getVelocity().y;
            if (vy > 0.02) this.setPitch(-35f);
            else if (vy < -0.02) this.setPitch(35f);
            else this.setPitch(0f);
        } else {
            this.setPitch(0f);
        }
    }

    /* ---------- Attack: wither+poison+glow, but never against cultist/protected ---------- */
    @Override
    public boolean tryAttack(Entity target) {
        if (!(target instanceof LivingEntity living)) return false;

        if (target instanceof PlayerEntity p && !isValidVictim(p)) {
            return false;
        }

        boolean hit = living.damage(this.getDamageSources().mobAttack(this), HIT_DAMAGE);
        if (!hit) return false;

        living.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, WITHER_TICKS, 0, true, false, true));
        living.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, POISON_TICKS, 0, true, false, true));
        living.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, GLOW_TICKS, 0, true, false, true));

        this.getWorld().playSound(null, this.getBlockPos(),
                SoundEvents.ENTITY_SILVERFISH_HURT, SoundCategory.HOSTILE, 0.8f, 0.5f);

        return true;
    }

    private boolean isValidVictim(PlayerEntity p) {
        if (!(p instanceof ServerPlayerEntity sp)) return false;
        if (!sp.isAlive() || sp.isSpectator()) return false;
        if (sp.getAbilities().creativeMode) return false;
        if (sp.isInvisible()) return false;

        if (isCultist(sp)) return false;
        return !sp.hasStatusEffect(ModStatusEffects.DIVINE_PROTECTION);
    }

    private static boolean isCultist(PlayerEntity p) {
        if (!(p instanceof ServerPlayerEntity sp)) return false;
        String current = net.seep.odd.abilities.PowerAPI.get(sp);
        return "cultist".equals(current);
    }

    /* ---------- Don’t retaliate to protected/cultist attackers ---------- */
    @Override
    public boolean damage(net.minecraft.entity.damage.DamageSource source, float amount) {
        boolean ok = super.damage(source, amount);

        Entity attacker = source.getAttacker();
        if (attacker instanceof PlayerEntity pe) {
            boolean protectedOrCultist =
                    (pe instanceof ServerPlayerEntity sp)
                            && (isCultist(sp) || sp.hasStatusEffect(ModStatusEffects.DIVINE_PROTECTION));

            if (protectedOrCultist) {
                this.setTarget(null);
                this.setAttacker(null);
            }
        }
        return ok;
    }

    /* ---------- GeckoLib ---------- */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "centipede.controller", 0, state -> {
            state.setAndContinue(IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /* ---------- NBT ---------- */
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putByte("ClimbingFlags", this.dataTracker.get(CLIMBING_FLAGS));
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("ClimbingFlags")) {
            this.dataTracker.set(CLIMBING_FLAGS, nbt.getByte("ClimbingFlags"));
        }
    }
}
