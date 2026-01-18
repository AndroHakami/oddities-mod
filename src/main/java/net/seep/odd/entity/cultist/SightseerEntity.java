package net.seep.odd.entity.cultist;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.status.ModStatusEffects;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public final class SightseerEntity extends PathAwareEntity implements GeoEntity {

    /* ---------- GeckoLib ---------- */
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation IDLE   = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK   = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN    = RawAnimation.begin().thenLoop("run");
    // Keep "caught" active while CAUGHT_TIME > 0 (loop is fine even if your anim is short)
    private static final RawAnimation CAUGHT = RawAnimation.begin().thenLoop("caught");

    /* ---------- Synced state ---------- */
    private static final TrackedData<BlockPos> HOME_POS =
            DataTracker.registerData(SightseerEntity.class, TrackedDataHandlerRegistry.BLOCK_POS);
    private static final TrackedData<Boolean> HOME_SET =
            DataTracker.registerData(SightseerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    // 0 = normal, 1 = running, 2 = caught
    private static final TrackedData<Integer> MODE =
            DataTracker.registerData(SightseerEntity.class, TrackedDataHandlerRegistry.INTEGER);

    // caught timer for client animation consistency
    private static final TrackedData<Integer> CAUGHT_TIME =
            DataTracker.registerData(SightseerEntity.class, TrackedDataHandlerRegistry.INTEGER);

    /* ---------- Behavior tuning ---------- */
    private static final int HEAR_RANGE  = 24;
    private static final int SIGHT_RANGE = 24;

    private static final int WANDER_RADIUS = 14;

    // Navigation speed multipliers (multiplies movement speed attribute)
    private static final double WALK_NAV_SPEED = 0.85;
    private static final double RUN_NAV_SPEED  = 2.45;

    private static final int CAUGHT_TOTAL_TICKS   = 40; // 2 seconds
    private static final int TELEPORT_AFTER_TICKS = 20; // 1 second after caught started

    /* ---------- Server-only state ---------- */
    @Nullable private UUID targetUuid = null;
    @Nullable private BlockPos investigatePos = null;

    private int retargetCooldown = 0;

    public SightseerEntity(EntityType<? extends SightseerEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 8;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 50.0D)
                // slow wander speed (warden-ish vibe)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.23D)
                // high knockback resistance
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.90D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0D);
    }

    /* ---------- Data tracker ---------- */
    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(HOME_POS, BlockPos.ORIGIN);
        this.dataTracker.startTracking(HOME_SET, false);
        this.dataTracker.startTracking(MODE, 0);
        this.dataTracker.startTracking(CAUGHT_TIME, 0);
    }

    public void setHomePos(BlockPos pos) {
        this.dataTracker.set(HOME_POS, pos);
        this.dataTracker.set(HOME_SET, true);
    }

    public BlockPos getHomePos() {
        if (this.dataTracker.get(HOME_SET)) return this.dataTracker.get(HOME_POS);
        return this.getBlockPos();
    }

    private int getMode() { return this.dataTracker.get(MODE); }
    private void setMode(int mode) { this.dataTracker.set(MODE, mode); }

    private int getCaughtTime() { return this.dataTracker.get(CAUGHT_TIME); }
    private void setCaughtTime(int ticks) { this.dataTracker.set(CAUGHT_TIME, ticks); }

    /* ---------- Goals ---------- */
    @Override
    protected void initGoals() {
        // One "brain" goal that runs forever and controls wander/hear/run/caught/teleport
        this.goalSelector.add(1, new SightseerCoreGoal(this));

        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 12f));
        this.goalSelector.add(9, new LookAroundGoal(this));
    }

    static final class SightseerCoreGoal extends Goal {
        private final SightseerEntity mob;

        SightseerCoreGoal(SightseerEntity mob) {
            this.mob = mob;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override public boolean canStart() { return true; }
        @Override public boolean shouldContinue() { return true; }

        @Override
        public void tick() {
            if (mob.getWorld().isClient) return;
            mob.serverCoreTick();
        }
    }

    /* ---------- Core logic ---------- */
    private void serverCoreTick() {
        // keep CAUGHT timer synced down
        int caught = getCaughtTime();
        if (caught > 0) {
            // caught mode
            setMode(2);
            getNavigation().stop();

            ServerPlayerEntity target = getTargetPlayer();
            if (target != null) {
                this.getLookControl().lookAt(target, 30.0F, 30.0F);

                // Apply "stop the player" once at the beginning of caught
                if (caught == CAUGHT_TOTAL_TICKS) {
                    target.addStatusEffect(new StatusEffectInstance(
                            StatusEffects.SLOWNESS, 40, 10, true, false, true
                    ));

                    this.getWorld().playSound(null, this.getBlockPos(),
                            SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 0.7f, 1.3f);
                }

                // Teleport check exactly 1 second after caught started
                int elapsed = CAUGHT_TOTAL_TICKS - caught;
                if (elapsed == TELEPORT_AFTER_TICKS) {
                    // still there + still valid + we didn't die (we're here)
                    if (isValidVictim(target) && this.squaredDistanceTo(target) <= (3.0 * 3.0)) {
                        teleportVictimHome(target);
                    }
                    // after attempting teleport, forget target either way
                    clearTargeting();
                }
            } else {
                // no target? just exit caught
                clearTargeting();
            }

            setCaughtTime(caught - 1);
            if (getCaughtTime() <= 0) {
                setMode(0);
            }
            return;
        }

        // Not caught
        setMode(0);

        if (retargetCooldown > 0) retargetCooldown--;

        // If we have a target, run toward them
        ServerPlayerEntity target = getTargetPlayer();
        if (target != null) {
            if (!isValidVictim(target) || this.squaredDistanceTo(target) > (SIGHT_RANGE * SIGHT_RANGE * 2.25)) {
                clearTargeting();
            } else {
                // RUN mode
                setMode(1);
                investigatePos = null;

                this.getLookControl().lookAt(target, 30.0F, 30.0F);
                this.getNavigation().startMovingTo(target, RUN_NAV_SPEED);

                // Catch if close
                if (this.distanceTo(target) <= 1.2F) {
                    setCaughtTime(CAUGHT_TOTAL_TICKS);
                }
                return;
            }
        }

        // Acquire a visible target (preferred)
        if (retargetCooldown == 0) {
            ServerPlayerEntity visible = findNearestVisibleVictim();
            if (visible != null) {
                targetUuid = visible.getUuid();
                retargetCooldown = 10;
                setMode(1);
                this.getNavigation().startMovingTo(visible, RUN_NAV_SPEED);
                return;
            }
        }

        // “Sound” seeking: if we hear someone moving nearby (not sneaking), walk toward them
        if (retargetCooldown == 0) {
            ServerPlayerEntity heard = findNearestHeardVictim();
            if (heard != null) {
                investigatePos = heard.getBlockPos();
                retargetCooldown = 10;
            }
        }

        // If we have an investigate position, walk there
        if (investigatePos != null) {
            double distSq = this.getPos().squaredDistanceTo(Vec3d.ofCenter(investigatePos));
            if (distSq <= 2.0) {
                investigatePos = null;
            } else {
                this.getNavigation().startMovingTo(
                        investigatePos.getX() + 0.5,
                        investigatePos.getY(),
                        investigatePos.getZ() + 0.5,
                        WALK_NAV_SPEED
                );
                return;
            }
        }

        // Wander around home if idle / no path
        if (this.age % 35 == 0 && (this.getNavigation().isIdle() || this.random.nextInt(3) == 0)) {
            BlockPos home = getHomePos();
            BlockPos dest = pickWanderPosNear(home, WANDER_RADIUS);
            if (dest != null) {
                this.getNavigation().startMovingTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, WALK_NAV_SPEED);
            }
        }
    }

    @Nullable
    private BlockPos pickWanderPosNear(BlockPos home, int radius) {
        World w = this.getWorld();
        for (int tries = 0; tries < 12; tries++) {
            int dx = this.random.nextInt(radius * 2 + 1) - radius;
            int dz = this.random.nextInt(radius * 2 + 1) - radius;
            BlockPos base = home.add(dx, 0, dz);

            // Find a nearby "standable" spot within +/-4 Y
            for (int dy = 4; dy >= -4; dy--) {
                BlockPos p = base.up(dy);
                if (w.getBlockState(p).isAir() && w.getBlockState(p.down()).isSolidBlock(w, p.down())) {
                    return p;
                }
            }
        }
        return null;
    }

    @Nullable
    private ServerPlayerEntity getTargetPlayer() {
        if (targetUuid == null) return null;
        if (!(this.getWorld() instanceof ServerWorld)) return null;

        MinecraftServer server = ((ServerWorld) this.getWorld()).getServer();
        if (server == null) return null;

        ServerPlayerEntity p = server.getPlayerManager().getPlayer(targetUuid);
        if (p == null) return null;
        if (p.getWorld() != this.getWorld()) return null;
        return p;
    }
    private static void divineTouchFx(ServerWorld sw, Vec3d pos) {
        // Enchant “letters”
        sw.spawnParticles(ParticleTypes.ENCHANT,
                pos.x, pos.y, pos.z,
                18,
                0.35, 0.45, 0.35,
                0.08);

        // Subtle purple ender wisps
        sw.spawnParticles(ParticleTypes.PORTAL,
                pos.x, pos.y + 0.25, pos.z,
                10,
                0.30, 0.35, 0.30,
                0.03);
    }

    private void clearTargeting() {
        targetUuid = null;
        investigatePos = null;
        setMode(0);
        setCaughtTime(0);
        this.getNavigation().stop();
    }

    private boolean isValidVictim(ServerPlayerEntity p) {
        if (p == null) return false;
        if (!p.isAlive() || p.isSpectator()) return false;

        // Creative players should be ignored
        if (p.getAbilities().creativeMode) return false;

        // Invisibility potion or otherwise invisible
        if (p.isInvisible()) return false;

        // Also ignore if divinely protected / cultist
        return !isDivinelyProtected(p);
    }


    private boolean isDivinelyProtected(PlayerEntity p) {
        // Cultist is naturally immune too
        if (isCultist(p)) return true;
        return p.hasStatusEffect(ModStatusEffects.DIVINE_PROTECTION);
    }

    private static boolean isCultist(PlayerEntity p) {
        if (!(p instanceof ServerPlayerEntity sp)) return false;
        String current = net.seep.odd.abilities.PowerAPI.get(sp);
        return "cultist".equals(current);
    }

    @Nullable
    private ServerPlayerEntity findNearestVisibleVictim() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return null;

        List<ServerPlayerEntity> players = sw.getPlayers(p -> isValidVictim(p));
        ServerPlayerEntity best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (ServerPlayerEntity p : players) {
            double d = this.squaredDistanceTo(p);
            if (d > (SIGHT_RANGE * SIGHT_RANGE)) continue;

            // visibility check
            if (!this.canSee(p)) continue;

            if (d < bestDistSq) {
                bestDistSq = d;
                best = p;
            }
        }
        return best;
    }

    @Nullable
    private ServerPlayerEntity findNearestHeardVictim() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return null;

        List<ServerPlayerEntity> players = sw.getPlayers(p -> isValidVictim(p));
        ServerPlayerEntity best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (ServerPlayerEntity p : players) {
            double d = this.squaredDistanceTo(p);
            if (d > (HEAR_RANGE * HEAR_RANGE)) continue;

            // “sound” approximation:
            // - ignore sneaking
            // - prefer players actually moving
            if (p.isSneaking()) continue;
            if (p.getVelocity().horizontalLengthSquared() < 0.003 && !p.isSprinting()) continue;

            if (d < bestDistSq) {
                bestDistSq = d;
                best = p;
            }
        }
        return best;
    }

    private void teleportVictimHome(ServerPlayerEntity victim) {
        BlockPos home = getHomePos();

        // teleport victim to stored spawn location
        victim.teleport(
                (ServerWorld) this.getWorld(),
                home.getX() + 0.5,
                home.getY() + 1.1,
                home.getZ() + 0.5,
                victim.getYaw(),
                victim.getPitch()
        );

        this.getWorld().playSound(null, home,
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 0.9f, 0.8f);
    }

    /* ---------- Never “retaliate” against protected / cultist attackers ---------- */
    @Override
    public boolean damage(net.minecraft.entity.damage.DamageSource source, float amount) {
        boolean ok = super.damage(source, amount);

        Entity attacker = source.getAttacker();
        if (attacker instanceof PlayerEntity pe) {
            if (isDivinelyProtected(pe)) {
                // Drop all aggression + forget our own tracking
                this.setTarget(null);
                this.setAttacker(null);
                clearTargeting();
            }
        }

        return ok;
    }

    /* ---------- GeckoLib ---------- */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "sightseer.controller", 0, state -> {
            if (getCaughtTime() > 0 || getMode() == 2) {
                state.setAndContinue(CAUGHT);
                return PlayState.CONTINUE;
            }

            if (state.isMoving()) {
                if (getMode() == 1) {
                    state.setAndContinue(RUN);
                } else {
                    state.setAndContinue(WALK);
                }
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

    /* ---------- NBT ---------- */
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);

        nbt.putBoolean("HomeSet", this.dataTracker.get(HOME_SET));
        BlockPos hp = this.dataTracker.get(HOME_POS);
        nbt.putInt("HomeX", hp.getX());
        nbt.putInt("HomeY", hp.getY());
        nbt.putInt("HomeZ", hp.getZ());

        nbt.putInt("Mode", getMode());
        nbt.putInt("CaughtTime", getCaughtTime());
        if (targetUuid != null) nbt.putUuid("TargetUUID", targetUuid);
        if (investigatePos != null) {
            nbt.putInt("InvX", investigatePos.getX());
            nbt.putInt("InvY", investigatePos.getY());
            nbt.putInt("InvZ", investigatePos.getZ());
            nbt.putBoolean("HasInvestigate", true);
        } else {
            nbt.putBoolean("HasInvestigate", false);
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);

        boolean set = nbt.getBoolean("HomeSet");
        this.dataTracker.set(HOME_SET, set);
        this.dataTracker.set(HOME_POS, new BlockPos(nbt.getInt("HomeX"), nbt.getInt("HomeY"), nbt.getInt("HomeZ")));

        setMode(nbt.getInt("Mode"));
        setCaughtTime(nbt.getInt("CaughtTime"));

        targetUuid = nbt.containsUuid("TargetUUID") ? nbt.getUuid("TargetUUID") : null;

        if (nbt.getBoolean("HasInvestigate")) {
            investigatePos = new BlockPos(nbt.getInt("InvX"), nbt.getInt("InvY"), nbt.getInt("InvZ"));
        } else {
            investigatePos = null;
        }
    }
}
