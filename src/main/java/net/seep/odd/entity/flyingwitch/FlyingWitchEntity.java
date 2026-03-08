// FILE: src/main/java/net/seep/odd/entity/flyingwitch/FlyingWitchEntity.java
package net.seep.odd.entity.flyingwitch;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.seep.odd.entity.ModEntities;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.UUID;

public final class FlyingWitchEntity extends HostileEntity implements GeoEntity {

    // ===== GeckoLib =====
    private static final RawAnimation IDLE   = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final TrackedData<Boolean> CHARGING_ATTACK =
            DataTracker.registerData(FlyingWitchEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    // ===== Attack timing =====
    private static final int CHARGE_TICKS = 20;
    private static final int SHOT_PERIOD_TICKS = 40;
    private static final double SHOOT_SPEED = 0.25; // slower projectile example

    private int chargeTicksLeft = 0;
    private int nextShotAge = 0;

    private @Nullable UUID heldHexUuid = null;

    public FlyingWitchEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 5;

        this.moveControl = new FlightMoveControl(this, 20, true);
        this.setNoGravity(true);
        this.noClip = false;
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(CHARGING_ATTACK, false);
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        return new BirdNavigation(this, world);
    }

    public boolean isChargingAttack() {
        return this.dataTracker.get(CHARGING_ATTACK);
    }

    private void setChargingAttack(boolean charging) {
        this.dataTracker.set(CHARGING_ATTACK, charging);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 22.0D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28D)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 0.45D);
    }

    /* ---------------- AI ---------------- */

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new OrbitAndStrafeGoal(this));
        this.goalSelector.add(2, new HexRangedAttackGoal(this));

        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.hasNoGravity()) {
            this.setNoGravity(true);
        }

        if (!this.getWorld().isClient) {
            if (chargeTicksLeft > 0) {
                this.setChargingAttack(true);
                chargeTicksLeft--;

                LivingEntity target = this.getTarget();
                if (target != null) {
                    this.getLookControl().lookAt(target, 30.0f, 30.0f);
                }

                HexProjectileEntity held = findHeldHex();
                if (held != null) {
                    Vec3d dir = getAimDirection();
                    Vec3d holdPos = this.getPos()
                            .add(0.0, this.getStandingEyeHeight() * 0.65, 0.0)
                            .add(dir.multiply(0.9));

                    held.setNoClip(true);
                    held.setArmed(false);
                    held.setPosition(holdPos.x, holdPos.y, holdPos.z);
                    held.setVelocity(Vec3d.ZERO);
                    held.setYaw(this.getYaw());
                    held.setPitch(this.getPitch());
                }

                if (chargeTicksLeft == 0) {
                    releaseShot();
                }
            } else {
                this.setChargingAttack(false);

                if (heldHexUuid != null) {
                    HexProjectileEntity held = findHeldHex();
                    if (held != null && !held.isArmed()) {
                        held.discard();
                    }
                    heldHexUuid = null;
                }
            }
        }
    }

    private Vec3d getAimDirection() {
        LivingEntity target = this.getTarget();
        if (target != null) {
            Vec3d from = this.getPos().add(0, this.getStandingEyeHeight() * 0.8, 0);
            Vec3d to = target.getPos().add(0, target.getStandingEyeHeight() * 0.6, 0);
            Vec3d dir = to.subtract(from);
            if (dir.lengthSquared() > 1.0E-6) return dir.normalize();
        }
        return this.getRotationVec(1.0f).normalize();
    }

    private @Nullable HexProjectileEntity findHeldHex() {
        if (heldHexUuid == null) return null;

        for (Entity e : this.getWorld().getEntitiesByClass(Entity.class, this.getBoundingBox().expand(24.0), ent -> true)) {
            if (e instanceof HexProjectileEntity hex && heldHexUuid.equals(hex.getUuid())) {
                return hex;
            }
        }
        return null;
    }

    private void beginCharge() {
        this.chargeTicksLeft = CHARGE_TICKS;
        this.nextShotAge = this.age + SHOT_PERIOD_TICKS;
        this.setChargingAttack(true);

        HexProjectileEntity hex = ModEntities.HEX_PROJECTILE.create(this.getWorld());
        if (hex == null) return;

        hex.setOwner(this);
        hex.setArmed(false);
        hex.setNoClip(true);
        hex.setVelocity(Vec3d.ZERO);

        Vec3d dir = getAimDirection();
        Vec3d pos = this.getPos()
                .add(0.0, this.getStandingEyeHeight() * 0.65, 0.0)
                .add(dir.multiply(0.9));

        hex.refreshPositionAndAngles(pos.x, pos.y, pos.z, this.getYaw(), this.getPitch());
        hex.setHeldVisual(true);

        this.getWorld().spawnEntity(hex);
        this.heldHexUuid = hex.getUuid();

        this.playSound(SoundEvents.ENTITY_WITCH_AMBIENT, 0.7f, 1.4f);
    }

    private void releaseShot() {
        HexProjectileEntity hex = findHeldHex();
        if (hex == null) {
            hex = ModEntities.HEX_PROJECTILE.create(this.getWorld());
            if (hex == null) {
                this.setChargingAttack(false);
                return;
            }
            hex.setOwner(this);
            this.getWorld().spawnEntity(hex);
        }

        Vec3d dir = getAimDirection();
        Vec3d spawn = this.getPos()
                .add(0.0, this.getStandingEyeHeight() * 0.65, 0.0)
                .add(dir.multiply(1.1));

        hex.setPosition(spawn.x, spawn.y, spawn.z);
        hex.setNoClip(false);
        hex.setHeldVisual(false);
        hex.setArmed(true);

        hex.setVelocity(dir.multiply(SHOOT_SPEED));
        hex.powerX = dir.x * 0.1;
        hex.powerY = dir.y * 0.1;
        hex.powerZ = dir.z * 0.1;

        this.playSound(SoundEvents.ENTITY_WITCH_THROW, 0.9f, 1.1f);
        this.heldHexUuid = null;
        this.setChargingAttack(false);
    }

    /* ---------------- Save/Load ---------------- */

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Charge", chargeTicksLeft);
        nbt.putInt("NextShotAge", nextShotAge);
        if (heldHexUuid != null) nbt.putUuid("HeldHex", heldHexUuid);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        chargeTicksLeft = nbt.getInt("Charge");
        nextShotAge = nbt.getInt("NextShotAge");
        heldHexUuid = nbt.containsUuid("HeldHex") ? nbt.getUuid("HeldHex") : null;
        this.setChargingAttack(chargeTicksLeft > 0);
    }

    /* ---------------- GeckoLib ---------------- */

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            if (this.isChargingAttack()) {
                state.setAndContinue(ATTACK);
                return PlayState.CONTINUE;
            }

            state.setAndContinue(IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /* ---------------- Goals ---------------- */

    static final class OrbitAndStrafeGoal extends Goal {
        private final FlyingWitchEntity mob;
        private float angle;
        private float radius;

        OrbitAndStrafeGoal(FlyingWitchEntity mob) {
            this.mob = mob;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return mob.getTarget() != null && mob.getTarget().isAlive();
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public void start() {
            Random r = mob.getRandom();
            this.angle = r.nextFloat() * (float) (Math.PI * 2.0);
            this.radius = 7.0f + r.nextFloat() * 4.0f;
        }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) return;

            double dist = mob.squaredDistanceTo(t);
            if (dist < 36.0) radius = Math.min(12.0f, radius + 0.08f);
            if (dist > 196.0) radius = Math.max(6.5f, radius - 0.06f);

            angle += 0.09f;

            double ox = Math.cos(angle) * radius;
            double oz = Math.sin(angle) * radius;

            double baseY = t.getY() + t.getStandingEyeHeight() + 2.2;
            double oy = baseY + Math.sin(angle * 0.5) * 1.2;

            Vec3d goal = new Vec3d(t.getX() + ox, oy, t.getZ() + oz);

            mob.getMoveControl().moveTo(goal.x, goal.y, goal.z, 1.35);
            mob.getLookControl().lookAt(t, 20.0f, 20.0f);
        }
    }

    static final class HexRangedAttackGoal extends Goal {
        private final FlyingWitchEntity mob;

        HexRangedAttackGoal(FlyingWitchEntity mob) {
            this.mob = mob;
        }

        @Override
        public boolean canStart() {
            LivingEntity t = mob.getTarget();
            if (t == null || !t.isAlive()) return false;
            if (mob.chargeTicksLeft > 0) return false;
            if (mob.age < mob.nextShotAge) return false;
            return mob.squaredDistanceTo(t) <= (26.0 * 26.0);
        }

        @Override
        public boolean shouldContinue() {
            return false;
        }

        @Override
        public void start() {
            mob.beginCharge();
        }
    }
}