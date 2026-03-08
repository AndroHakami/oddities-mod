package net.seep.odd.entity.windwitch;

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

public final class WindWitchEntity extends HostileEntity implements GeoEntity {

    private static final RawAnimation IDLE   = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenLoop("attack");

    private static final TrackedData<Boolean> CHARGING_ATTACK =
            DataTracker.registerData(WindWitchEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final int CHARGE_TICKS = 28;
    private static final int SHOT_PERIOD_TICKS = 20 * 8;

    private int chargeTicksLeft = 0;
    private int nextShotAge = 0;

    private @Nullable UUID heldTornado0 = null;
    private @Nullable UUID heldTornado1 = null;
    private @Nullable UUID heldTornado2 = null;

    public WindWitchEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 5;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 24.0D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.27D)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0D);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(CHARGING_ATTACK, false);
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        MobNavigation nav = new MobNavigation(this, world);
        nav.setCanPathThroughDoors(true);
        return nav;
    }

    public boolean isChargingAttack() {
        return this.dataTracker.get(CHARGING_ATTACK);
    }

    private void setChargingAttack(boolean value) {
        this.dataTracker.set(CHARGING_ATTACK, value);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new GroundOrbitGoal(this));
        this.goalSelector.add(2, new TornadoRangedAttackGoal(this));

        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.getWorld().isClient) {
            if (chargeTicksLeft > 0) {
                this.setChargingAttack(true);
                chargeTicksLeft--;

                LivingEntity target = this.getTarget();
                if (target != null) {
                    this.getLookControl().lookAt(target, 30.0f, 30.0f);
                }

                for (int slot = 0; slot < 3; slot++) {
                    TornadoProjectileEntity tornado = findHeldTornado(slot);
                    if (tornado != null) {
                        double baseAngle = (this.age * 0.35) + (slot * (Math.PI * 2.0 / 3.0));
                        double radius = 1.15;
                        double x = this.getX() + Math.cos(baseAngle) * radius;
                        double y = this.getY() + 0.95 + Math.sin(this.age * 0.18 + slot) * 0.10;
                        double z = this.getZ() + Math.sin(baseAngle) * radius;

                        tornado.setNoClip(true);
                        tornado.setArmed(false);
                        tornado.setHeldVisual(true);
                        tornado.setSlotIndex(slot);
                        tornado.setPosition(x, y, z);
                        tornado.setVelocity(Vec3d.ZERO);
                    }
                }

                if (chargeTicksLeft == 0) {
                    releaseTornadoes();
                }
            } else {
                this.setChargingAttack(false);

                for (int slot = 0; slot < 3; slot++) {
                    UUID uuid = getHeldUuid(slot);
                    if (uuid != null) {
                        TornadoProjectileEntity tornado = findHeldTornado(slot);
                        if (tornado != null && !tornado.isArmed()) {
                            tornado.discard();
                        }
                        setHeldUuid(slot, null);
                    }
                }
            }
        }
    }

    private @Nullable UUID getHeldUuid(int slot) {
        return switch (slot) {
            case 0 -> heldTornado0;
            case 1 -> heldTornado1;
            default -> heldTornado2;
        };
    }

    private void setHeldUuid(int slot, @Nullable UUID uuid) {
        switch (slot) {
            case 0 -> heldTornado0 = uuid;
            case 1 -> heldTornado1 = uuid;
            default -> heldTornado2 = uuid;
        }
    }

    private @Nullable TornadoProjectileEntity findHeldTornado(int slot) {
        UUID uuid = getHeldUuid(slot);
        if (uuid == null) return null;

        for (Entity e : this.getWorld().getEntitiesByClass(Entity.class, this.getBoundingBox().expand(48.0), ent -> true)) {
            if (e instanceof TornadoProjectileEntity tornado && uuid.equals(tornado.getUuid())) {
                return tornado;
            }
        }

        return null;
    }

    private Vec3d getHorizontalAim() {
        LivingEntity target = this.getTarget();
        if (target != null) {
            Vec3d dir = target.getPos().subtract(this.getPos());
            dir = new Vec3d(dir.x, 0.0, dir.z);
            if (dir.lengthSquared() > 1.0E-6) {
                return dir.normalize();
            }
        }

        Vec3d fallback = this.getRotationVec(1.0f);
        fallback = new Vec3d(fallback.x, 0.0, fallback.z);
        if (fallback.lengthSquared() > 1.0E-6) {
            return fallback.normalize();
        }

        return new Vec3d(0.0, 0.0, 1.0);
    }

    private void beginCharge() {
        this.chargeTicksLeft = CHARGE_TICKS;
        this.nextShotAge = this.age + SHOT_PERIOD_TICKS;
        this.setChargingAttack(true);

        for (int slot = 0; slot < 3; slot++) {
            TornadoProjectileEntity tornado = ModEntities.TORNADO_PROJECTILE.create(this.getWorld());
            if (tornado == null) continue;

            double angle = slot * (Math.PI * 2.0 / 3.0);
            double radius = 1.15;
            double x = this.getX() + Math.cos(angle) * radius;
            double y = this.getY() + 0.95;
            double z = this.getZ() + Math.sin(angle) * radius;

            tornado.setOwner(this);
            tornado.setArmed(false);
            tornado.setHeldVisual(true);
            tornado.setSlotIndex(slot);
            tornado.setNoClip(true);
            tornado.setVelocity(Vec3d.ZERO);
            tornado.refreshPositionAndAngles(x, y, z, this.getYaw(), this.getPitch());

            this.getWorld().spawnEntity(tornado);
            setHeldUuid(slot, tornado.getUuid());
        }

        this.playSound(SoundEvents.ENTITY_WITCH_AMBIENT, 0.8f, 1.15f);
    }

    private void releaseTornadoes() {
        LivingEntity target = this.getTarget();
        Vec3d forward = getHorizontalAim();
        Vec3d side = new Vec3d(-forward.z, 0.0, forward.x);

        Vec3d baseCenter;
        if (target != null) {
            baseCenter = new Vec3d(target.getX(), target.getY() + 0.10, target.getZ());
        } else {
            baseCenter = this.getPos().add(forward.multiply(6.0));
        }

        Vec3d[] anchors = new Vec3d[] {
                baseCenter.add(side.multiply(2.10)),
                baseCenter.add(side.multiply(-1.05)).add(forward.multiply(1.80)),
                baseCenter.add(side.multiply(-1.05)).add(forward.multiply(-1.80))
        };

        for (int slot = 0; slot < 3; slot++) {
            TornadoProjectileEntity tornado = findHeldTornado(slot);
            if (tornado == null) {
                tornado = ModEntities.TORNADO_PROJECTILE.create(this.getWorld());
                if (tornado == null) continue;

                tornado.setOwner(this);
                this.getWorld().spawnEntity(tornado);
            }

            tornado.setSlotIndex(slot);
            tornado.setNoClip(true);
            tornado.setHeldVisual(false);
            tornado.launchToAnchor(anchors[slot]);

            setHeldUuid(slot, null);
        }

        this.playSound(SoundEvents.ENTITY_PHANTOM_FLAP, 1.0f, 0.75f);
        this.setChargingAttack(false);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Charge", chargeTicksLeft);
        nbt.putInt("NextShotAge", nextShotAge);

        if (heldTornado0 != null) nbt.putUuid("HeldTornado0", heldTornado0);
        if (heldTornado1 != null) nbt.putUuid("HeldTornado1", heldTornado1);
        if (heldTornado2 != null) nbt.putUuid("HeldTornado2", heldTornado2);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.chargeTicksLeft = nbt.getInt("Charge");
        this.nextShotAge = nbt.getInt("NextShotAge");

        this.heldTornado0 = nbt.containsUuid("HeldTornado0") ? nbt.getUuid("HeldTornado0") : null;
        this.heldTornado1 = nbt.containsUuid("HeldTornado1") ? nbt.getUuid("HeldTornado1") : null;
        this.heldTornado2 = nbt.containsUuid("HeldTornado2") ? nbt.getUuid("HeldTornado2") : null;

        this.setChargingAttack(this.chargeTicksLeft > 0);
    }

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

    static final class GroundOrbitGoal extends Goal {
        private final WindWitchEntity mob;
        private float angle;
        private float radius;

        GroundOrbitGoal(WindWitchEntity mob) {
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
            this.radius = 6.5f + r.nextFloat() * 2.5f;
        }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) return;

            double dist = mob.squaredDistanceTo(t);
            if (dist < 16.0) radius = Math.min(9.5f, radius + 0.08f);
            if (dist > 121.0) radius = Math.max(5.0f, radius - 0.06f);

            angle += 0.09f;

            double ox = Math.cos(angle) * radius;
            double oz = Math.sin(angle) * radius;

            double goalX = t.getX() + ox;
            double goalY = t.getY();
            double goalZ = t.getZ() + oz;

            mob.getNavigation().startMovingTo(goalX, goalY, goalZ, 1.0);
            mob.getLookControl().lookAt(t, 20.0f, 20.0f);
        }
    }

    static final class TornadoRangedAttackGoal extends Goal {
        private final WindWitchEntity mob;

        TornadoRangedAttackGoal(WindWitchEntity mob) {
            this.mob = mob;
        }

        @Override
        public boolean canStart() {
            LivingEntity t = mob.getTarget();
            if (t == null || !t.isAlive()) return false;
            if (mob.chargeTicksLeft > 0) return false;
            if (mob.age < mob.nextShotAge) return false;
            return mob.squaredDistanceTo(t) <= (22.0 * 22.0);
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