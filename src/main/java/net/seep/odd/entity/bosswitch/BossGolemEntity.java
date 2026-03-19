package net.seep.odd.entity.bosswitch;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
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

import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class BossGolemEntity extends HostileEntity implements GeoEntity {

    private static final RawAnimation CORPSE           = RawAnimation.begin().thenLoop("corpse");
    private static final RawAnimation CORPSE_SPAWN     = RawAnimation.begin().thenPlay("corpse_spawn");
    private static final RawAnimation IDLE             = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK             = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation SWING_ATTACK     = RawAnimation.begin().thenPlay("swing_attack");
    private static final RawAnimation PICKUP           = RawAnimation.begin().thenPlay("pickup");
    private static final RawAnimation PICKUP_SUCCESS   = RawAnimation.begin().thenPlay("pickup_success");
    private static final RawAnimation PICKUP_WALK      = RawAnimation.begin().thenLoop("pickup_walk");
    private static final RawAnimation PICKUP_THROW     = RawAnimation.begin().thenPlay("pickup_throw");
    private static final RawAnimation SMASH_ATTACK     = RawAnimation.begin().thenPlay("smash_attack");
    private static final RawAnimation SHOCKWAVE_ATTACK = RawAnimation.begin().thenPlay("shockwave_attack");

    private static final int AWAKEN_TICKS = 187;

    /*
     * ===== EASY TUNING =====
     * attackCooldown = shared "normal attack rhythm"
     * pickup/shockwave cooldowns = ability cooldowns
     */
    private static final int PICKUP_ABILITY_COOLDOWN_TICKS = 100;
    private static final int SHOCKWAVE_ABILITY_COOLDOWN_TICKS = 140;

    private static final int PICKUP_ABILITY_CHANCE = 5;
    private static final int SHOCKWAVE_ABILITY_CHANCE = 4;
    private static final int CLOSE_SMASH_CHANCE = 4;

    /*
     * ===== PICKUP TUNING =====
     */
    private static final double PICKUP_TRIGGER_RANGE = 5.0D;
    private static final double PICKUP_TRIGGER_RANGE_SQ = PICKUP_TRIGGER_RANGE * PICKUP_TRIGGER_RANGE;

    // new rush/commit behavior before the actual pickup animation begins
    private static final int PICKUP_RUSH_MAX_TICKS = 30;
    private static final double PICKUP_RUSH_SPEED = 1.55D;
    private static final double PICKUP_BEGIN_ANIM_RANGE = 4.75D;
    private static final double PICKUP_BEGIN_ANIM_RANGE_SQ = PICKUP_BEGIN_ANIM_RANGE * PICKUP_BEGIN_ANIM_RANGE;

    /*
     * ===== THROW TUNING =====
     */
    private static final int THROW_IMPACT_ARM_TICKS = 10;
    private static final double THROW_RELEASE_FORWARD = 1.35D;
    private static final double THROW_RELEASE_UP = 0.18D;
    private static final double THROW_SPEED_XZ = 3.15D;
    private static final double THROW_SPEED_Y = 0.92D;

    /*
     * ===== NORMAL ATTACK RANGES =====
     */
    private static final double SWING_RANGE = 4.6D;
    private static final double SWING_RANGE_SQ = SWING_RANGE * SWING_RANGE;

    /*
     * ===== SMASH TUNING =====
     * total = 1.44s ~= 29 ticks
     * hit at 0.92s ~= 18 ticks
     */
    private static final int SMASH_HIT_TICK = 18;
    private static final int SMASH_TOTAL_TICKS = 29;
    private static final double SMASH_RANGE = 5.75D;
    private static final double SMASH_RANGE_SQ = SMASH_RANGE * SMASH_RANGE;
    private static final float SMASH_DAMAGE = 18.0f;

    /*
     * ===== SHOCKWAVE TUNING =====
     * total = 4.6s ~= 92 ticks
     * hit1 at 0.96s ~= 19
     * hit2 at 2.36s ~= 47
     * hit3 at 4.10s ~= 82
     */
    private static final int SHOCKWAVE_HIT_1 = 19;
    private static final int SHOCKWAVE_HIT_2 = 47;
    private static final int SHOCKWAVE_HIT_3 = 82;
    private static final int SHOCKWAVE_TOTAL_TICKS = 92;
    private static final double SHOCKWAVE_TRIGGER_RANGE = 30.0D;
    private static final double SHOCKWAVE_TRIGGER_RANGE_SQ = SHOCKWAVE_TRIGGER_RANGE * SHOCKWAVE_TRIGGER_RANGE;

    private static final byte STATUS_SHOCKWAVE_LIGHT = 71;
    private static final byte STATUS_SHOCKWAVE_HEAVY = 72;

    private static final TrackedData<Integer> ANIM_STATE =
            DataTracker.registerData(BossGolemEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> ACTIVE =
            DataTracker.registerData(BossGolemEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> AWAKENING =
            DataTracker.registerData(BossGolemEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> CARRIED_ENTITY_ID =
            DataTracker.registerData(BossGolemEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private enum AnimState {
        CORPSE,
        CORPSE_SPAWN,
        IDLE,
        WALK,
        SWING_ATTACK,
        PICKUP,
        PICKUP_SUCCESS,
        PICKUP_WALK,
        PICKUP_THROW,
        SMASH_ATTACK,
        SHOCKWAVE_ATTACK
    }

    private static final class ActiveShockwave {
        double radius;
        final double maxRadius;
        final double speed;
        final float damage;
        final float knockback;
        final boolean heavy;
        final double sourceSurfaceY;
        final Set<UUID> hitEntities = new HashSet<>();

        ActiveShockwave(boolean heavy, double sourceSurfaceY) {
            this.radius = 0.8D;
            this.maxRadius = 30.0D;
            this.speed = heavy ? 1.85D : 1.45D;
            this.damage = heavy ? 15.0f : 10.0f;
            this.knockback = heavy ? 1.45f : 0.72f;
            this.heavy = heavy;
            this.sourceSurfaceY = sourceSurfaceY;
        }
    }

    private @Nullable UUID ownerUuid;
    private @Nullable BlockPos homePos;

    private int actionTicks = 0;
    private int attackCooldown = 40;
    private int pickupAbilityCooldown = 20 * 10;
    private int shockwaveAbilityCooldown = 20 * 10;

    private @Nullable UUID carriedEntityUuid;
    private @Nullable UUID thrownEntityUuid;
    private int thrownTicks = 0;

    private int allowDismountTicks = 0;
    private @Nullable Vec3d queuedThrowDir = null;

    // committed pickup rush state
    private @Nullable UUID pickupRushTargetUuid;
    private int pickupRushTicks = 0;

    private final List<ActiveShockwave> activeShockwaves = new ArrayList<>();

    public BossGolemEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.setPersistent();
        this.experiencePoints = 0;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 500.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.44D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0D)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 14.0D)
                .add(EntityAttributes.GENERIC_ARMOR, 20.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(ANIM_STATE, AnimState.CORPSE.ordinal());
        this.dataTracker.startTracking(ACTIVE, false);
        this.dataTracker.startTracking(AWAKENING, false);
        this.dataTracker.startTracking(CARRIED_ENTITY_ID, -1);
    }

    public void setOwnerUuid(UUID uuid) {
        this.ownerUuid = uuid;
    }

    public void setHomePos(BlockPos pos) {
        this.homePos = pos.toImmutable();
    }

    public void beginAwakening() {
        if (isActiveBody() || isAwakeningBody()) return;

        this.dataTracker.set(AWAKENING, true);
        this.actionTicks = 0;
        setAnimState(AnimState.CORPSE_SPAWN);
    }

    public boolean isActiveBody() {
        return this.dataTracker.get(ACTIVE);
    }

    public boolean isAwakeningBody() {
        return this.dataTracker.get(AWAKENING);
    }

    public int getCarriedEntityIdClient() {
        return this.dataTracker.get(CARRIED_ENTITY_ID);
    }

    public boolean shouldBlockPassengerDismount(Entity passenger) {
        return this.allowDismountTicks <= 0
                && this.carriedEntityUuid != null
                && passenger != null
                && this.carriedEntityUuid.equals(passenger.getUuid());
    }

    private boolean isPickupRushing() {
        return this.pickupRushTargetUuid != null;
    }

    private void clearPickupRush() {
        this.pickupRushTargetUuid = null;
        this.pickupRushTicks = 0;
    }

    private void allowPassengerDismountNow() {
        this.allowDismountTicks = 3;
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        return new MobNavigation(this, world);
    }

    @Override
    protected void initGoals() {
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
        this.goalSelector.add(1, new CombatGoal(this));
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.getWorld().isClient) {
            if (this.allowDismountTicks > 0) this.allowDismountTicks--;
            if (this.pickupAbilityCooldown > 0) this.pickupAbilityCooldown--;
            if (this.shockwaveAbilityCooldown > 0) this.shockwaveAbilityCooldown--;

            if (this.ownerUuid != null && this.getWorld() instanceof ServerWorld sw) {
                Entity owner = sw.getEntity(this.ownerUuid);
                if (!(owner instanceof BossWitchEntity witch) || !witch.isAlive()) {
                    this.discard();
                    return;
                }
            }

            if (this.homePos == null) {
                this.homePos = this.getBlockPos();
            }

            if (isAwakeningBody()) {
                this.actionTicks++;
                setAnimState(AnimState.CORPSE_SPAWN);

                if (this.actionTicks >= AWAKEN_TICKS) {
                    this.dataTracker.set(AWAKENING, false);
                    this.dataTracker.set(ACTIVE, true);
                    this.actionTicks = 0;
                    this.attackCooldown = 30;
                    this.pickupAbilityCooldown = 80;
                    this.shockwaveAbilityCooldown = 70;
                    setAnimState(AnimState.IDLE);
                }
                return;
            }

            if (!isActiveBody()) {
                this.setVelocity(Vec3d.ZERO);
                this.getNavigation().stop();
                setAnimState(AnimState.CORPSE);
                return;
            }

            if (this.attackCooldown > 0) this.attackCooldown--;

            tickThrownEntity();
            tickActiveShockwaves();

            if (isPickupRushing()) {
                tickPickupRush();
                return;
            }

            LivingEntity target = this.getTarget();
            if (target != null && isAttackAnimation(getAnimState())) {
                this.getLookControl().lookAt(target, 25.0f, 25.0f);
            }

            if (getAnimState() == AnimState.PICKUP_THROW) {
                if (this.carriedEntityUuid != null) {
                    tickCarryState();
                } else {
                    tickThrowRecovery();
                }
                return;
            }

            if (this.carriedEntityUuid != null) {
                tickCarryState();
            }
        }
    }

    private boolean isAttackAnimation(AnimState state) {
        return state == AnimState.SWING_ATTACK
                || state == AnimState.PICKUP
                || state == AnimState.PICKUP_SUCCESS
                || state == AnimState.PICKUP_WALK
                || state == AnimState.PICKUP_THROW
                || state == AnimState.SMASH_ATTACK
                || state == AnimState.SHOCKWAVE_ATTACK;
    }

    private static Vec3d flattenNormalized(Vec3d v) {
        Vec3d flat = new Vec3d(v.x, 0.0D, v.z);
        if (flat.lengthSquared() < 1.0E-6) {
            return new Vec3d(1.0D, 0.0D, 0.0D);
        }
        return flat.normalize();
    }

    private void faceRightHandThrowDirection(Vec3d desiredThrowDir) {
        Vec3d right = flattenNormalized(desiredThrowDir);

        Vec3d forward = new Vec3d(right.z, 0.0D, -right.x);
        float yaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));

        this.setYaw(yaw);
        this.prevYaw = yaw;
        this.bodyYaw = yaw;
        this.prevBodyYaw = yaw;
        this.headYaw = yaw;
        this.prevHeadYaw = yaw;
    }

    private void tickPickupRush() {
        Entity entity = getTrackedEntity(this.pickupRushTargetUuid);
        if (!(entity instanceof LivingEntity target) || !target.isAlive() || !isInsideArena(target)) {
            clearPickupRush();
            this.getNavigation().stop();
            setAnimState(AnimState.IDLE);
            return;
        }

        this.pickupRushTicks++;
        this.getLookControl().lookAt(target, 30.0f, 30.0f);
        this.getNavigation().startMovingTo(target, PICKUP_RUSH_SPEED);
        setAnimState(AnimState.WALK);

        double d2 = this.squaredDistanceTo(target);
        if (d2 <= PICKUP_BEGIN_ANIM_RANGE_SQ) {
            clearPickupRush();
            this.actionTicks = 0;
            this.getNavigation().stop();
            setAnimState(AnimState.PICKUP);
            return;
        }

        if (this.pickupRushTicks >= PICKUP_RUSH_MAX_TICKS) {
            clearPickupRush();
            this.getNavigation().stop();
            this.attackCooldown = 10;
            setAnimState(AnimState.IDLE);
        }
    }

    private void tickCarryState() {
        Entity carried = getTrackedEntity(this.carriedEntityUuid);
        if (!(carried instanceof LivingEntity living) || !living.isAlive()) {
            this.queuedThrowDir = null;
            clearCarry();
            setAnimState(AnimState.IDLE);
            return;
        }

        if (living.getVehicle() != this) {
            living.startRiding(this, true);
        }

        if (getAnimState() == AnimState.PICKUP_SUCCESS) {
            applyCarryEffects(living);

            this.actionTicks++;
            if (this.actionTicks >= 11) {
                this.actionTicks = 0;
                setAnimState(AnimState.PICKUP_WALK);
            }
            return;
        }

        if (getAnimState() == AnimState.PICKUP_WALK) {
            applyCarryEffects(living);

            this.actionTicks++;

            Vec3d moveTarget = chooseCarryMoveTarget(living);
            this.getNavigation().startMovingTo(moveTarget.x, moveTarget.y, moveTarget.z, 1.0D);

            if (squaredDistanceTo(moveTarget.x, moveTarget.y, moveTarget.z) <= 9.0D || this.actionTicks > 70) {
                this.actionTicks = 0;
                this.getNavigation().stop();

                this.queuedThrowDir = flattenNormalized(chooseThrowDirection(living));
                faceRightHandThrowDirection(this.queuedThrowDir);

                setAnimState(AnimState.PICKUP_THROW);
            }
            return;
        }

        if (getAnimState() == AnimState.PICKUP_THROW) {
            this.actionTicks++;

            if (this.queuedThrowDir != null) {
                faceRightHandThrowDirection(this.queuedThrowDir);
            }

            if (this.actionTicks < 15) {
                applyCarryEffects(living);
                return;
            }

            if (this.actionTicks == 15) {
                performThrow(living);
            }
        }
    }

    private void tickThrowRecovery() {
        this.actionTicks++;

        if (this.actionTicks >= 38) {
            this.actionTicks = 0;
            this.attackCooldown = 12;
            this.queuedThrowDir = null;
            setAnimState(AnimState.IDLE);
        }
    }

    private void applyCarryEffects(LivingEntity living) {
        living.setVelocity(Vec3d.ZERO);
        living.velocityModified = true;
        living.fallDistance = 0.0f;
        living.setSneaking(false);

        Registries.STATUS_EFFECT.getOrEmpty(new Identifier(Oddities.MOD_ID, "powerless")).ifPresent(effect ->
                living.addStatusEffect(new StatusEffectInstance(effect, 10, 0, false, false, true))
        );
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, Entity.PositionUpdater positionUpdater) {
        if (this.carriedEntityUuid != null && this.carriedEntityUuid.equals(passenger.getUuid())) {
            Vec3d seat = getCarryPos();
            positionUpdater.accept(passenger, seat.x, seat.y, seat.z);
            return;
        }

        super.updatePassengerPosition(passenger, positionUpdater);
    }

    private Vec3d getCarryPos() {
        Vec3d forward = this.getRotationVec(1.0f);
        Vec3d right = new Vec3d(-forward.z, 0.0D, forward.x);
        if (right.lengthSquared() > 1.0E-6) {
            right = right.normalize();
        }

        return this.getPos()
                .add(forward.multiply(1.9D))
                .add(right.multiply(1.35D))
                .add(0.0D, 2.0D, 0.0D);
    }

    private Vec3d chooseCarryMoveTarget(LivingEntity carried) {
        List<PlayerEntity> others = (List<PlayerEntity>) this.getWorld().getPlayers().stream()
                .filter(p -> p.isAlive() && !p.isCreative() && !p.isSpectator())
                .filter(p -> this.homePos == null || isInsideArena(p))
                .filter(p -> p.getId() != carried.getId())
                .sorted(Comparator.comparingDouble(this::squaredDistanceTo))
                .toList();

        if (!others.isEmpty()) {
            return others.get(0).getPos();
        }

        if (this.homePos == null) {
            return this.getPos().add(this.getRotationVec(1.0f).multiply(8.0D));
        }

        Vec3d center = Vec3d.ofCenter(this.homePos);
        Vec3d outward = this.getPos().subtract(center);
        if (outward.lengthSquared() < 1.0E-6) {
            outward = new Vec3d(1.0D, 0.0D, 0.0D);
        } else {
            outward = new Vec3d(outward.x, 0.0D, outward.z).normalize();
        }

        return center.add(outward.multiply(28.0D));
    }

    private void performThrow(LivingEntity living) {
        Vec3d throwDir = this.queuedThrowDir != null
                ? flattenNormalized(this.queuedThrowDir)
                : flattenNormalized(chooseThrowDirection(living));

        allowPassengerDismountNow();
        living.stopRiding();

        Vec3d releasePos = getCarryPos()
                .add(throwDir.multiply(THROW_RELEASE_FORWARD))
                .add(0.0D, THROW_RELEASE_UP, 0.0D);

        living.refreshPositionAndAngles(
                releasePos.x, releasePos.y, releasePos.z,
                living.getYaw(), living.getPitch()
        );

        living.setVelocity(
                throwDir.x * THROW_SPEED_XZ,
                THROW_SPEED_Y,
                throwDir.z * THROW_SPEED_XZ
        );
        living.velocityModified = true;
        living.fallDistance = 0.0f;

        this.thrownEntityUuid = living.getUuid();
        this.thrownTicks = 0;

        this.queuedThrowDir = null;
        clearCarry();
        this.playSound(SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.75f);
    }

    private Vec3d chooseThrowDirection(LivingEntity carried) {
        List<PlayerEntity> others = (List<PlayerEntity>) this.getWorld().getPlayers().stream()
                .filter(p -> p.isAlive() && !p.isCreative() && !p.isSpectator())
                .filter(p -> p.getId() != carried.getId())
                .filter(this::isInsideArena)
                .sorted(Comparator.comparingDouble(this::squaredDistanceTo))
                .toList();

        if (!others.isEmpty()) {
            Vec3d to = others.get(0).getPos().subtract(this.getPos());
            Vec3d flat = new Vec3d(to.x, 0.0D, to.z);
            if (flat.lengthSquared() > 1.0E-6) return flat.normalize();
        }

        if (this.homePos != null) {
            Vec3d center = Vec3d.ofCenter(this.homePos);
            Vec3d out = this.getPos().subtract(center);
            Vec3d flat = new Vec3d(out.x, 0.0D, out.z);
            if (flat.lengthSquared() > 1.0E-6) return flat.normalize();
        }

        return this.getRotationVec(1.0f).normalize();
    }

    private void tickThrownEntity() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        if (this.thrownEntityUuid == null) return;

        Entity thrown = getTrackedEntity(this.thrownEntityUuid);
        if (!(thrown instanceof LivingEntity living) || !living.isAlive()) {
            this.thrownEntityUuid = null;
            this.thrownTicks = 0;
            return;
        }

        this.thrownTicks++;
        living.fallDistance = 0.0f;

        if (this.thrownTicks <= THROW_IMPACT_ARM_TICKS) {
            return;
        }

        boolean blockImpact = sw.getBlockCollisions(living, living.getBoundingBox().expand(0.04D)).iterator().hasNext();
        boolean groundImpact = this.thrownTicks > THROW_IMPACT_ARM_TICKS && living.isOnGround();
        boolean timedOut = this.thrownTicks > 50;

        boolean entityImpact = !sw.getEntitiesByClass(
                LivingEntity.class,
                living.getBoundingBox().expand(1.2D),
                e -> e.isAlive()
                        && e != living
                        && e != this
                        && (this.ownerUuid == null || !this.ownerUuid.equals(e.getUuid()))
        ).isEmpty();

        if (blockImpact || groundImpact || entityImpact || timedOut) {
            explodeThrownEntity(sw, living);
            this.thrownEntityUuid = null;
            this.thrownTicks = 0;
        }
    }

    private void explodeThrownEntity(ServerWorld sw, LivingEntity sourceEntity) {
        sw.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION_EMITTER,
                sourceEntity.getX(), sourceEntity.getY() + 1.0D, sourceEntity.getZ(),
                1, 0.0D, 0.0D, 0.0D, 0.0D);

        this.playSound(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.1f, 0.9f);

        Box box = sourceEntity.getBoundingBox().expand(3.0D);
        for (LivingEntity living : sw.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive())) {
            if (living == this) continue;

            double d2 = living.squaredDistanceTo(sourceEntity);
            if (d2 > 9.0D) continue;

            living.damage(sw.getDamageSources().explosion(this, this), 8.0f);

            Vec3d dir = living.getPos().subtract(sourceEntity.getPos());
            Vec3d flat = new Vec3d(dir.x, 0.0D, dir.z);
            if (flat.lengthSquared() < 1.0E-6) flat = new Vec3d(1.0D, 0.0D, 0.0D);
            flat = flat.normalize();

            living.addVelocity(flat.x * 1.2D, 0.45D, flat.z * 1.2D);
            living.velocityModified = true;
        }
    }

    private double findSurfaceY(ServerWorld sw, double x, double aroundY, double z) {
        int ix = MathHelper.floor(x);
        int iz = MathHelper.floor(z);
        int top = MathHelper.floor(aroundY + 3.0D);
        int bottom = MathHelper.floor(aroundY - 6.0D);

        for (int y = top; y >= bottom; y--) {
            BlockPos pos = new BlockPos(ix, y, iz);
            if (!sw.getBlockState(pos).isSolidBlock(sw, pos)) continue;

            BlockPos above = pos.up();
            if (sw.getBlockState(above).isSolidBlock(sw, above)) continue;

            return y + 1.0D;
        }

        return aroundY;
    }

    private boolean isShockwaveReachable(ServerWorld sw, ActiveShockwave wave, double tx, double tz, double targetSurfaceY) {
        double sx = this.getX();
        double sz = this.getZ();
        double dx = tx - sx;
        double dz = tz - sz;
        double dist = Math.sqrt(dx * dx + dz * dz);

        int steps = Math.max(1, MathHelper.ceil(dist / 0.65D));
        double prevY = wave.sourceSurfaceY;

        for (int i = 1; i <= steps; i++) {
            double t = (double) i / (double) steps;
            double px = sx + dx * t;
            double pz = sz + dz * t;

            double surfaceY = findSurfaceY(sw, px, prevY + 1.25D, pz);

            if (surfaceY - prevY > 1.05D) {
                return false;
            }

            prevY = surfaceY;
        }

        return targetSurfaceY - prevY <= 1.05D;
    }

    private void tickActiveShockwaves() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        if (this.activeShockwaves.isEmpty()) return;

        Iterator<ActiveShockwave> it = this.activeShockwaves.iterator();
        while (it.hasNext()) {
            ActiveShockwave wave = it.next();
            wave.radius += wave.speed;
            applyShockwaveHits(sw, wave);

            if (wave.radius >= wave.maxRadius) {
                it.remove();
            }
        }
    }

    private void applyShockwaveHits(ServerWorld sw, ActiveShockwave wave) {
        double band = wave.heavy ? 1.55D : 1.10D;
        Box box = new Box(
                this.getX() - wave.radius - band, this.getY() - 2.0D, this.getZ() - wave.radius - band,
                this.getX() + wave.radius + band, this.getY() + 4.0D, this.getZ() + wave.radius + band
        );

        for (LivingEntity living : sw.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != this)) {
            if (this.ownerUuid != null && this.ownerUuid.equals(living.getUuid())) continue;
            if (!isInsideArena(living)) continue;
            if (wave.hitEntities.contains(living.getUuid())) continue;

            if (!living.isOnGround()) continue;

            double dx = living.getX() - this.getX();
            double dz = living.getZ() - this.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (Math.abs(dist - wave.radius) > band) continue;

            double targetSurfaceY = findSurfaceY(sw, living.getX(), living.getY() + 0.8D, living.getZ());

            if (Math.abs(living.getY() - targetSurfaceY) > 0.65D) continue;
            if (!isShockwaveReachable(sw, wave, living.getX(), living.getZ(), targetSurfaceY)) continue;

            wave.hitEntities.add(living.getUuid());

            living.damage(sw.getDamageSources().mobAttack(this), wave.damage);

            Vec3d dir = new Vec3d(dx, 0.0D, dz);
            if (dir.lengthSquared() < 1.0E-6) dir = new Vec3d(1.0D, 0.0D, 0.0D);
            dir = dir.normalize();

            float up = wave.heavy ? 0.55f : 0.26f;
            living.addVelocity(dir.x * wave.knockback, up, dir.z * wave.knockback);
            living.velocityModified = true;
        }
    }

    private void releaseShockwave(boolean heavy) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        double sourceSurfaceY = findSurfaceY(sw, this.getX(), this.getY() + 1.0D, this.getZ());
        this.activeShockwaves.add(new ActiveShockwave(heavy, sourceSurfaceY));
        sw.sendEntityStatus(this, heavy ? STATUS_SHOCKWAVE_HEAVY : STATUS_SHOCKWAVE_LIGHT);

        this.playSound(
                heavy ? SoundEvents.ENTITY_GENERIC_EXPLODE : SoundEvents.ENTITY_IRON_GOLEM_ATTACK,
                heavy ? 1.2f : 1.0f,
                heavy ? 0.70f : 0.85f
        );
    }

    private void clearCarry() {
        this.carriedEntityUuid = null;
        this.dataTracker.set(CARRIED_ENTITY_ID, -1);
        this.getNavigation().stop();
    }

    private @Nullable Entity getTrackedEntity(@Nullable UUID uuid) {
        if (uuid == null) return null;
        if (!(this.getWorld() instanceof ServerWorld sw)) return null;
        return sw.getEntity(uuid);
    }

    private boolean isInsideArena(Entity entity) {
        if (this.homePos == null) return true;

        double dx = entity.getX() - (this.homePos.getX() + 0.5D);
        double dz = entity.getZ() - (this.homePos.getZ() + 0.5D);
        return (dx * dx + dz * dz) <= (30.0D * 30.0D);
    }

    private void startSwingAttack() {
        this.actionTicks = 0;
        this.getNavigation().stop();
        setAnimState(AnimState.SWING_ATTACK);
    }

    private void startPickupAttack(LivingEntity target) {
        this.actionTicks = 0;
        this.pickupAbilityCooldown = PICKUP_ABILITY_COOLDOWN_TICKS;
        this.pickupRushTicks = 0;
        this.pickupRushTargetUuid = target.getUuid();
        this.getNavigation().stop();
        setAnimState(AnimState.WALK);
    }

    private void startSmashAttack() {
        this.actionTicks = 0;
        this.getNavigation().stop();
        setAnimState(AnimState.SMASH_ATTACK);
    }

    private void startShockwaveAttack() {
        this.actionTicks = 0;
        this.shockwaveAbilityCooldown = SHOCKWAVE_ABILITY_COOLDOWN_TICKS;
        this.getNavigation().stop();
        setAnimState(AnimState.SHOCKWAVE_ATTACK);
    }

    private void tickSwingAttack() {
        this.actionTicks++;

        if (this.actionTicks == 11 && this.getWorld() instanceof ServerWorld sw) {
            performSwingHit(sw);
        }

        if (this.actionTicks >= 22) {
            this.actionTicks = 0;
            this.attackCooldown = 14;
            setAnimState(AnimState.IDLE);
        }
    }

    private void tickSmashAttack() {
        this.actionTicks++;

        if (this.actionTicks == SMASH_HIT_TICK && this.getWorld() instanceof ServerWorld sw) {
            performSmashHit(sw);
        }

        if (this.actionTicks >= SMASH_TOTAL_TICKS) {
            this.actionTicks = 0;
            this.attackCooldown = 18;
            setAnimState(AnimState.IDLE);
        }
    }

    private void tickShockwaveAttack() {
        this.actionTicks++;

        if (this.actionTicks == SHOCKWAVE_HIT_1) {
            releaseShockwave(false);
        }

        if (this.actionTicks == SHOCKWAVE_HIT_2) {
            releaseShockwave(false);
        }

        if (this.actionTicks == SHOCKWAVE_HIT_3) {
            releaseShockwave(true);
        }

        if (this.actionTicks >= SHOCKWAVE_TOTAL_TICKS) {
            this.actionTicks = 0;
            this.attackCooldown = 24;
            setAnimState(AnimState.IDLE);
        }
    }

    private void performSwingHit(ServerWorld sw) {
        this.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.70f);

        Vec3d forward = this.getRotationVec(1.0f);
        Vec3d origin = this.getPos().add(0.0D, 2.3D, 0.0D);

        Box box = this.getBoundingBox().expand(4.6D, 1.5D, 4.6D);
        for (LivingEntity living : sw.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != this)) {
            if (this.ownerUuid != null && this.ownerUuid.equals(living.getUuid())) continue;

            Vec3d to = living.getPos().add(0.0D, living.getStandingEyeHeight() * 0.5D, 0.0D).subtract(origin);
            Vec3d flat = new Vec3d(to.x, 0.0D, to.z);
            if (flat.lengthSquared() < 1.0E-6) continue;
            flat = flat.normalize();

            if (forward.dotProduct(flat) <= 0.0D) continue;

            if (isBlockedByShield(living, origin)) {
                living.playSound(SoundEvents.ITEM_SHIELD_BLOCK, 1.0f, 0.9f);
                continue;
            }

            living.damage(sw.getDamageSources().mobAttack(this), 12.0f);
            living.addVelocity(flat.x * 1.45D, 0.45D, flat.z * 1.45D);
            living.velocityModified = true;
        }
    }

    private void performSmashHit(ServerWorld sw) {
        Vec3d forward = flattenNormalized(this.getRotationVec(1.0f));
        Vec3d center = this.getPos().add(forward.multiply(3.8D)).add(0.0D, 0.2D, 0.0D);

        this.playSound(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.05f, 0.72f);

        sw.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION,
                center.x, this.getY() + 0.12D, center.z,
                6, 0.6D, 0.05D, 0.6D, 0.01D);

        Box box = new Box(
                center.x - 2.8D, this.getY() - 0.5D, center.z - 2.8D,
                center.x + 2.8D, this.getY() + 3.0D, center.z + 2.8D
        );

        for (LivingEntity living : sw.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != this)) {
            if (this.ownerUuid != null && this.ownerUuid.equals(living.getUuid())) continue;

            Vec3d to = living.getPos().subtract(this.getPos());
            Vec3d flat = new Vec3d(to.x, 0.0D, to.z);
            if (flat.lengthSquared() < 1.0E-6) continue;
            flat = flat.normalize();

            if (forward.dotProduct(flat) <= 0.15D) continue;

            living.damage(sw.getDamageSources().mobAttack(this), SMASH_DAMAGE);
            living.addVelocity(flat.x * 1.05D, 0.62D, flat.z * 1.05D);
            living.velocityModified = true;
        }
    }

    private boolean isBlockedByShield(LivingEntity target, Vec3d from) {
        if (!target.isBlocking()) return false;

        Vec3d look = target.getRotationVec(1.0f);
        Vec3d flatLook = new Vec3d(look.x, 0.0D, look.z);
        if (flatLook.lengthSquared() < 1.0E-6) return false;
        flatLook = flatLook.normalize();

        Vec3d toAttacker = new Vec3d(from.x - target.getX(), 0.0D, from.z - target.getZ());
        if (toAttacker.lengthSquared() < 1.0E-6) return true;
        toAttacker = toAttacker.normalize();

        return flatLook.dotProduct(toAttacker) > 0.0D;
    }

    private void tickPickupAttack() {
        this.actionTicks++;

        if (this.actionTicks == 24) {
            LivingEntity grabbed = tryPickupTarget();
            if (grabbed != null) {
                this.carriedEntityUuid = grabbed.getUuid();
                this.dataTracker.set(CARRIED_ENTITY_ID, grabbed.getId());

                grabbed.startRiding(this, true);
                applyCarryEffects(grabbed);

                this.actionTicks = 0;
                setAnimState(AnimState.PICKUP_SUCCESS);
                return;
            }
        }

        if (this.actionTicks >= 43) {
            this.actionTicks = 0;
            this.attackCooldown = 12;
            setAnimState(AnimState.IDLE);
        }
    }

    private @Nullable LivingEntity tryPickupTarget() {
        Vec3d forward = this.getRotationVec(1.0f);
        Vec3d origin = this.getPos().add(0.0D, 2.2D, 0.0D);

        Box box = this.getBoundingBox().expand(2.8D, 1.5D, 2.8D);
        List<LivingEntity> candidates = this.getWorld().getEntitiesByClass(
                LivingEntity.class,
                box,
                e -> e.isAlive() && e != this && !(this.ownerUuid != null && this.ownerUuid.equals(e.getUuid()))
        );

        candidates.sort(Comparator.comparingDouble(this::squaredDistanceTo));

        for (LivingEntity living : candidates) {
            Vec3d to = living.getPos().add(0.0D, living.getStandingEyeHeight() * 0.5D, 0.0D).subtract(origin);
            Vec3d flat = new Vec3d(to.x, 0.0D, to.z);
            if (flat.lengthSquared() < 1.0E-6) continue;
            flat = flat.normalize();

            if (forward.dotProduct(flat) < 0.35D) continue;
            return living;
        }

        return null;
    }

    private void setAnimState(AnimState state) {
        this.dataTracker.set(ANIM_STATE, state.ordinal());
    }

    private AnimState getAnimState() {
        int idx = MathHelper.clamp(this.dataTracker.get(ANIM_STATE), 0, AnimState.values().length - 1);
        return AnimState.values()[idx];
    }

    @Override
    public boolean canHit() {
        return isActiveBody();
    }

    @Override
    public boolean isAttackable() {
        return isActiveBody();
    }

    @Override
    public boolean isCollidable() {
        return isActiveBody();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (!isActiveBody()) {
            return false;
        }

        this.playSound(SoundEvents.BLOCK_ANVIL_LAND, 0.9f, 0.85f);
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void tickMovement() {
        super.tickMovement();

        if (!(this.getWorld() instanceof ServerWorld)) return;
        if (!isActiveBody()) return;

        if (isPickupRushing()) {
            if (getAnimState() != AnimState.WALK) {
                setAnimState(AnimState.WALK);
            }
            return;
        }

        AnimState anim = getAnimState();
        if (anim == AnimState.SWING_ATTACK) {
            tickSwingAttack();
        } else if (anim == AnimState.PICKUP) {
            tickPickupAttack();
        } else if (anim == AnimState.SMASH_ATTACK) {
            tickSmashAttack();
        } else if (anim == AnimState.SHOCKWAVE_ATTACK) {
            tickShockwaveAttack();
        }

        if (this.carriedEntityUuid == null
                && this.getNavigation().isFollowingPath()
                && anim == AnimState.IDLE) {
            setAnimState(AnimState.WALK);
        } else if (this.carriedEntityUuid == null
                && anim == AnimState.WALK
                && !this.getNavigation().isFollowingPath()) {
            setAnimState(AnimState.IDLE);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            switch (getAnimState()) {
                case CORPSE -> state.setAndContinue(CORPSE);
                case CORPSE_SPAWN -> state.setAndContinue(CORPSE_SPAWN);
                case IDLE -> state.setAndContinue(IDLE);
                case WALK -> state.setAndContinue(WALK);
                case SWING_ATTACK -> state.setAndContinue(SWING_ATTACK);
                case PICKUP -> state.setAndContinue(PICKUP);
                case PICKUP_SUCCESS -> state.setAndContinue(PICKUP_SUCCESS);
                case PICKUP_WALK -> state.setAndContinue(PICKUP_WALK);
                case PICKUP_THROW -> state.setAndContinue(PICKUP_THROW);
                case SMASH_ATTACK -> state.setAndContinue(SMASH_ATTACK);
                case SHOCKWAVE_ATTACK -> state.setAndContinue(SHOCKWAVE_ATTACK);
            }
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);

        if (this.ownerUuid != null) nbt.putUuid("Owner", this.ownerUuid);
        if (this.homePos != null) {
            nbt.putInt("HomeX", this.homePos.getX());
            nbt.putInt("HomeY", this.homePos.getY());
            nbt.putInt("HomeZ", this.homePos.getZ());
        }
        if (this.carriedEntityUuid != null) nbt.putUuid("Carried", this.carriedEntityUuid);
        if (this.thrownEntityUuid != null) nbt.putUuid("Thrown", this.thrownEntityUuid);
        if (this.pickupRushTargetUuid != null) nbt.putUuid("PickupRushTarget", this.pickupRushTargetUuid);

        nbt.putInt("ActionTicks", this.actionTicks);
        nbt.putInt("AttackCooldown", this.attackCooldown);
        nbt.putInt("PickupAbilityCooldown", this.pickupAbilityCooldown);
        nbt.putInt("ShockwaveAbilityCooldown", this.shockwaveAbilityCooldown);
        nbt.putBoolean("Active", isActiveBody());
        nbt.putBoolean("Awakening", isAwakeningBody());
        nbt.putInt("AnimState", getAnimState().ordinal());
        nbt.putInt("ThrownTicks", this.thrownTicks);
        nbt.putInt("AllowDismountTicks", this.allowDismountTicks);
        nbt.putInt("PickupRushTicks", this.pickupRushTicks);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);

        this.ownerUuid = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
        if (nbt.contains("HomeX") && nbt.contains("HomeY") && nbt.contains("HomeZ")) {
            this.homePos = new BlockPos(nbt.getInt("HomeX"), nbt.getInt("HomeY"), nbt.getInt("HomeZ"));
        }

        this.carriedEntityUuid = nbt.containsUuid("Carried") ? nbt.getUuid("Carried") : null;
        this.thrownEntityUuid = nbt.containsUuid("Thrown") ? nbt.getUuid("Thrown") : null;
        this.pickupRushTargetUuid = nbt.containsUuid("PickupRushTarget") ? nbt.getUuid("PickupRushTarget") : null;

        this.actionTicks = nbt.getInt("ActionTicks");
        this.attackCooldown = nbt.getInt("AttackCooldown");
        this.pickupAbilityCooldown = nbt.contains("PickupAbilityCooldown") ? nbt.getInt("PickupAbilityCooldown") : 80;
        this.shockwaveAbilityCooldown = nbt.contains("ShockwaveAbilityCooldown") ? nbt.getInt("ShockwaveAbilityCooldown") : 70;
        this.dataTracker.set(ACTIVE, nbt.getBoolean("Active"));
        this.dataTracker.set(AWAKENING, nbt.getBoolean("Awakening"));
        this.dataTracker.set(CARRIED_ENTITY_ID, -1);
        this.thrownTicks = nbt.getInt("ThrownTicks");
        this.allowDismountTicks = nbt.getInt("AllowDismountTicks");
        this.pickupRushTicks = nbt.getInt("PickupRushTicks");

        int anim = MathHelper.clamp(nbt.getInt("AnimState"), 0, AnimState.values().length - 1);
        this.dataTracker.set(ANIM_STATE, anim);

        this.queuedThrowDir = null;
        this.activeShockwaves.clear();
    }

    @Override
    public void handleStatus(byte status) {
        if (status == STATUS_SHOCKWAVE_LIGHT || status == STATUS_SHOCKWAVE_HEAVY) {
            if (this.getWorld().isClient) {
                handleShockwaveStatusClient(status == STATUS_SHOCKWAVE_HEAVY);
            }
            return;
        }

        super.handleStatus(status);
    }

    @Environment(EnvType.CLIENT)
    private void handleShockwaveStatusClient(boolean heavy) {
        net.seep.odd.entity.bosswitch.client.BossGolemShockwaveClient.spawnWave(this, heavy);
    }

    static final class CombatGoal extends Goal {
        private final BossGolemEntity mob;

        CombatGoal(BossGolemEntity mob) {
            this.mob = mob;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return true;
        }

        @Override
        public boolean shouldContinue() {
            return true;
        }

        @Override
        public void tick() {
            if (!mob.isActiveBody()) return;
            if (mob.carriedEntityUuid != null) return;
            if (mob.isPickupRushing()) return;

            AnimState anim = mob.getAnimState();
            if (anim == AnimState.SWING_ATTACK
                    || anim == AnimState.PICKUP
                    || anim == AnimState.PICKUP_SUCCESS
                    || anim == AnimState.PICKUP_WALK
                    || anim == AnimState.PICKUP_THROW
                    || anim == AnimState.CORPSE_SPAWN
                    || anim == AnimState.SMASH_ATTACK
                    || anim == AnimState.SHOCKWAVE_ATTACK) {
                return;
            }

            PlayerEntity target = mob.getWorld().getPlayers().stream()
                    .filter(p -> p.isAlive() && !p.isCreative() && !p.isSpectator())
                    .filter(mob::isInsideArena)
                    .min(Comparator.comparingDouble(mob::squaredDistanceTo))
                    .orElse(null);

            if (target == null) {
                mob.getNavigation().stop();
                mob.setAnimState(AnimState.IDLE);
                return;
            }

            mob.getLookControl().lookAt(target, 25.0f, 25.0f);

            double d2 = mob.squaredDistanceTo(target);

            if (mob.attackCooldown <= 0) {
                // pickup now commits from range: decide first, then rush in fast before animating
                if (mob.pickupAbilityCooldown <= 0
                        && mob.getRandom().nextInt(PICKUP_ABILITY_CHANCE) == 0) {
                    mob.startPickupAttack(target);
                    return;
                }

                if (mob.shockwaveAbilityCooldown <= 0
                        && target.isOnGround()
                        && d2 <= SHOCKWAVE_TRIGGER_RANGE_SQ
                        && d2 >= 7.0D * 7.0D
                        && mob.getRandom().nextInt(SHOCKWAVE_ABILITY_CHANCE) == 0) {
                    mob.startShockwaveAttack();
                    return;
                }

                if (d2 <= SWING_RANGE_SQ) {
                    if (d2 > (3.0D * 3.0D) && mob.getRandom().nextInt(CLOSE_SMASH_CHANCE) == 0) {
                        mob.startSmashAttack();
                    } else {
                        mob.startSwingAttack();
                    }
                    return;
                }

                if (d2 <= SMASH_RANGE_SQ) {
                    mob.startSmashAttack();
                    return;
                }
            }

            mob.getNavigation().startMovingTo(target, 1.0D);
            mob.setAnimState(AnimState.WALK);
        }
    }
}
