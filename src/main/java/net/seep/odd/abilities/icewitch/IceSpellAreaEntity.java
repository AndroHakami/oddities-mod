// FILE: net/seep/odd/abilities/icewitch/IceSpellAreaEntity.java
package net.seep.odd.abilities.icewitch;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.particles.OddParticles;

// geckolib v4
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public class IceSpellAreaEntity extends Entity implements GeoEntity {
    /* ===== vanilla tracked data ===== */
    private static final TrackedData<Float>   RADIUS = DataTracker.registerData(IceSpellAreaEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> LIFE   = DataTracker.registerData(IceSpellAreaEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private UUID owner; // nullable

    /* ===== client-only helpers (not tracked) ===== */
    private int initialLifeClient = 0;          // set first client tick, used for any fade math elsewhere

    // EXACT animation gate timings (MC runs at 20 ticks/sec)
    private static final int APPEAR_TICKS = Math.round(0.24f * 20f); // 0.24s -> 5 ticks
    private static final int VANISH_TICKS = Math.round(0.34f * 20f); // 0.34s -> 7 ticks

    public IceSpellAreaEntity(EntityType<? extends IceSpellAreaEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.ignoreCameraFrustum = true;
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(RADIUS, 3.0f);
        this.dataTracker.startTracking(LIFE,   200);
    }

    /* ===== public API ===== */
    public void  setRadius(float r)        { this.dataTracker.set(RADIUS, Math.max(0.5f, r)); }
    public float getRadius()               { return this.dataTracker.get(RADIUS); }
    public void  setLifetimeTicks(int t)   { this.dataTracker.set(LIFE, Math.max(1, t)); }
    public int   getLifetimeTicks()        { return this.dataTracker.get(LIFE); }
    public void  setOwner(UUID u)          { this.owner = u; }
    public UUID  getOwnerUuid()            { return this.owner; }
    public float getAngleDeg(float dt)     { return ((this.age + dt) * 6f) % 360f; }

    /* ======================== MAIN TICK ======================== */
    @Override
    public void tick() {
        super.tick();
        this.setNoGravity(true);
        this.setVelocity(Vec3d.ZERO);

        if (!this.getWorld().isClient) {
            // life & server effects
            int life = getLifetimeTicks();
            if (life <= 0) { this.discard(); return; }
            this.dataTracker.set(LIFE, life - 1);

            // damage / freeze pulse
            if ((this.age % 5) == 0) {
                float r = getRadius();
                Box area = new Box(getX()-r, getY()-0.5, getZ()-r, getX()+r, getY()+0.5, getZ()+r);
                for (LivingEntity le : ((ServerWorld)this.getWorld()).getEntitiesByClass(LivingEntity.class, area, e -> e.isAlive())) {
                    if (owner != null && owner.equals(le.getUuid())) continue;
                    if (le.distanceTo(this) <= r + le.getWidth() * 0.5f) {
                        le.damage(getDamageSources().magic(), 1.5f);
                        le.setFrozenTicks(Math.min(600, le.getFrozenTicks() + 25));
                        le.slowMovement(this.getWorld().getBlockState(le.getBlockPos()), new Vec3d(0.75, 1.0, 0.75));
                        le.setSprinting(false);
                    }
                }
            }
        } else {
            // client: cache initial life for any renderer fade math
            if (initialLifeClient == 0) initialLifeClient = Math.max(1, getLifetimeTicks());

            // --------- client particles: stormy spiral rising from the circle (throttled) ----------
            if ((this.age & 1) == 0) { // half the previous load
                final double R   = getRadius();
                float partial = 0f;
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null) partial = mc.getLastFrameDuration();

                final double t  = (this.age + partial);
                final double spin = t * 0.25;
                final double breath = 0.12 * Math.sin(t * 0.12);

                final int arms      = 3;        // reduced from 4
                final int steps     = 14;       // reduced from 20
                final double turns  = 2.0;
                final double height = 2.2;

                final double upVelBase   = 0.10;
                final double swirlSpeed  = 0.06;
                final double jitterRad   = 0.05;

                for (int arm = 0; arm < arms; arm++) {
                    double armPhase = arm * (Math.PI * 2.0 / arms);
                    for (int i = 0; i < steps; i++) {
                        double s = (i + 0.25 * arm) / (double) steps;
                        double ang = armPhase + spin + s * turns * (Math.PI * 2.0);
                        double rad = R * (0.90 + breath);

                        double x = getX() + Math.cos(ang) * rad;
                        double z = getZ() + Math.sin(ang) * rad;
                        double y = getY() + 0.04 + s * height;

                        double tx = -Math.sin(ang);
                        double tz =  Math.cos(ang);

                        double jx = (random.nextDouble() - 0.5) * jitterRad;
                        double jz = (random.nextDouble() - 0.5) * jitterRad;

                        this.getWorld().addParticle(
                                OddParticles.ICE_FLAKE,
                                x, y, z,
                                tx * swirlSpeed + jx,
                                upVelBase,
                                tz * swirlSpeed + jz
                        );

                        if (((this.age + i + arm) % 11) == 0) {
                            this.getWorld().addParticle(
                                    ParticleTypes.SNOWFLAKE,
                                    x, y, z,
                                    tx * (swirlSpeed * 0.6),
                                    upVelBase * 0.85,
                                    tz * (swirlSpeed * 0.6)
                            );
                        }
                    }
                }
            }
        }
    }

    /* ---- collision & save flags ---- */
    @Override public boolean collidesWith(Entity other) { return false; }
    @Override public boolean isCollidable()             { return false; }
    @Override public boolean isPushable()               { return false; }
    @Override public boolean shouldSave()               { return false; }

    /* ---- NBT ---- */
    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (owner != null) nbt.putUuid("Owner", owner);
        nbt.putFloat("Radius", getRadius());
        nbt.putInt("Life", getLifetimeTicks());
    }
    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) this.owner = nbt.getUuid("Owner");
        if (nbt.contains("Radius"))    setRadius(nbt.getFloat("Radius"));
        if (nbt.contains("Life"))      setLifetimeTicks(nbt.getInt("Life"));
    }

    /* ======================== GECKOLIB ======================== */
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation ANIM_APPEAR = RawAnimation.begin().then("appear", Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation ANIM_IDLE   = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation ANIM_VANISH = RawAnimation.begin().then("vanish", Animation.LoopType.PLAY_ONCE);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "sigil", 0, state -> {
            final int lifeLeft = getLifetimeTicks();

            // Play "appear" only for the first 0.24s (5 ticks)
            if (this.age < APPEAR_TICKS) {
                state.setAnimation(ANIM_APPEAR);
                return PlayState.CONTINUE;
            }

            // Play "vanish" only for the final 0.34s (7 ticks)
            if (lifeLeft <= VANISH_TICKS) {
                state.setAnimation(ANIM_VANISH);
                return PlayState.CONTINUE;
            }

            // Otherwise idle
            state.setAnimation(ANIM_IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }
}
