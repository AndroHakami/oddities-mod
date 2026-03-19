package net.seep.odd.entity.fatwitch;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public final class FatWitchSigilEntity extends Entity implements GeoEntity {

    private static final TrackedData<Integer> LIFE =
            DataTracker.registerData(FatWitchSigilEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> IGNITED =
            DataTracker.registerData(FatWitchSigilEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> VANISHING =
            DataTracker.registerData(FatWitchSigilEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    public static final int IGNITE_TICKS = Math.round(1.04f * 20f); // 21
    private static final int APPEAR_TICKS = 4;
    private static final int VANISH_TICKS = 7;

    // enough lifetime to survive until ignite, then ignite() resets it to VANISH_TICKS
    public static final int DEFAULT_LIFE_TICKS = IGNITE_TICKS + VANISH_TICKS;

    private static final float EFFECT_RADIUS = 3.0f;
    private static final float DAMAGE = 4.0f;
    private static final int FIRE_SECONDS = 4;

    private UUID owner;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation ANIM_APPEAR = RawAnimation.begin().then("appear", Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation ANIM_IDLE   = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation ANIM_VANISH = RawAnimation.begin().then("vanish", Animation.LoopType.PLAY_ONCE);

    public FatWitchSigilEntity(EntityType<? extends FatWitchSigilEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.ignoreCameraFrustum = true;
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(LIFE, DEFAULT_LIFE_TICKS);
        this.dataTracker.startTracking(IGNITED, false);
        this.dataTracker.startTracking(VANISHING, false);
    }

    public void setOwner(UUID uuid) {
        this.owner = uuid;
    }

    public UUID getOwnerUuid() {
        return this.owner;
    }

    public int getLifetimeTicks() {
        return this.dataTracker.get(LIFE);
    }

    public void setLifetimeTicks(int ticks) {
        this.dataTracker.set(LIFE, Math.max(0, ticks));
    }

    public boolean isIgnited() {
        return this.dataTracker.get(IGNITED);
    }

    private void setIgnited(boolean value) {
        this.dataTracker.set(IGNITED, value);
    }

    public boolean isVanishing() {
        return this.dataTracker.get(VANISHING);
    }

    private void setVanishing(boolean value) {
        this.dataTracker.set(VANISHING, value);
    }

    public float getEffectRadius() {
        return EFFECT_RADIUS;
    }

    public float getAngleDeg(float partialTick) {
        return ((this.age + partialTick) * 18.0f) % 360.0f;
    }

    @Override
    public void tick() {
        super.tick();

        this.setNoGravity(true);
        this.setVelocity(Vec3d.ZERO);

        if (!this.getWorld().isClient) {
            if (!this.isIgnited() && this.age >= IGNITE_TICKS) {
                ignite();
            }

            int life = this.getLifetimeTicks() - 1;
            this.setLifetimeTicks(life);

            if (life <= 0) {
                this.discard();
                return;
            }
        } else {
            clientParticles();
        }
    }

    private void ignite() {
        this.setIgnited(true);
        this.setVanishing(true);
        this.setLifetimeTicks(VANISH_TICKS);

        if (this.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(
                    ParticleTypes.FLAME,
                    this.getX(), this.getY() + 0.02, this.getZ(),
                    48,
                    EFFECT_RADIUS * 0.70, 0.02, EFFECT_RADIUS * 0.70,
                    0.02
            );
            serverWorld.spawnParticles(
                    ParticleTypes.SMOKE,
                    this.getX(), this.getY() + 0.04, this.getZ(),
                    28,
                    EFFECT_RADIUS * 0.65, 0.03, EFFECT_RADIUS * 0.65,
                    0.01
            );
            serverWorld.spawnParticles(
                    ParticleTypes.LAVA,
                    this.getX(), this.getY() + 0.03, this.getZ(),
                    10,
                    EFFECT_RADIUS * 0.50, 0.01, EFFECT_RADIUS * 0.50,
                    0.0
            );
        }

        this.playSound(SoundEvents.ITEM_FIRECHARGE_USE, 0.8f, 1.02f + this.random.nextFloat() * 0.12f);
        damageEntitiesInRadius();
    }

    private void damageEntitiesInRadius() {
        Box area = new Box(
                this.getX() - EFFECT_RADIUS, this.getY() - 0.25, this.getZ() - EFFECT_RADIUS,
                this.getX() + EFFECT_RADIUS, this.getY() + 2.2, this.getZ() + EFFECT_RADIUS
        );

        float radiusSq = EFFECT_RADIUS * EFFECT_RADIUS;

        for (LivingEntity target : this.getWorld().getEntitiesByClass(LivingEntity.class, area, e -> e.isAlive())) {
            if (this.owner != null && this.owner.equals(target.getUuid())) continue;

            double dx = target.getX() - this.getX();
            double dz = target.getZ() - this.getZ();
            if ((dx * dx + dz * dz) > radiusSq) continue;

            target.damage(this.getDamageSources().magic(), DAMAGE);
            target.setOnFireFor(FIRE_SECONDS);
        }
    }

    private void clientParticles() {
        if (!this.isIgnited()) {
            if ((this.age & 1) == 0) {
                for (int i = 0; i < 2; i++) {
                    double a = this.random.nextDouble() * Math.PI * 2.0;
                    double r = 0.45 + this.random.nextDouble() * (EFFECT_RADIUS - 0.25);
                    double x = this.getX() + Math.cos(a) * r;
                    double z = this.getZ() + Math.sin(a) * r;

                    this.getWorld().addParticle(
                            ParticleTypes.SMOKE,
                            x, this.getY() + 0.03, z,
                            0.0, 0.01, 0.0
                    );

                    this.getWorld().addParticle(
                            ParticleTypes.ENCHANT,
                            x, this.getY() + 0.02, z,
                            0.0, 0.01, 0.0
                    );
                }
            }
            return;
        }

        for (int i = 0; i < 5; i++) {
            double a = this.random.nextDouble() * Math.PI * 2.0;
            double r = this.random.nextDouble() * EFFECT_RADIUS;
            double x = this.getX() + Math.cos(a) * r;
            double z = this.getZ() + Math.sin(a) * r;

            this.getWorld().addParticle(
                    ParticleTypes.FLAME,
                    x, this.getY() + 0.04, z,
                    0.0, 0.02, 0.0
            );

            if ((this.age & 1) == 0) {
                this.getWorld().addParticle(
                        ParticleTypes.SMOKE,
                        x, this.getY() + 0.08, z,
                        0.0, 0.015, 0.0
                );
            }
        }
    }

    @Override public boolean collidesWith(Entity other) { return false; }
    @Override public boolean isCollidable()             { return false; }
    @Override public boolean isPushable()               { return false; }
    @Override public boolean shouldSave()               { return false; }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Life", this.getLifetimeTicks());
        nbt.putBoolean("Ignited", this.isIgnited());
        nbt.putBoolean("Vanishing", this.isVanishing());
        if (this.owner != null) nbt.putUuid("Owner", this.owner);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.contains("Life")) this.setLifetimeTicks(nbt.getInt("Life"));
        this.setIgnited(nbt.getBoolean("Ignited"));
        this.setVanishing(nbt.getBoolean("Vanishing"));
        if (nbt.containsUuid("Owner")) this.owner = nbt.getUuid("Owner");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "sigil", 0, state -> {
            if (this.age < APPEAR_TICKS && !this.isVanishing()) {
                state.setAnimation(ANIM_APPEAR);
                return PlayState.CONTINUE;
            }

            if (this.isVanishing()) {
                state.setAnimation(ANIM_VANISH);
                return PlayState.CONTINUE;
            }

            state.setAnimation(ANIM_IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}