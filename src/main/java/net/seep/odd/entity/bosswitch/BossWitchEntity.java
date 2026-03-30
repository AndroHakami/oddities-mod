package net.seep.odd.entity.bosswitch;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

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
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.FrogEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.bosswitch.client.BossWitchHexZoneClient;
import net.seep.odd.entity.flyingwitch.HexProjectileEntity;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class BossWitchEntity extends HostileEntity implements GeoEntity {

    private static final double ARENA_RADIUS = 30.0D;
    private static final float PHASE_TWO_TRIGGER = 0.40f;
    private static final double BASE_MAX_HEALTH = 260.0D;
    private static final double HEALTH_SCALE_CHECK_RADIUS = 100.0D;
    private static final double EXTRA_PLAYER_HEALTH_SCALE = 0.60D;
    private static final int HEALTH_SCALE_CHECK_INTERVAL = 20;

    private static final int HEX_ZONE_WAVE_COUNT = 3;
    private static final int HEX_ZONE_INITIAL_CHARGE_TICKS = 30;
    private static final int HEX_ZONE_REPEAT_CHARGE_TICKS = 15;
    private static final int HEX_ZONE_TOTAL_TICKS = 67;
    private static final int HEX_ZONE_COOLDOWN = 58;
    private static final float HEX_ZONE_DAMAGE = 8.0f;
    private static final double HEX_ZONE_ARENA_PADDING = 1.5D;

    private static final int GOLEM_RECALL_CAST_TICK = 18;
    private static final int GOLEM_RECALL_TOTAL_TICKS = 36;
    private static final int GOLEM_RECALL_COOLDOWN = 54;

    private static final int FALSE_MEMORY_SPAWN_TICKS = 100;

    private static final RawAnimation IDLE               = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation SPAWN              = RawAnimation.begin().thenPlay("spawn");
    private static final RawAnimation FLYING_DASH        = RawAnimation.begin().thenPlay("flying_dash");
    private static final RawAnimation FLYING_DASH_TWIRL  = RawAnimation.begin().thenPlay("flying_dash_twirl");
    private static final RawAnimation SKULLS_CAST        = RawAnimation.begin().thenPlay("skulls_cast");
    private static final RawAnimation HEX_CAST           = RawAnimation.begin().thenPlay("hex_cast");
    private static final RawAnimation FROG_CAST          = RawAnimation.begin().thenPlay("frog_cast");
    private static final RawAnimation FROG_TRANSFORM     = RawAnimation.begin().thenLoop("frog_transform");
    private static final RawAnimation SNARE_CAST         = RawAnimation.begin().thenPlay("snare_cast");
    private static final RawAnimation SPIKE_CAST         = RawAnimation.begin().thenPlay("spike_cast");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final TrackedData<Integer> ANIM_STATE =
            DataTracker.registerData(BossWitchEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> PHASE_TWO =
            DataTracker.registerData(BossWitchEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> SPAWNING =
            DataTracker.registerData(BossWitchEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final byte STATUS_HEX_ZONE_LONG_WAVE_A = 81;
    private static final byte STATUS_HEX_ZONE_LONG_WAVE_B = 82;
    private static final byte STATUS_HEX_ZONE_SHORT_WAVE_A = 83;
    private static final byte STATUS_HEX_ZONE_SHORT_WAVE_B = 84;

    private enum AnimState {
        IDLE,
        SPAWN,
        FLYING_DASH,
        FLYING_DASH_TWIRL,
        SKULLS_CAST,
        HEX_CAST,
        FROG_CAST,
        FROG_TRANSFORM,
        SNARE_CAST,
        SPIKE_CAST
    }

    private enum AttackKind {
        NONE,
        SKULLS,
        HEX,
        FROGS,
        FROG_TRANSFORM,
        SNARE,
        SPIKE,
        HEX_ZONES,
        GOLEM_RECALL
    }

    private static final class TransformingFrogData {
        final UUID frogUuid;
        int ticksLeft;

        TransformingFrogData(UUID frogUuid, int ticksLeft) {
            this.frogUuid = frogUuid;
            this.ticksLeft = ticksLeft;
        }
    }

    private final ServerBossBar bossBar = new ServerBossBar(
            Text.literal("Boss Witch"),
            BossBar.Color.PURPLE,
            BossBar.Style.PROGRESS
    );

    private @Nullable BlockPos homePos;
    private @Nullable UUID bossGolemUuid;

    private float orbitAngle = 0.0f;
    private float orbitRadius = 11.0f;
    private int orbitDirection = 1;

    private int moveAnimTicks = 0;
    private int dashCooldown = 0;

    private AttackKind currentAttack = AttackKind.NONE;
    private int attackTicks = 0;
    private int attackCooldown = 60;

    private @Nullable UUID heldHexUuid = null;
    private int cachedNearbyScalingPlayers = -1;
    private double cachedScaledMaxHealth = BASE_MAX_HEALTH;

    private int falseMemorySpawnTicks = 0;

    private final List<TransformingFrogData> transformingFrogs = new ArrayList<>();

    public BossWitchEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 100;

        this.moveControl = new FlightMoveControl(this, 20, true);
        this.setNoGravity(true);
        this.noClip = false;
        this.setPersistent();
        this.bossBar.setVisible(true);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, BASE_MAX_HEALTH)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.30D)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 0.55D)
                .add(EntityAttributes.GENERIC_ARMOR, 6.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0D)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 8.0D);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(ANIM_STATE, AnimState.IDLE.ordinal());
        this.dataTracker.startTracking(PHASE_TWO, false);
        this.dataTracker.startTracking(SPAWNING, false);
    }

    public boolean isPhaseTwo() {
        return this.dataTracker.get(PHASE_TWO);
    }

    private void setPhaseTwo(boolean value) {
        this.dataTracker.set(PHASE_TWO, value);
    }

    public boolean isFalseMemorySpawning() {
        return this.dataTracker.get(SPAWNING);
    }

    private void setFalseMemorySpawning(boolean value) {
        this.dataTracker.set(SPAWNING, value);
    }

    public void beginFalseMemorySpawn(BlockPos arenaHome) {
        this.homePos = arenaHome.toImmutable();
        this.setFalseMemorySpawning(true);
        this.falseMemorySpawnTicks = FALSE_MEMORY_SPAWN_TICKS;

        this.currentAttack = AttackKind.NONE;
        this.attackTicks = 0;
        this.attackCooldown = FALSE_MEMORY_SPAWN_TICKS + 10;
        this.moveAnimTicks = 0;
        this.dashCooldown = 20;
        this.heldHexUuid = null;
        this.setTarget(null);
        this.setVelocity(Vec3d.ZERO);
        this.fallDistance = 0.0f;

        setAnimState(AnimState.SPAWN);
    }

    public int getBossPhase() {
        return isPhaseTwo() ? 2 : 1;
    }

    private boolean isPhaseTwoCombatActive() {
        if (!isPhaseTwo()) return false;

        BossGolemEntity golem = findBossGolem();
        return golem != null && golem.isActiveBody();
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        return new BirdNavigation(this, world);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new HomeOrbitGoal(this));
        this.goalSelector.add(2, new PhaseAttackGoal(this));

        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.hasNoGravity()) {
            this.setNoGravity(true);
        }

        if (this.homePos == null) {
            this.homePos = this.getBlockPos();
        }

        if (this.moveAnimTicks > 0 && this.currentAttack == AttackKind.NONE && !isFalseMemorySpawning()) {
            this.moveAnimTicks--;
            if (this.moveAnimTicks <= 0 && (!isPhaseTwo() || isPhaseTwoCombatActive())) {
                setAnimState(AnimState.IDLE);
            }
        }

        if (this.attackCooldown > 0) this.attackCooldown--;
        if (this.dashCooldown > 0) this.dashCooldown--;

        if (!this.getWorld().isClient) {
            this.bossBar.setName(this.getDisplayName());
            if (this.age <= 1 || this.age % HEALTH_SCALE_CHECK_INTERVAL == 0) {
                updateScaledMaxHealth();
            }
            this.bossBar.setPercent(MathHelper.clamp(this.getHealth() / this.getMaxHealth(), 0.0f, 1.0f));

            ensureBossGolemExists();
            validateTarget();

            if (isFalseMemorySpawning()) {
                this.setTarget(null);
                this.setVelocity(Vec3d.ZERO);
                this.fallDistance = 0.0f;
                setAnimState(AnimState.SPAWN);

                if (this.falseMemorySpawnTicks > 0) {
                    this.falseMemorySpawnTicks--;
                }

                if (this.falseMemorySpawnTicks <= 0) {
                    this.setFalseMemorySpawning(false);
                    this.falseMemorySpawnTicks = 0;
                    this.attackCooldown = Math.max(this.attackCooldown, 20);
                    setAnimState(AnimState.IDLE);
                }

                tickTransformingFrogs();
                return;
            }

            if (!isPhaseTwo() && this.getHealth() <= this.getMaxHealth() * PHASE_TWO_TRIGGER) {
                beginPhaseTwo();
            }

            if (isPhaseTwo() && !isPhaseTwoCombatActive()) {
                this.currentAttack = AttackKind.NONE;
                this.attackTicks = 0;
                this.setVelocity(Vec3d.ZERO);
                setAnimState(AnimState.FROG_TRANSFORM);
            } else {
                if (this.currentAttack == AttackKind.NONE && this.attackCooldown <= 0 && shouldRecallBossGolem()) {
                    startAttack(AttackKind.GOLEM_RECALL);
                }

                tickAttackLogic();
                tickHeldHex();
            }

            tickTransformingFrogs();
        }
    }

    private void beginPhaseTwo() {
        if (isPhaseTwo()) return;

        setPhaseTwo(true);
        this.currentAttack = AttackKind.NONE;
        this.attackTicks = 0;
        this.attackCooldown = 40;
        this.heldHexUuid = null;

        this.heal(this.getMaxHealth());
        setAnimState(AnimState.FROG_TRANSFORM);

        BossGolemEntity golem = findBossGolem();
        if (golem != null) {
            golem.beginAwakening();
        }
    }

    private boolean shouldRecallBossGolem() {
        if (!isPhaseTwoCombatActive()) return false;

        BossGolemEntity golem = findBossGolem();
        return golem != null && golem.needsRecallToHome();
    }

    private void recallBossGolem() {
        BossGolemEntity golem = findBossGolem();
        if (golem != null) {
            golem.recallToHome();
        }
    }

    private void ensureBossGolemExists() {
        if (!(this.getWorld() instanceof ServerWorld)) return;
        if (this.bossGolemUuid != null && findBossGolem() != null) return;

        BossGolemEntity golem = ModEntities.BOSS_GOLEM.create(this.getWorld());
        if (golem == null) return;

        BlockPos spawnBase = this.homePos != null ? this.homePos : this.getBlockPos();
        double gx = spawnBase.getX() + 4.5D;
        double gz = spawnBase.getZ() + 0.5D;
        double gy = findGroundYAt(gx, spawnBase.getY() + 8.0D, gz, spawnBase.getY());

        golem.setOwnerUuid(this.getUuid());
        golem.setHomePos(spawnBase);
        golem.refreshPositionAndAngles(gx, gy, gz, 0.0f, 0.0f);

        this.getWorld().spawnEntity(golem);
        this.bossGolemUuid = golem.getUuid();
    }

    private @Nullable BossGolemEntity findBossGolem() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return null;
        if (this.bossGolemUuid == null) return null;

        Entity e = sw.getEntity(this.bossGolemUuid);
        return e instanceof BossGolemEntity golem ? golem : null;
    }

    private void validateTarget() {
        if (isFalseMemorySpawning()) {
            this.setTarget(null);
            return;
        }

        LivingEntity target = this.getTarget();
        if (target == null) {
            if (this.age % 10 == 0) {
                this.setTarget(findArenaTarget());
            }
            return;
        }

        if (!target.isAlive()) {
            this.setTarget(null);
            return;
        }

        if (target instanceof PlayerEntity player && (player.isCreative() || player.isSpectator())) {
            this.setTarget(null);
            return;
        }

        if (!isInsideArena(target)) {
            this.setTarget(null);
        }
    }

    private @Nullable LivingEntity findArenaTarget() {
        PlayerEntity best = null;
        double bestD2 = Double.MAX_VALUE;

        for (PlayerEntity player : this.getWorld().getPlayers()) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()) continue;
            if (!isInsideArena(player)) continue;

            double d2 = this.squaredDistanceTo(player);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = player;
            }
        }

        return best;
    }

    private void updateScaledMaxHealth() {
        EntityAttributeInstance maxHealthAttr = this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (maxHealthAttr == null) return;

        int nearbyPlayers = countNearbyScalingPlayers();
        double scaledMaxHealth = BASE_MAX_HEALTH * (1.0D + Math.max(0, nearbyPlayers - 1) * EXTRA_PLAYER_HEALTH_SCALE);

        if (this.cachedNearbyScalingPlayers == nearbyPlayers && Math.abs(this.cachedScaledMaxHealth - scaledMaxHealth) < 0.01D) {
            return;
        }

        double oldMaxHealth = Math.max(1.0D, this.getMaxHealth());
        float oldHealthPercent = MathHelper.clamp(this.getHealth() / (float) oldMaxHealth, 0.0f, 1.0f);

        this.cachedNearbyScalingPlayers = nearbyPlayers;
        this.cachedScaledMaxHealth = scaledMaxHealth;
        maxHealthAttr.setBaseValue(scaledMaxHealth);
        this.setHealth(MathHelper.clamp((float) (scaledMaxHealth * oldHealthPercent), 0.0f, (float) scaledMaxHealth));
    }

    private int countNearbyScalingPlayers() {
        Vec3d center = getArenaCenter();
        double radiusSq = HEALTH_SCALE_CHECK_RADIUS * HEALTH_SCALE_CHECK_RADIUS;
        int count = 0;

        for (PlayerEntity player : this.getWorld().getPlayers()) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()) continue;
            if (player.squaredDistanceTo(center.x, center.y, center.z) > radiusSq) continue;
            count++;
        }

        return Math.max(1, count);
    }

    private boolean isInsideArena(Entity entity) {
        if (this.homePos == null) return true;
        return horizontalDistToHomeSq(entity.getX(), entity.getZ()) <= (ARENA_RADIUS * ARENA_RADIUS);
    }

    private double horizontalDistToHomeSq(double x, double z) {
        if (this.homePos == null) return 0.0D;
        double dx = x - (this.homePos.getX() + 0.5D);
        double dz = z - (this.homePos.getZ() + 0.5D);
        return dx * dx + dz * dz;
    }

    private double findGroundYAt(double x, double startY, double z, double fallbackY) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int top = Math.min(this.getWorld().getTopY() - 1, MathHelper.floor(startY));
        int bottom = this.getWorld().getBottomY();

        int ix = MathHelper.floor(x);
        int iz = MathHelper.floor(z);

        for (int y = top; y >= bottom; y--) {
            pos.set(ix, y, iz);
            if (this.getWorld().getBlockState(pos).isSolidBlock(this.getWorld(), pos)) {
                return y + 1.0D;
            }
        }

        return Math.floor(fallbackY);
    }

    public boolean canAttackNow() {
        return !isFalseMemorySpawning()
                && this.currentAttack == AttackKind.NONE
                && this.attackCooldown <= 0
                && this.getTarget() != null
                && (!isPhaseTwo() || isPhaseTwoCombatActive());
    }

    public void maybeStartDashAnimation(boolean twirl) {
        if (this.currentAttack != AttackKind.NONE) return;
        if (isFalseMemorySpawning()) return;
        if (isPhaseTwo() && !isPhaseTwoCombatActive()) return;
        if (this.moveAnimTicks > 0) return;

        if (twirl) {
            this.moveAnimTicks = 59;
            setAnimState(AnimState.FLYING_DASH_TWIRL);
        } else {
            this.moveAnimTicks = 22;
            setAnimState(AnimState.FLYING_DASH);
        }
    }

    private void tickAttackLogic() {
        if (this.currentAttack == AttackKind.NONE) return;

        this.attackTicks++;

        LivingEntity target = this.getTarget();
        if (target != null) {
            this.getLookControl().lookAt(target, 30.0f, 30.0f);
        }

        switch (this.currentAttack) {
            case HEX -> {
                if (this.attackTicks == 1) {
                    setAnimState(AnimState.HEX_CAST);
                    beginHexCharge();
                }
                if (this.attackTicks == 25) {
                    releaseHex();
                }
                if (this.attackTicks >= 40) {
                    finishAttack(26);
                }
            }

            case SKULLS -> {
                if (this.attackTicks == 1) {
                    setAnimState(AnimState.SKULLS_CAST);
                    this.playSound(SoundEvents.ENTITY_WITCH_CELEBRATE, 1.0f, 0.9f);
                }
                if (this.attackTicks == 48) {
                    setAnimState(AnimState.IDLE);
                }
                if (this.attackTicks >= 25 && this.attackTicks <= 125 && ((this.attackTicks - 25) % 20 == 0)) {
                    summonFlamingSkull(target);
                }
                if (this.attackTicks >= 140) {
                    finishAttack(68);
                }
            }

            case FROGS -> {
                if (this.attackTicks == 1) {
                    setAnimState(AnimState.FROG_CAST);
                    this.playSound(SoundEvents.ENTITY_WITCH_AMBIENT, 1.0f, 0.8f);
                }
                if (this.attackTicks == 32) {
                    summonFrogs(target);
                }
                if (this.attackTicks >= 48) {
                    this.currentAttack = AttackKind.FROG_TRANSFORM;
                    this.attackTicks = 0;
                    setAnimState(AnimState.FROG_TRANSFORM);
                    this.setVelocity(Vec3d.ZERO);
                }
            }

            case FROG_TRANSFORM -> {
                this.setVelocity(Vec3d.ZERO);
                if (this.attackTicks >= 120) {
                    finishAttack(90);
                }
            }

            case SNARE -> {
                if (this.attackTicks == 1) {
                    setAnimState(AnimState.SNARE_CAST);
                    this.playSound(SoundEvents.ENTITY_WITCH_DRINK, 0.85f, 0.75f);
                }
                if (this.attackTicks == 24 && target != null) {
                    BossWitchSnareEntity snare = ModEntities.BOSS_WITCH_SNARE.create(this.getWorld());
                    if (snare != null) {
                        snare.setOwnerUuid(this.getUuid());
                        snare.refreshPositionAndAngles(target.getX(), target.getY() + 0.05D, target.getZ(), 0.0f, 0.0f);
                        this.getWorld().spawnEntity(snare);
                    }
                }
                if (this.attackTicks >= 40) {
                    finishAttack(28);
                }
            }

            case SPIKE -> {
                if (this.attackTicks == 1) {
                    setAnimState(AnimState.SPIKE_CAST);
                    this.playSound(SoundEvents.BLOCK_ROOTED_DIRT_BREAK, 1.0f, 0.72f);
                }
                if (this.attackTicks >= 38) {
                    finishAttack(36);
                }
            }

            case HEX_ZONES -> {
                if (this.attackTicks == 1) {
                    setAnimState(AnimState.HEX_CAST);
                    this.playSound(SoundEvents.ENTITY_WITCH_AMBIENT, 1.0f, 0.65f);
                    sendHexZoneTelegraph(0, true);
                }

                if (this.attackTicks == 31) {
                    detonateHexZones(0);
                    sendHexZoneTelegraph(1, false);
                }

                if (this.attackTicks == 46) {
                    detonateHexZones(1);
                    sendHexZoneTelegraph(0, false);
                }

                if (this.attackTicks == 61) {
                    detonateHexZones(0);
                }

                if (this.attackTicks >= HEX_ZONE_TOTAL_TICKS) {
                    finishAttack(HEX_ZONE_COOLDOWN);
                }
            }

            case GOLEM_RECALL -> {
                if (this.attackTicks == 1) {
                    setAnimState(AnimState.HEX_CAST);
                    this.playSound(SoundEvents.ENTITY_WITCH_AMBIENT, 0.95f, 0.70f);
                }
                if (this.attackTicks == GOLEM_RECALL_CAST_TICK) {
                    recallBossGolem();
                    this.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.85f);
                }
                if (this.attackTicks >= GOLEM_RECALL_TOTAL_TICKS) {
                    finishAttack(GOLEM_RECALL_COOLDOWN);
                }
            }

            default -> {
            }
        }
    }

    public void chooseAndStartAttack() {
        LivingEntity target = this.getTarget();
        if (target == null) return;
        if (isFalseMemorySpawning()) return;
        if (isPhaseTwo() && !isPhaseTwoCombatActive()) return;

        if (shouldRecallBossGolem()) {
            startAttack(AttackKind.GOLEM_RECALL);
            return;
        }

        double d2 = this.squaredDistanceTo(target);
        float healthPct = this.getHealth() / this.getMaxHealth();
        boolean closeForSnare = d2 <= (18.0D * 18.0D);
        boolean inFrogRange = d2 <= (22.0D * 22.0D);

        List<AttackKind> pool = new ArrayList<>();

        pool.add(AttackKind.HEX);
        pool.add(AttackKind.HEX);
        pool.add(AttackKind.HEX);
        pool.add(AttackKind.SKULLS);
        pool.add(AttackKind.SKULLS);
        pool.add(AttackKind.HEX_ZONES);
        pool.add(AttackKind.HEX_ZONES);

        if (isPhaseTwo()) {
            pool.add(AttackKind.HEX_ZONES);
            pool.add(AttackKind.HEX_ZONES);
            pool.add(AttackKind.HEX_ZONES);
        }

        if (closeForSnare) {
            pool.add(AttackKind.SNARE);
            pool.add(AttackKind.SNARE);
        }

        if (inFrogRange && this.random.nextInt(6) == 0) {
            pool.add(AttackKind.FROGS);
        }

        if (healthPct <= 0.66f) {
            pool.add(AttackKind.HEX);
            pool.add(AttackKind.HEX_ZONES);
            pool.add(AttackKind.HEX_ZONES);
            if (closeForSnare) pool.add(AttackKind.SNARE);
        }

        if (healthPct <= 0.33f && inFrogRange && this.random.nextInt(3) == 0) {
            pool.add(AttackKind.FROGS);
        }

        if (isPhaseTwo()) {
            pool.add(AttackKind.HEX);
            pool.add(AttackKind.SKULLS);
            pool.add(AttackKind.HEX_ZONES);
            pool.add(AttackKind.HEX_ZONES);
            pool.add(AttackKind.HEX_ZONES);
            if (closeForSnare) {
                pool.add(AttackKind.SNARE);
            }
        }

        startAttack(pool.get(this.random.nextInt(pool.size())));
    }

    private void startAttack(AttackKind kind) {
        this.currentAttack = kind;
        this.attackTicks = 0;
        this.setVelocity(Vec3d.ZERO);
    }

    private void finishAttack(int cooldownTicks) {
        this.currentAttack = AttackKind.NONE;
        this.attackTicks = 0;
        this.attackCooldown = cooldownTicks;
        this.heldHexUuid = null;

        if (isFalseMemorySpawning()) {
            setAnimState(AnimState.SPAWN);
        } else if (!isPhaseTwo() || isPhaseTwoCombatActive()) {
            setAnimState(AnimState.IDLE);
        } else {
            setAnimState(AnimState.FROG_TRANSFORM);
        }
    }

    public Vec3d getArenaCenter() {
        BlockPos centerPos = this.homePos != null ? this.homePos : this.getBlockPos();
        return Vec3d.ofCenter(centerPos);
    }

    private void sendHexZoneTelegraph(int hazardParity, boolean longCharge) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        byte status = longCharge
                ? (hazardParity == 0 ? STATUS_HEX_ZONE_LONG_WAVE_A : STATUS_HEX_ZONE_LONG_WAVE_B)
                : (hazardParity == 0 ? STATUS_HEX_ZONE_SHORT_WAVE_A : STATUS_HEX_ZONE_SHORT_WAVE_B);

        sw.sendEntityStatus(this, status);
        sw.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.HOSTILE, 1.0f, longCharge ? 0.82f : (hazardParity == 0 ? 1.15f : 0.9f));
    }

    private void detonateHexZones(int hazardParity) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        Vec3d center = getArenaCenter();

        for (PlayerEntity player : sw.getPlayers()) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()) continue;
            if (!isInsideArena(player)) continue;
            if (!isHazardousHexTile(player.getX(), player.getZ(), hazardParity)) continue;

            player.damage(sw.getDamageSources().magic(), HEX_ZONE_DAMAGE);
            player.addVelocity(0.0D, 0.20D, 0.0D);
            player.velocityModified = true;
        }

        sw.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.1f, 0.55f + this.random.nextFloat() * 0.12f);
    }

    private boolean isHazardousHexTile(double x, double z, int hazardParity) {
        Vec3d center = getArenaCenter();
        double dx = x - center.x;
        double dz = z - center.z;
        double radiusSq = (ARENA_RADIUS - HEX_ZONE_ARENA_PADDING) * (ARENA_RADIUS - HEX_ZONE_ARENA_PADDING);
        if ((dx * dx + dz * dz) > radiusSq) return false;

        int tileX = MathHelper.floor(x - Math.floor(center.x));
        int tileZ = MathHelper.floor(z - Math.floor(center.z));
        return ((tileX + tileZ) & 1) == hazardParity;
    }

    @Environment(EnvType.CLIENT)
    private void handleHexZoneWaveClient(int hazardParity, int chargeTicks) {
        BossWitchHexZoneClient.spawnWave(this.getId(), getArenaCenter(), ARENA_RADIUS - HEX_ZONE_ARENA_PADDING, hazardParity, chargeTicks);
    }

    @Override
    public void handleStatus(byte status) {
        if (status == STATUS_HEX_ZONE_LONG_WAVE_A || status == STATUS_HEX_ZONE_LONG_WAVE_B
                || status == STATUS_HEX_ZONE_SHORT_WAVE_A || status == STATUS_HEX_ZONE_SHORT_WAVE_B) {
            if (this.getWorld().isClient) {
                int hazardParity = (status == STATUS_HEX_ZONE_LONG_WAVE_B || status == STATUS_HEX_ZONE_SHORT_WAVE_B) ? 1 : 0;
                int chargeTicks = (status == STATUS_HEX_ZONE_LONG_WAVE_A || status == STATUS_HEX_ZONE_LONG_WAVE_B)
                        ? HEX_ZONE_INITIAL_CHARGE_TICKS
                        : HEX_ZONE_REPEAT_CHARGE_TICKS;
                handleHexZoneWaveClient(hazardParity, chargeTicks);
            }
            return;
        }

        super.handleStatus(status);
    }

    private void summonFrogs(@Nullable LivingEntity target) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        if (target == null) return;

        int count = frogSummonCount();
        for (int i = 0; i < count; i++) {
            FrogEntity frog = EntityType.FROG.create(sw);
            if (frog == null) continue;

            double angle = (Math.PI * 2.0D / count) * i + (this.random.nextDouble() * 0.35D);
            double radius = 1.8D + this.random.nextDouble() * 1.1D;
            double x = target.getX() + Math.cos(angle) * radius;
            double z = target.getZ() + Math.sin(angle) * radius;
            double y = target.getY();

            frog.refreshPositionAndAngles(x, y, z, this.random.nextFloat() * 360.0f, 0.0f);
            frog.setVelocity(
                    (this.random.nextDouble() - 0.5D) * 0.18D,
                    0.22D + this.random.nextDouble() * 0.08D,
                    (this.random.nextDouble() - 0.5D) * 0.18D
            );
            frog.velocityModified = true;

            sw.spawnEntity(frog);
            this.transformingFrogs.add(new TransformingFrogData(frog.getUuid(), 136));
        }
    }

    private int frogSummonCount() {
        float pct = this.getHealth() / this.getMaxHealth();
        if (pct <= 0.33f) return 5;
        if (pct <= 0.66f) return 4;
        return 3;
    }

    private void tickTransformingFrogs() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        if (this.transformingFrogs.isEmpty()) return;

        Iterator<TransformingFrogData> it = this.transformingFrogs.iterator();
        while (it.hasNext()) {
            TransformingFrogData data = it.next();
            Entity frogEntity = sw.getEntity(data.frogUuid);

            if (!(frogEntity instanceof FrogEntity frog) || !frog.isAlive()) {
                it.remove();
                continue;
            }

            data.ticksLeft--;

            if (data.ticksLeft <= 120 && (this.age % 3 == 0)) {
                sw.spawnParticles(net.minecraft.particle.ParticleTypes.WITCH,
                        frog.getX(), frog.getBodyY(0.5D), frog.getZ(),
                        6, 0.22D, 0.18D, 0.22D, 0.01D);
            }

            if (data.ticksLeft <= 0) {
                Entity falseFrog = ModEntities.FALSE_FROG.create(sw);
                if (falseFrog != null) {
                    falseFrog.refreshPositionAndAngles(frog.getX(), frog.getY(), frog.getZ(), frog.getYaw(), frog.getPitch());
                    sw.spawnEntity(falseFrog);
                }
                frog.discard();
                it.remove();
            }
        }
    }

    private void summonFlamingSkull(@Nullable LivingEntity target) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        FlamingSkullEntity skull = ModEntities.FLAMING_SKULL.create(sw);
        if (skull == null) return;

        Vec3d dir = getAimDirection();
        if (target != null) {
            Vec3d spawnGuess = this.getPos().add(0.0D, 1.5D, 0.0D);
            Vec3d to = target.getPos().add(0.0D, target.getStandingEyeHeight() * 0.55D, 0.0D).subtract(spawnGuess);
            if (to.lengthSquared() > 1.0E-6) dir = to.normalize();
        }

        double angle = this.random.nextDouble() * Math.PI * 2.0D;
        double radius = 1.3D + this.random.nextDouble() * 2.3D;
        double height = 0.45D + this.random.nextDouble() * 1.5D;

        Vec3d spawn = this.getPos().add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);

        skull.refreshPositionAndAngles(spawn.x, spawn.y, spawn.z, this.getYaw(), this.getPitch());
        skull.setOwnerUuid(this.getUuid());
        if (target != null) skull.setTargetUuid(target.getUuid());
        skull.setVelocity(dir.multiply(0.14D));

        sw.spawnEntity(skull);
        this.playSound(SoundEvents.ENTITY_BLAZE_SHOOT, 0.8f, 0.75f + this.random.nextFloat() * 0.15f);
    }

    private Vec3d getAimDirection() {
        LivingEntity target = this.getTarget();
        if (target != null) {
            Vec3d from = this.getPos().add(0.0D, this.getStandingEyeHeight() * 0.8D, 0.0D);
            Vec3d to = target.getPos().add(0.0D, target.getStandingEyeHeight() * 0.6D, 0.0D);
            Vec3d dir = to.subtract(from);
            if (dir.lengthSquared() > 1.0E-6) return dir.normalize();
        }
        return this.getRotationVec(1.0f).normalize();
    }

    private @Nullable HexProjectileEntity findHeldHex() {
        if (this.heldHexUuid == null) return null;

        for (Entity e : this.getWorld().getEntitiesByClass(Entity.class, this.getBoundingBox().expand(24.0D), ent -> true)) {
            if (e instanceof HexProjectileEntity hex && this.heldHexUuid.equals(hex.getUuid())) {
                return hex;
            }
        }
        return null;
    }

    private void beginHexCharge() {
        HexProjectileEntity hex = ModEntities.HEX_PROJECTILE.create(this.getWorld());
        if (hex == null) return;

        hex.setOwner(this);
        hex.setArmed(false);
        hex.setNoClip(true);
        hex.setVelocity(Vec3d.ZERO);
        hex.setHeldVisual(true);

        Vec3d dir = getAimDirection();
        Vec3d pos = this.getPos()
                .add(0.0D, this.getStandingEyeHeight() * 0.65D, 0.0D)
                .add(dir.multiply(0.9D));

        hex.refreshPositionAndAngles(pos.x, pos.y, pos.z, this.getYaw(), this.getPitch());
        this.getWorld().spawnEntity(hex);
        this.heldHexUuid = hex.getUuid();

        this.playSound(SoundEvents.ENTITY_WITCH_AMBIENT, 0.9f, 0.9f);
    }

    private void tickHeldHex() {
        if (this.currentAttack != AttackKind.HEX) return;
        if (this.attackTicks <= 0 || this.attackTicks >= 25) return;

        HexProjectileEntity held = findHeldHex();
        if (held == null) return;

        Vec3d dir = getAimDirection();
        Vec3d holdPos = this.getPos()
                .add(0.0D, this.getStandingEyeHeight() * 0.65D, 0.0D)
                .add(dir.multiply(0.9D));

        held.setNoClip(true);
        held.setArmed(false);
        held.setVelocity(Vec3d.ZERO);
        held.setPosition(holdPos.x, holdPos.y, holdPos.z);
        held.setYaw(this.getYaw());
        held.setPitch(this.getPitch());
    }

    private void releaseHex() {
        HexProjectileEntity hex = findHeldHex();
        if (hex == null) {
            hex = ModEntities.HEX_PROJECTILE.create(this.getWorld());
            if (hex == null) return;
            hex.setOwner(this);
            this.getWorld().spawnEntity(hex);
        }

        Vec3d dir = getAimDirection();
        Vec3d spawn = this.getPos()
                .add(0.0D, this.getStandingEyeHeight() * 0.65D, 0.0D)
                .add(dir.multiply(1.1D));

        hex.setPosition(spawn.x, spawn.y, spawn.z);
        hex.setNoClip(false);
        hex.setHeldVisual(false);
        hex.setArmed(true);
        hex.setVelocity(dir.multiply(0.33D));
        hex.powerX = dir.x * 0.1D;
        hex.powerY = dir.y * 0.1D;
        hex.powerZ = dir.z * 0.1D;

        this.playSound(SoundEvents.ENTITY_WITCH_THROW, 1.0f, 1.0f);
        this.heldHexUuid = null;
    }

    private void setAnimState(AnimState state) {
        this.dataTracker.set(ANIM_STATE, state.ordinal());
    }

    private AnimState getAnimState() {
        int idx = MathHelper.clamp(this.dataTracker.get(ANIM_STATE), 0, AnimState.values().length - 1);
        return AnimState.values()[idx];
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public void checkDespawn() {
    }

    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        this.bossBar.addPlayer(player);
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        super.onStoppedTrackingBy(player);
        this.bossBar.removePlayer(player);
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);
        BossGolemEntity golem = findBossGolem();
        if (golem != null) {
            golem.discard();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        this.bossBar.clearPlayers();
        BossGolemEntity golem = findBossGolem();
        if (golem != null) {
            golem.discard();
        }
        super.remove(reason);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);

        if (this.homePos != null) {
            nbt.putInt("HomeX", this.homePos.getX());
            nbt.putInt("HomeY", this.homePos.getY());
            nbt.putInt("HomeZ", this.homePos.getZ());
        }
        if (this.bossGolemUuid != null) {
            nbt.putUuid("BossGolem", this.bossGolemUuid);
        }

        nbt.putBoolean("PhaseTwo", isPhaseTwo());
        nbt.putBoolean("FalseMemorySpawning", isFalseMemorySpawning());
        nbt.putInt("FalseMemorySpawnTicks", this.falseMemorySpawnTicks);
        nbt.putInt("AttackCooldown", this.attackCooldown);
        nbt.putInt("MoveAnimTicks", this.moveAnimTicks);
        nbt.putInt("DashCooldown", this.dashCooldown);
        nbt.putInt("AttackTicks", this.attackTicks);
        nbt.putString("AttackKind", this.currentAttack.name());
        if (this.heldHexUuid != null) nbt.putUuid("HeldHex", this.heldHexUuid);
        nbt.putInt("ScaledNearbyPlayers", this.cachedNearbyScalingPlayers);
        nbt.putDouble("ScaledMaxHealth", this.cachedScaledMaxHealth);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);

        if (nbt.contains("HomeX") && nbt.contains("HomeY") && nbt.contains("HomeZ")) {
            this.homePos = new BlockPos(nbt.getInt("HomeX"), nbt.getInt("HomeY"), nbt.getInt("HomeZ"));
        }

        this.bossGolemUuid = nbt.containsUuid("BossGolem") ? nbt.getUuid("BossGolem") : null;
        this.setPhaseTwo(nbt.getBoolean("PhaseTwo"));
        this.setFalseMemorySpawning(nbt.getBoolean("FalseMemorySpawning"));
        this.falseMemorySpawnTicks = nbt.getInt("FalseMemorySpawnTicks");

        this.attackCooldown = nbt.getInt("AttackCooldown");
        this.moveAnimTicks = nbt.getInt("MoveAnimTicks");
        this.dashCooldown = nbt.getInt("DashCooldown");
        this.attackTicks = nbt.getInt("AttackTicks");

        try {
            this.currentAttack = AttackKind.valueOf(nbt.getString("AttackKind"));
        } catch (Exception ignored) {
            this.currentAttack = AttackKind.NONE;
        }

        this.heldHexUuid = nbt.containsUuid("HeldHex") ? nbt.getUuid("HeldHex") : null;
        this.cachedNearbyScalingPlayers = nbt.contains("ScaledNearbyPlayers") ? nbt.getInt("ScaledNearbyPlayers") : -1;
        this.cachedScaledMaxHealth = nbt.contains("ScaledMaxHealth") ? nbt.getDouble("ScaledMaxHealth") : BASE_MAX_HEALTH;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            if (isFalseMemorySpawning()) {
                state.setAndContinue(SPAWN);
                return PlayState.CONTINUE;
            }

            if (isPhaseTwo() && !isPhaseTwoCombatActive()) {
                state.setAndContinue(FROG_TRANSFORM);
                return PlayState.CONTINUE;
            }

            switch (getAnimState()) {
                case SPAWN -> state.setAndContinue(SPAWN);
                case FLYING_DASH -> state.setAndContinue(FLYING_DASH);
                case FLYING_DASH_TWIRL -> state.setAndContinue(FLYING_DASH_TWIRL);
                case SKULLS_CAST -> state.setAndContinue(SKULLS_CAST);
                case HEX_CAST -> state.setAndContinue(HEX_CAST);
                case FROG_CAST -> state.setAndContinue(FROG_CAST);
                case FROG_TRANSFORM -> state.setAndContinue(FROG_TRANSFORM);
                case SNARE_CAST -> state.setAndContinue(SNARE_CAST);
                case SPIKE_CAST -> state.setAndContinue(SPIKE_CAST);
                default -> state.setAndContinue(IDLE);
            }
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    static final class HomeOrbitGoal extends Goal {
        private final BossWitchEntity mob;

        HomeOrbitGoal(BossWitchEntity mob) {
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
        public void start() {
            this.mob.orbitAngle = this.mob.getRandom().nextFloat() * ((float) Math.PI * 2.0f);
            this.mob.orbitDirection = this.mob.getRandom().nextBoolean() ? 1 : -1;
            this.mob.orbitRadius = 10.0f + this.mob.getRandom().nextFloat() * 5.0f;
        }

        @Override
        public void tick() {
            if (this.mob.homePos == null) return;

            if (this.mob.isFalseMemorySpawning()) {
                this.mob.setVelocity(Vec3d.ZERO);
                return;
            }

            Vec3d center = Vec3d.ofCenter(this.mob.homePos).add(0.0D, 8.0D, 0.0D);
            LivingEntity target = this.mob.getTarget();

            this.mob.orbitAngle += 0.045f * this.mob.orbitDirection;
            float wantedRadius = 9.0f + 6.0f * (0.5f + 0.5f * MathHelper.sin(this.mob.age * 0.045f));
            this.mob.orbitRadius += (wantedRadius - this.mob.orbitRadius) * 0.08f;

            double goalX = center.x + Math.cos(this.mob.orbitAngle) * this.mob.orbitRadius;
            double goalZ = center.z + Math.sin(this.mob.orbitAngle) * this.mob.orbitRadius;
            double goalY;

            if (target != null) {
                goalY = MathHelper.clamp(
                        target.getY() + 3.5D + Math.sin(this.mob.orbitAngle * 0.7f) * 1.5D,
                        this.mob.homePos.getY() + 4.0D,
                        this.mob.homePos.getY() + 13.0D
                );
            } else {
                goalY = center.y + Math.sin(this.mob.orbitAngle * 1.8f) * 2.0D;
            }

            Vec3d goal = new Vec3d(goalX, goalY, goalZ);

            Vec3d fromCenter = goal.subtract(Vec3d.ofCenter(this.mob.homePos));
            Vec3d flat = new Vec3d(fromCenter.x, 0.0D, fromCenter.z);
            if (flat.lengthSquared() > ((ARENA_RADIUS - 4.0D) * (ARENA_RADIUS - 4.0D))) {
                flat = flat.normalize().multiply(ARENA_RADIUS - 4.0D);
                goal = new Vec3d(
                        this.mob.homePos.getX() + 0.5D + flat.x,
                        goal.y,
                        this.mob.homePos.getZ() + 0.5D + flat.z
                );
            }

            boolean canDash = !this.mob.isPhaseTwo() || this.mob.isPhaseTwoCombatActive();
            double d2 = this.mob.getPos().squaredDistanceTo(goal);
            boolean dash = canDash && this.mob.dashCooldown <= 0
                    && (d2 > 64.0D || this.mob.getRandom().nextInt(90) == 0);

            double speed = dash ? 1.85D : ((this.mob.isPhaseTwo() && !this.mob.isPhaseTwoCombatActive()) ? 1.18D : 1.08D);

            if (dash) {
                this.mob.maybeStartDashAnimation(this.mob.getRandom().nextBoolean());
                this.mob.dashCooldown = 70 + this.mob.getRandom().nextInt(40);
            }

            this.mob.getMoveControl().moveTo(goal.x, goal.y, goal.z, speed);

            if (target != null) {
                this.mob.getLookControl().lookAt(target, 24.0f, 24.0f);
            }
        }
    }

    static final class PhaseAttackGoal extends Goal {
        private final BossWitchEntity mob;

        PhaseAttackGoal(BossWitchEntity mob) {
            this.mob = mob;
        }

        @Override
        public boolean canStart() {
            if (this.mob.isFalseMemorySpawning()) return false;

            LivingEntity target = this.mob.getTarget();
            if (target == null || !target.isAlive()) return false;
            if (!this.mob.isInsideArena(target)) return false;
            return this.mob.canAttackNow();
        }

        @Override
        public boolean shouldContinue() {
            return false;
        }

        @Override
        public void start() {
            this.mob.chooseAndStartAttack();
        }
    }
}