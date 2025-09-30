package net.seep.odd.entity.car;

import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

import net.seep.odd.particles.OddParticles;
import net.seep.odd.sound.ModSounds;

// Geckolib v4
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.*;

/**
 * RiderCarEntity — two-seat, drift/dash, jump-charge car with radio + ramming.
 */
public class RiderCarEntity extends LivingEntity implements GeoEntity {

    /* ---------- geckolib ---------- */
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private static final String CTRL_BASE   = "base";
    private static final String CTRL_EVENT  = "event";
    private static final String CTRL_STEER  = "steer";

    // base movement/drift/boost
    private static final RawAnimation ANIM_IDLE        = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation ANIM_FWD         = RawAnimation.begin().thenLoop("forward");
    private static final RawAnimation ANIM_REV         = RawAnimation.begin().thenLoop("reverse");
    private static final RawAnimation ANIM_DRIFT_LEFT  = RawAnimation.begin().thenLoop("drift_left");
    private static final RawAnimation ANIM_DRIFT_RIGHT = RawAnimation.begin().thenLoop("drift_right");
    private static final RawAnimation ANIM_BOOST1      = RawAnimation.begin().thenLoop("boost1");
    private static final RawAnimation ANIM_BOOST2      = RawAnimation.begin().thenLoop("boost2");
    private static final RawAnimation ANIM_BOOST3      = RawAnimation.begin().thenLoop("boost3");

    // steering overlay (non-drift)
    private static final RawAnimation ANIM_TURN_LEFT   = RawAnimation.begin().thenLoop("turn_left");
    private static final RawAnimation ANIM_TURN_RIGHT  = RawAnimation.begin().thenLoop("turn_right");

    // one-shots
    private static final RawAnimation ANIM_HONK        = RawAnimation.begin().then("honk",    Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation ANIM_JUMP        = RawAnimation.begin().then("jump",    Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation ANIM_BLOW        = RawAnimation.begin().then("explode", Animation.LoopType.PLAY_ONCE);

    /* ---------- tracker (for HUD/anim state) ---------- */
    private static final TrackedData<Boolean> DRIFTING   = DataTracker.registerData(RiderCarEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> DRIFT_TICK = DataTracker.registerData(RiderCarEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> BOOST_LVL  = DataTracker.registerData(RiderCarEntity.class, TrackedDataHandlerRegistry.INTEGER);

    // radio tracked data (clients use this to manage client-only CarRadioSound)
    private static final TrackedData<Boolean> RADIO_ON   = DataTracker.registerData(RiderCarEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> RADIO_TRACK= DataTracker.registerData(RiderCarEntity.class, TrackedDataHandlerRegistry.INTEGER); // index
    private static final TrackedData<Integer> RADIO_VOL  = DataTracker.registerData(RiderCarEntity.class, TrackedDataHandlerRegistry.INTEGER); // 0..100

    /* ---------- ownership / seats ---------- */
    private UUID owner;
    public UUID getOwner() { return owner; }
    public void setOwner(UUID u) { owner = u; }

    private static final Vec3d DRIVER_SEAT_OFFSET    = new Vec3d(+0.60, -0.30, 0.20); // left seat
    private static final Vec3d PASSENGER_SEAT_OFFSET = new Vec3d(-0.75, -0.30, 0.20); // right seat

    /* ---------- movement ---------- */
    private float  yawDegrees = 0f;
    private double speed      = 0.0;

    private static final double ACCEL_FWD   = 0.030; // slightly softer to help precision
    private static final double ACCEL_REV   = 0.030;
    private static final double DRAG_GROUND = 0.94;
    private static final double DRAG_DRIFT  = 0.90;
    private static final double MAX_FWD     = 0.85;
    private static final double MAX_REV     = 0.45;
    private static final double TURN_BASE   = 2.2;   // more nimble
    private static final double TURN_HIGH   = 1.25;
    private static final double STEP_HEIGHT = 1.1;

    /* ---------- nicer steering feel ---------- */
    private static final double STEER_FILTER      = 0.45; // stronger smoothing
    private static final double TURN_LOWSPD_GAIN  = 3.5;  // much more nimble at crawl
    private static final double TURN_MIDSPD_GAIN  = 2.0;
    private static final double DRIFT_YAW_GAIN    = 1.35;
    private float steerFiltered = 0f;

    /* ---------- drift / dash ---------- */
    private boolean driftHeld = false;
    private int     driftTicks = 0;
    private int     boostLevel = 0;

    private static final int DRIFT_L1 = 18, DRIFT_L2 = 34, DRIFT_L3 = 60;

    private static final double DASH_SPD1 = 1.15;
    private static final double DASH_SPD2 = 1.40;
    private static final double DASH_SPD3 = 1.70;
    private static final int    DASH_T1   = 12, DASH_T2 = 16, DASH_T3 = 20;
    private static final double DASH_DRAG = 0.985;
    private static final double DASH_TURN_SCALE = 0.35;

    private int    dashTicks = 0;
    private double dashMax   = 0.0;

    /* ---------- jump (hold to charge, jump on release) ---------- */
    private boolean chargingJump = false;
    private boolean prevInputJump = false;
    private int     jumpChargeTicks = 0;
    private static final int    JUMP_MAX_TICKS = 18;
    private static final double JUMP_MIN = 0.32;
    private static final double JUMP_MAX = 0.78;

    /* ---------- inputs (server) ---------- */
    private float  inputThrottle = 0f; // -1..1
    private float  inputSteer    = 0f; // -1..1
    private boolean inputDrift   = false;
    private boolean inputHonk    = false;
    private boolean inputJump    = false;

    /* ---------- airborne feel & fall damage easing ---------- */
    private static final double AIR_FALL_DAMP   = 0.90;
    private static final double SPEED_LIFT_COEF = 0.015;
    private static final float  CAR_NO_FALL_UNTIL = 7.0f;
    private static final float  CAR_FALL_MULT     = 0.25f;

    /* ---------- equipment (avoid NPEs) ---------- */
    private final DefaultedList<ItemStack> armorItems = DefaultedList.ofSize(4, ItemStack.EMPTY);
    private final DefaultedList<ItemStack> handItems  = DefaultedList.ofSize(2, ItemStack.EMPTY);

    /* ---------- one-shot detonation guard ---------- */
    private boolean detonating = false;

    /* ---------- ramming (high-speed hit) ---------- */
    private static final double RAM_SPEED_BPS   = 10.0;
    private static final double RAM_AABB_EXPAND = 0.6;
    private static final float  RAM_KB_BASE     = 0.6f;
    private static final float  RAM_KB_PER_BPS  = 0.03f;
    private static final float  RAM_DMG_BASE    = 2.0f;
    private static final float  RAM_DMG_PER_BPS = 0.35f;
    private static final int    RAM_IFRAMES_T   = 8;
    private final Map<UUID, Integer> ramIframes = new HashMap<>();

    /* ---------- radio (SoundEvent, not RegistryEntry) ---------- */
    private static final int RADIO_TICK_PERIOD = 40; // (unused now; playback is client-only)
    private boolean radioOn = false;
    private float   radioVolume = 0.8f; // 0..1
    private int     radioIdx = 0;
    private static final List<SoundEvent> RADIO_TRACKS = new ArrayList<>();
    private static final List<String>     RADIO_TITLES = new ArrayList<>();

    // public API: add tracks (call on BOTH SIDES during mod init)
    public static void addRadioTrack(SoundEvent evt) {
        if (evt == null) return;
        RADIO_TRACKS.add(evt);
        String title = Optional.ofNullable(Registries.SOUND_EVENT.getId(evt))
                .map(Identifier::getPath)
                .orElse("track_" + RADIO_TRACKS.size());
        RADIO_TITLES.add(title);
    }
    public static void addRadioTrack(SoundEvent evt, String title) {
        if (evt == null) return;
        RADIO_TRACKS.add(evt);
        if (title == null || title.isEmpty()) {
            title = Optional.ofNullable(Registries.SOUND_EVENT.getId(evt))
                    .map(Identifier::getPath)
                    .orElse("track_" + RADIO_TRACKS.size());
        }
        RADIO_TITLES.add(title);
    }

    /* ---------- SFX state ---------- */
    private int accSoundCooldown = 60;

    public RiderCarEntity(EntityType<RiderCarEntity> type, World world) {
        super(type, world);
        this.setStepHeight((float) STEP_HEIGHT);
        this.ignoreCameraFrustum = true;
    }
    public static SoundEvent getRadioSoundAt(int idx) {
        if (RADIO_TRACKS.isEmpty()) return null;
        return RADIO_TRACKS.get(MathHelper.clamp(idx, 0, RADIO_TRACKS.size()-1));
    }

    /* ===== attributes ===== */
    public static DefaultAttributeContainer.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_ARMOR, 0.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    /* ===== mounting via right-click ===== */
    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (this.getWorld().isClient) return ActionResult.SUCCESS;

        boolean wasEmpty = !this.hasPassengers();
        if (wasEmpty) {
            player.startRiding(this);
            // driver seat taken -> play start SFX
            if (ModSounds.CAR_START != null) {
                ((ServerWorld)getWorld()).playSound(null, getBlockPos(), ModSounds.CAR_START, SoundCategory.PLAYERS, 1.0f, 1.0f);
            }
            return ActionResult.SUCCESS;
        }
        if (this.getPassengerList().size() == 1 && this.getFirstPassenger() != player) {
            player.startRiding(this);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    public void tryMountDriver(PlayerEntity p) {
        if (this.hasPassengers()) {
            if (getFirstPassenger() != null && !getFirstPassenger().equals(p)) p.startRiding(this);
        } else {
            p.startRiding(this);
            if (!getWorld().isClient && ModSounds.CAR_START != null) {
                ((ServerWorld)getWorld()).playSound(null, getBlockPos(), ModSounds.CAR_START, SoundCategory.PLAYERS, 1.0f, 1.0f);
            }
        }
    }
    public boolean isDriver(PlayerEntity p) {
        return !this.getPassengerList().isEmpty() && this.getPassengerList().get(0) == p;
    }

    /* ===== radio controls (server) ===== */
    public void serverRadioToggle() {
        radioOn = !radioOn;
        syncRadioTracker();
    }
    public void serverRadioSetVolume(float v01) {
        radioVolume = MathHelper.clamp(v01, 0f, 1f);
        syncRadioTracker();
    }
    public void serverRadioNext() {
        if (!RADIO_TRACKS.isEmpty()) {
            radioIdx = (radioIdx + 1) % RADIO_TRACKS.size();
            syncRadioTracker();
        }
    }
    public void serverRadioPrev() {
        if (!RADIO_TRACKS.isEmpty()) {
            radioIdx = (radioIdx - 1 + RADIO_TRACKS.size()) % RADIO_TRACKS.size();
            syncRadioTracker();
        }
    }

    /* ===== rider seats ===== */
    @Override public boolean canAddPassenger(Entity passenger) { return this.getPassengerList().size() < 2; }
    @Override public double getMountedHeightOffset() { return 0.0; }

    @Override
    public void updatePassengerPosition(Entity passenger, PositionUpdater updater) {
        int idx = this.getPassengerList().indexOf(passenger);
        Vec3d seatLocal = (idx == 0) ? DRIVER_SEAT_OFFSET : PASSENGER_SEAT_OFFSET;

        float yaw = this.getYaw();
        double rad = Math.toRadians(yaw);
        double cos = Math.cos(rad), sin = Math.sin(rad);

        Vec3d off = new Vec3d(
                seatLocal.x * cos - seatLocal.z * sin,
                seatLocal.y,
                seatLocal.x * sin + seatLocal.z * cos
        );

        updater.accept(passenger, this.getX() + off.x, this.getY() + off.y, this.getZ() + off.z);

        // glue + no rider fall damage
        passenger.setVelocity(Vec3d.ZERO);
        passenger.velocityDirty = true;
        passenger.fallDistance = 0f;
    }

    /* ===== equipment overrides (no nulls) ===== */
    @Override public Iterable<ItemStack> getArmorItems() { return armorItems; }
    @Override public Iterable<ItemStack> getHandItems()  { return handItems; }
    @Override public ItemStack getEquippedStack(EquipmentSlot slot) {
        return slot.getType() == EquipmentSlot.Type.HAND
                ? handItems.get(slot.getEntitySlotId())
                : armorItems.get(slot.getEntitySlotId());
    }
    @Override public void equipStack(EquipmentSlot slot, ItemStack stack) {
        if (stack == null) stack = ItemStack.EMPTY;
        if (slot.getType() == EquipmentSlot.Type.HAND) {
            ItemStack old = handItems.set(slot.getEntitySlotId(), stack);
            this.onEquipStack(slot, old, stack);
        } else {
            ItemStack old = armorItems.set(slot.getEntitySlotId(), stack);
            this.onEquipStack(slot, old, stack);
        }
    }
    @Override protected void dropEquipment(DamageSource source, int lootingMultiplier, boolean allowDrops) { /* no drops */ }

    /* ===== tracker ===== */
    @Override protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(DRIFTING, false);
        this.dataTracker.startTracking(DRIFT_TICK, 0);
        this.dataTracker.startTracking(BOOST_LVL, 0);

        // radio defaults
        this.dataTracker.startTracking(RADIO_ON, false);
        this.dataTracker.startTracking(RADIO_TRACK, 0);
        this.dataTracker.startTracking(RADIO_VOL, (int)(radioVolume * 100f));
    }
    private void syncDriftState() {
        this.dataTracker.set(DRIFTING, driftHeld);
        this.dataTracker.set(DRIFT_TICK, driftTicks);
        this.dataTracker.set(BOOST_LVL, boostLevel);
    }
    private void syncRadioTracker() {
        this.dataTracker.set(RADIO_ON, radioOn);
        int idx = RADIO_TRACKS.isEmpty() ? 0 : MathHelper.clamp(radioIdx, 0, RADIO_TRACKS.size() - 1);
        this.dataTracker.set(RADIO_TRACK, idx);
        this.dataTracker.set(RADIO_VOL, MathHelper.clamp((int)Math.round(radioVolume * 100f), 0, 100));
    }

    /* ===== NBT ===== */
    @Override public void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) owner = nbt.getUuid("Owner");
        this.yawDegrees = nbt.getFloat("BodyYaw");
        radioOn = nbt.getBoolean("RadioOn");
        radioVolume = nbt.getFloat("RadioVol");
        radioIdx = nbt.getInt("RadioIdx");
        syncRadioTracker(); // make sure tracker reflects loaded values
    }
    @Override public void writeCustomDataToNbt(NbtCompound nbt) {
        if (owner != null) nbt.putUuid("Owner", owner);
        nbt.putFloat("BodyYaw", (float) yawDegrees);
        nbt.putBoolean("RadioOn", radioOn);
        nbt.putFloat("RadioVol", radioVolume);
        nbt.putInt("RadioIdx", radioIdx);
    }
    public int  getDriftTicksClient() { return this.dataTracker.get(DRIFT_TICK); }

    /* ===== main tick ===== */
    @Override
    public void tick() {
        super.tick();

        if (!getWorld().isClient) {
            // decay ramming i-frames
            if (!ramIframes.isEmpty()) {
                Iterator<Map.Entry<UUID,Integer>> it = ramIframes.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<UUID,Integer> e = it.next();
                    int v = e.getValue() - 1;
                    if (v <= 0) it.remove(); else e.setValue(v);
                }
            }

            // Acceleration SFX (forward accel bursts, not spammy)
            if (accSoundCooldown > 0) accSoundCooldown--;
            if (ModSounds.CAR_ACC != null && accSoundCooldown == 0) {
                if (inputThrottle > 0 && speed >= 0.02) {
                    float vol = (float)MathHelper.clamp(0.4 + 0.6 * (speed / MAX_FWD), 0.4, 1.0);
                    ((ServerWorld)getWorld()).playSound(null, getBlockPos(), ModSounds.CAR_ACC, SoundCategory.PLAYERS, vol, 1.0f);
                    accSoundCooldown = 12;
                }
            }

            // Honk (momentary)
            if (inputHonk) {
                getWorld().playSound(null, getBlockPos(), SoundEvents.ITEM_GOAT_HORN_PLAY, SoundCategory.PLAYERS, 1.0f, 1.0f);
                triggerHonk();
                inputHonk = false;
            }

            // === DRIFT charge / release -> DASH ===
            boolean canDrift = Math.abs(inputSteer) > 0.12f && Math.abs(speed) > 0.08;
            if (inputDrift && canDrift) {
                driftHeld = true;
                driftTicks++;
                if (age % 2 == 0)
                    ((ServerWorld)getWorld()).spawnParticles(ParticleTypes.SMOKE, getX(), getY() + 0.2, getZ(), 2, 0.2, 0.0, 0.2, 0.01);
            } else if (driftHeld) {
                int lvl = driftTicks >= DRIFT_L3 ? 3 : driftTicks >= DRIFT_L2 ? 2 : driftTicks >= DRIFT_L1 ? 1 : 0;

                if (lvl > 0) {
                    boostLevel = lvl;
                    dashTicks = (lvl == 3 ? DASH_T3 : (lvl == 2 ? DASH_T2 : DASH_T1));
                    dashMax   = (lvl == 3 ? DASH_SPD3 : (lvl == 2 ? DASH_SPD2 : DASH_SPD1));

                    double rad = Math.toRadians(yawDegrees);
                    Vec3d forward = new Vec3d(-MathHelper.sin((float)rad), 0, MathHelper.cos((float)rad));
                    speed = dashMax;
                    setVelocity(forward.multiply(speed).add(0, getVelocity().y + 0.05, 0));
                    this.velocityDirty = true;

                    ((ServerWorld)getWorld()).spawnParticles(OddParticles.SPECTRAL_BURST, getX(), getBodyY(0.5), getZ(), 20, 0.2, 0.1, 0.2, 0.12);
                    getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 0.8f, 1.2f);
                }
                driftHeld = false;
                driftTicks = 0;
            }

            // === Steering feel ===
            steerFiltered += (inputSteer - steerFiltered) * STEER_FILTER;

            double spdAbs = Math.abs(speed);
            double turnRate = MathHelper.lerp(Math.min(spdAbs / MAX_FWD, 1.0), TURN_BASE, TURN_HIGH);
            if (spdAbs < 0.15)      turnRate *= TURN_LOWSPD_GAIN;
            else if (spdAbs < 0.45) turnRate *= TURN_MIDSPD_GAIN;
            if (driftHeld)          turnRate *= DRIFT_YAW_GAIN;
            if (dashTicks > 0)      turnRate *= DASH_TURN_SCALE;

            yawDegrees += steerFiltered * turnRate * (speed < 0 ? -1 : 1);
            setYaw((float) yawDegrees);
            setBodyYaw((float) yawDegrees);
            setHeadYaw((float) yawDegrees);

            // === Accel / drag ===
            double accel = 0.0;
            if (inputThrottle > 0) accel = ACCEL_FWD * inputThrottle;
            else if (inputThrottle < 0) accel = -ACCEL_REV * (-inputThrottle);

            double drag = driftHeld ? DRAG_DRIFT : (dashTicks > 0 ? DASH_DRAG : DRAG_GROUND);
            speed = (speed + accel) * drag;

            double allowedMax = (speed >= 0 ? MAX_FWD : MAX_REV);
            if (dashTicks > 0) allowedMax = Math.max(allowedMax, dashMax);
            speed = MathHelper.clamp(speed, -allowedMax, allowedMax);

            if (dashTicks > 0 && --dashTicks == 0) {
                dashMax = 0.0;
                boostLevel = 0;
            }

            // === Hold-to-charge jump, jump on release ===
            boolean onGround = this.isOnGround();
            if (inputJump && onGround) {
                chargingJump = true;
                if (jumpChargeTicks < JUMP_MAX_TICKS) jumpChargeTicks++;
            }
            if (prevInputJump && !inputJump) {
                if (chargingJump && onGround) {
                    double t = Math.min(1.0, (double)jumpChargeTicks / JUMP_MAX_TICKS);
                    double vy = MathHelper.lerp(t, JUMP_MIN, JUMP_MAX);
                    this.setVelocity(this.getVelocity().x, vy, this.getVelocity().z);
                    this.velocityDirty = true;
                    ((ServerWorld)getWorld()).playSound(null, getBlockPos(), SoundEvents.ENTITY_HORSE_JUMP, SoundCategory.PLAYERS, 0.9f, 1.1f);
                    triggerJump();
                }
                chargingJump = false;
                jumpChargeTicks = 0;
            }

            // === Floatier airborne feel ===
            if (!onGround) {
                Vec3d v = this.getVelocity();
                if (v.y < 0) {
                    double lift = SPEED_LIFT_COEF * Math.abs(speed);
                    v = new Vec3d(v.x, v.y * AIR_FALL_DAMP + lift, v.z);
                    this.setVelocity(v);
                }
            }

            // Integrate
            double rad = Math.toRadians(yawDegrees);
            Vec3d forward = new Vec3d(-MathHelper.sin((float)rad), 0, MathHelper.cos((float)rad));
            Vec3d vel = forward.multiply(speed).add(0, this.getVelocity().y, 0);
            this.setVelocity(vel);
            this.move(MovementType.SELF, this.getVelocity());

            // landing ease: kill bounce
            if (this.isOnGround() && this.getVelocity().y < 0) {
                this.setVelocity(this.getVelocity().x, 0, this.getVelocity().z);
            }

            // drift skid puffs
            if (driftHeld && this.isOnGround() && age % 3 == 0) {
                ((ServerWorld)getWorld()).spawnParticles(ParticleTypes.CLOUD, getX(), getY() + 0.05, getZ(), 2, 0.25, 0.0, 0.25, 0.0);
            }

            // keep riders safe each tick
            if (this.hasPassengers()) {
                for (var e : this.getPassengerList()) e.fallDistance = 0f;
            }

            // High-speed ramming
            handleRamming(forward);

            syncDriftState();
            prevInputJump = inputJump;
        }
    }

    /* ===== helpers (client-side logic for animation choice) ===== */

    // Tracked-data getters for the anim controller / client radio HUD
    public boolean isDriftingClient() { return this.dataTracker.get(DRIFTING); }
    public int getBoostLevelClient()  { return this.dataTracker.get(BOOST_LVL); }
    public boolean isRadioOnClient()      { return this.dataTracker.get(RADIO_ON); }
    public int     getRadioTrackIndex()   { return this.dataTracker.get(RADIO_TRACK); }
    public float   getRadioVolumeClient() { return this.dataTracker.get(RADIO_VOL) / 100f; }
    public String  getRadioTrackNameClient() {
        if (RADIO_TITLES.isEmpty()) return null;
        int i = MathHelper.clamp(getRadioTrackIndex(), 0, RADIO_TITLES.size() - 1);
        return RADIO_TITLES.get(i);
    }

    private boolean isMovingForwardClient() {
        Vec3d v = this.getVelocity();
        if (v.lengthSquared() < 0.0004) return false;
        double rad = Math.toRadians(this.getYaw());
        Vec3d fwd = new Vec3d(-MathHelper.sin((float)rad), 0, MathHelper.cos((float)rad));
        return v.dotProduct(fwd) > 0.02;
    }
    private boolean isMovingBackwardClient() {
        Vec3d v = this.getVelocity();
        if (v.lengthSquared() < 0.0004) return false;
        double rad = Math.toRadians(this.getYaw());
        Vec3d fwd = new Vec3d(-MathHelper.sin((float)rad), 0, MathHelper.cos((float)rad));
        return v.dotProduct(fwd) < -0.02;
    }
    // Positive => turning LEFT (Minecraft yaw increases CCW)
    private float yawDeltaClient() {
        return MathHelper.wrapDegrees(this.getYaw() - this.prevYaw);
    }

    private void handleRamming(Vec3d forward) {
        double bps = this.getVelocity().horizontalLength() * 20.0;
        if (bps < RAM_SPEED_BPS) return;

        Box box = this.getBoundingBox().expand(RAM_AABB_EXPAND);
        var targets = this.getWorld().getOtherEntities(this, box, e ->
                e instanceof LivingEntity
                        && e.isAlive()
                        && !this.hasPassenger(e)
                        && (owner == null || !(e instanceof PlayerEntity p && p.getUuid().equals(owner)))
        );
        if (targets.isEmpty()) return;

        for (Entity e : targets) {
            LivingEntity le = (LivingEntity) e;
            UUID id = le.getUuid();
            Integer cd = ramIframes.get(id);
            if (cd != null && cd > 0) continue;

            Vec3d to = le.getPos().subtract(this.getPos());
            if (to.lengthSquared() > 0.0001) {
                to = new Vec3d(to.x, 0, to.z).normalize();
                double dot = to.dotProduct(forward.normalize());
                if (dot < -0.25) continue; // behind us
            }

            float over = (float)(bps - RAM_SPEED_BPS);
            float dmg = RAM_DMG_BASE + over * RAM_DMG_PER_BPS + (boostLevel > 0 ? 1.0f * boostLevel : 0f);
            dmg = MathHelper.clamp(dmg, 2.0f, 18.0f);

            DamageSource src = this.getDamageSources().mobAttack(this);
            le.damage(src, dmg);

            float kb = RAM_KB_BASE + (float)bps * RAM_KB_PER_BPS + (boostLevel > 0 ? 0.2f * boostLevel : 0f);
            le.takeKnockback(kb, -forward.x, -forward.z);

            if (this.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.POOF, le.getX(), le.getBodyY(0.5), le.getZ(), 8, 0.2, 0.1, 0.2, 0.02);
                sw.playSound(null, le.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 0.8f, 1.0f);
            }

            ramIframes.put(id, RAM_IFRAMES_T);
        }
    }

    @Override public Arm getMainArm() { return Arm.RIGHT; }

    /* ===== detonate (guarded) ===== */
    public void serverDetonate() {
        if (detonating || !this.isAlive()) return;
        detonating = true;
        triggerExplode();
        if (getWorld() instanceof ServerWorld sw) {
            sw.createExplosion(this, null, null, getX(), getBodyY(0.5), getZ(), 2.8f, false, World.ExplosionSourceType.NONE);
        }
        this.discard();
    }

    // No auto-detonate loops
    @Override public boolean damage(DamageSource source, float amount) { return super.damage(source, amount); }

    /* ===== fall damage easing for the car ===== */
    @Override
    public boolean handleFallDamage(float distance, float damageMultiplier, DamageSource source) {
        if (distance < CAR_NO_FALL_UNTIL) return false;
        return super.handleFallDamage(distance, damageMultiplier * CAR_FALL_MULT, source);
    }
    @Override
    protected int computeFallDamage(float fallDistance, float damageMultiplier) {
        if (fallDistance < CAR_NO_FALL_UNTIL) return 0;
        int dmg = super.computeFallDamage(fallDistance * 0.6f, damageMultiplier * CAR_FALL_MULT);
        return Math.max(0, dmg - 2);
    }

    /* ===== input from RiderNet (RESTORED) ===== */
    public void applyDriverInput(float throttle, float steer, boolean drift, boolean honk, boolean jump) {
        this.inputThrottle = MathHelper.clamp(throttle, -1f, 1f);
        this.inputSteer    = MathHelper.clamp(steer, -1f, 1f);
        this.inputDrift    = drift;
        this.inputHonk    |= honk; // momentary latch for one tick
        this.inputJump     = jump;
    }

    /* ===== misc ===== */
    private static Vec3d rotateXZ(Vec3d v, float yawRad) {
        double c = Math.cos(yawRad), s = Math.sin(yawRad);
        return new Vec3d(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
    }
    @Override public void lookAt(EntityAnchorArgumentType.EntityAnchor anchorPoint, Vec3d target) { /* no-op */ }

    /* ===== geckolib controllers ===== */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // BASE: chooses movement/drift/boost
        controllers.add(new AnimationController<>(this, CTRL_BASE, 5, state -> {
            int boost = getBoostLevelClient();
            if (boost == 3) { state.setAnimation(ANIM_BOOST3); return PlayState.CONTINUE; }
            if (boost == 2) { state.setAnimation(ANIM_BOOST2); return PlayState.CONTINUE; }
            if (boost == 1) { state.setAnimation(ANIM_BOOST1); return PlayState.CONTINUE; }

            if (isDriftingClient()) {
                float yawDelta = yawDeltaClient();
                if (yawDelta > 0.25f)       state.setAnimation(ANIM_DRIFT_LEFT);
                else if (yawDelta < -0.25f) state.setAnimation(ANIM_DRIFT_RIGHT);
                else                        state.setAnimation(ANIM_DRIFT_LEFT);
                return PlayState.CONTINUE;
            }

            if (isMovingForwardClient())  { state.setAnimation(ANIM_FWD); return PlayState.CONTINUE; }
            if (isMovingBackwardClient()) { state.setAnimation(ANIM_REV); return PlayState.CONTINUE; }

            state.setAnimation(ANIM_IDLE);
            return PlayState.CONTINUE;
        }));

        // STEER overlay: when NOT drifting, twist wheels left/right while moving
        controllers.add(new AnimationController<>(this, CTRL_STEER, 2, state -> {
            if (isDriftingClient()) return PlayState.STOP; // drift anim already twists wheels
            if (this.getVelocity().horizontalLengthSquared() < 0.0009) return PlayState.STOP;

            float yawDelta = yawDeltaClient();
            float eps = 0.35f; // ignore micro jitter
            if (yawDelta > eps) {
                state.setAnimation(ANIM_TURN_LEFT);
                return PlayState.CONTINUE;
            } else if (yawDelta < -eps) {
                state.setAnimation(ANIM_TURN_RIGHT);
                return PlayState.CONTINUE;
            }
            return PlayState.STOP;
        }));

        // EVENT one-shots
        controllers.add(new AnimationController<>(this, CTRL_EVENT, state -> PlayState.STOP)
                .triggerableAnim("honk", ANIM_HONK)
                .triggerableAnim("jump", ANIM_JUMP)
                .triggerableAnim("explode", ANIM_BLOW));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }
    public void triggerHonk()    { this.triggerAnim(CTRL_EVENT, "honk"); }
    public void triggerJump()    { this.triggerAnim(CTRL_EVENT, "jump"); }
    public void triggerExplode() { this.triggerAnim(CTRL_EVENT, "explode"); }
}
