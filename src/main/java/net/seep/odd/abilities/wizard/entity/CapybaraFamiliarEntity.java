// FILE: src/main/java/net/seep/odd/abilities/wizard/entity/CapybaraFamiliarEntity.java
package net.seep.odd.abilities.wizard.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.JukeboxBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EntityView;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import net.seep.odd.abilities.power.WizardPower;
import net.seep.odd.abilities.wizard.WizardElement;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public class CapybaraFamiliarEntity extends TameableEntity implements GeoEntity {

    private static final RawAnimation IDLE   = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK   = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation DANCE_T = RawAnimation.begin().thenPlay("transition_to_dance");
    private static final RawAnimation DANCE  = RawAnimation.begin().thenLoop("dancing");
    private static final int DANCE_TRANS_TICKS_MAX = 18;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private UUID ownerId;
    private UUID orbitTargetId;

    private int element = 0; // 0 fire,1 water,2 air,3 earth

    private boolean dancing = false;
    private int danceTransTicks = 0;

    public CapybaraFamiliarEntity(EntityType<? extends TameableEntity> type, World world) {
        super(type, world);
        this.setStepHeight(1.0f);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return TameableEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(2, new FollowOwnerGoal(this, 1.1D, 3.0F, 18.0F, false) {
            @Override public boolean canStart() { return !hasOrbitTarget() && super.canStart(); }
            @Override public boolean shouldContinue() { return !hasOrbitTarget() && super.shouldContinue(); }
        });

        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.add(9, new LookAroundGoal(this));
    }

    @Override public boolean damage(DamageSource source, float amount) { return false; }
    @Override public boolean isInvulnerableTo(DamageSource damageSource) { return true; }

    public boolean hasOrbitTarget() { return orbitTargetId != null; }
    public void clearOrbitTarget() { orbitTargetId = null; }
    public void setOrbitTarget(UUID id) { orbitTargetId = id; }

    public void setOwnerUuid(UUID id) { this.ownerId = id; }
    public UUID getOwnerUuidSafe() { return ownerId; }

    public void setElement(int element) {
        this.element = Math.max(0, Math.min(3, element));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        if (ownerId == null) { this.discard(); return; }
        var owner = sw.getPlayerByUuid(ownerId);
        if (owner == null || owner.isDead()) { this.discard(); return; }

        // HARD SYNC element to owner’s real element (fixes “always fire”)
        WizardElement e = WizardPower.getElement(owner);
        this.element = e.id;

        if ((this.age % 10) == 0) {
            boolean nowDancing = isJukeboxWithRecordNearby(6);
            if (nowDancing && !dancing) danceTransTicks = DANCE_TRANS_TICKS_MAX;
            dancing = nowDancing;
        }
        if (danceTransTicks > 0) danceTransTicks--;

        if (orbitTargetId != null) {
            Entity t = sw.getEntity(orbitTargetId);
            if (t == null) {
                orbitTargetId = null;
            } else {
                double ang = (this.age * 0.18);
                double r = 1.6;
                double ox = Math.cos(ang) * r;
                double oz = Math.sin(ang) * r;

                this.getNavigation().stop();
                this.setVelocity(0, 0, 0);
                this.refreshPositionAndAngles(
                        t.getX() + ox,
                        t.getY() + 0.6,
                        t.getZ() + oz,
                        this.getYaw(),
                        this.getPitch()
                );

                if ((this.age % 20) == 0 && t instanceof net.minecraft.entity.LivingEntity living) {
                    applyElementBuff(living);
                }

                // REDUCED particles: only every 5 ticks and fewer count
                if ((this.age % 5) == 0) {
                    spawnElementParticles(sw, t.getX(), t.getY() + 1.0, t.getZ());
                }
            }
        } else {
            if (this.squaredDistanceTo(owner) > 40 * 40) {
                this.refreshPositionAndAngles(owner.getX(), owner.getY(), owner.getZ(), this.getYaw(), this.getPitch());
            }
        }
    }

    private void applyElementBuff(net.minecraft.entity.LivingEntity target) {
        int dur = 40;
        switch (element) {
            case 0 -> target.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, dur, 1, false, true, true));
            case 1 -> target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, dur, 1, false, true, true));
            case 2 -> {
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, dur, 1, false, true, true));
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, dur, 0, false, true, true));
            }
            case 3 -> target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, dur, 1, false, true, true));
        }
    }

    private void spawnElementParticles(ServerWorld sw, double x, double y, double z) {
        ParticleEffect p = switch (element) {
            case 0 -> ParticleTypes.FLAME;
            case 1 -> ParticleTypes.SPLASH;
            case 2 -> ParticleTypes.CLOUD;
            default -> new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.DIRT.getDefaultState());
        };

        sw.spawnParticles(p, x, y, z, 2, 0.18, 0.18, 0.18, 0.01);
    }

    private boolean isJukeboxWithRecordNearby(int r) {
        BlockPos base = this.getBlockPos();
        for (BlockPos p : BlockPos.iterate(base.add(-r, -r, -r), base.add(r, r, r))) {
            BlockState s = this.getWorld().getBlockState(p);
            if (s.isOf(Blocks.JUKEBOX) && s.contains(JukeboxBlock.HAS_RECORD) && s.get(JukeboxBlock.HAS_RECORD)) return true;
        }
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            if (dancing) {
                if (danceTransTicks > 0) state.setAndContinue(DANCE_T);
                else state.setAndContinue(DANCE);
                return PlayState.CONTINUE;
            }

            boolean moving = state.isMoving() || this.getVelocity().horizontalLengthSquared() > 1.0e-4;
            if (moving) state.setAndContinue(WALK);
            else state.setAndContinue(IDLE);

            return PlayState.CONTINUE;
        }));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public EntityView method_48926() { return this.getWorld(); }

    @Nullable
    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty,
                                 SpawnReason spawnReason, @Nullable EntityData entityData,
                                 @Nullable NbtCompound entityNbt) {
        EntityData data = super.initialize(world, difficulty, spawnReason, entityData, entityNbt);
        this.setTamed(true);
        return data;
    }

    @Override public @Nullable PassiveEntity createChild(ServerWorld world, PassiveEntity entity) { return null; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (ownerId != null) nbt.putUuid("Owner", ownerId);
        if (orbitTargetId != null) nbt.putUuid("Orbit", orbitTargetId);
        nbt.putInt("Element", element);
        nbt.putBoolean("Dancing", dancing);
        nbt.putInt("DanceTrans", danceTransTicks);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("Owner")) ownerId = nbt.getUuid("Owner");
        if (nbt.containsUuid("Orbit")) orbitTargetId = nbt.getUuid("Orbit");
        element = nbt.getInt("Element");
        dancing = nbt.getBoolean("Dancing");
        danceTransTicks = nbt.getInt("DanceTrans");
    }
}
