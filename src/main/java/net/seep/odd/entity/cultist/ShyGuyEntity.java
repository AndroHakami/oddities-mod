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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.sound.ModSounds;
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

public final class ShyGuyEntity extends PathAwareEntity implements GeoEntity {

    /* ---------- GeckoLib ---------- */
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation IDLE   = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK   = RawAnimation.begin().thenLoop("walk");

    private static final RawAnimation TRANS_TO_SIT = RawAnimation.begin().thenPlay("transition_to_sit");
    private static final RawAnimation SITTING      = RawAnimation.begin().thenLoop("sitting");
    private static final RawAnimation SIT_TO_IDLE  = RawAnimation.begin().thenPlay("sit_to_idle");

    private static final RawAnimation RAGE_WINDUP  = RawAnimation.begin().thenPlay("rage"); // 4s stand-still angry
    private static final RawAnimation RUN          = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation RUN_ATTACK   = RawAnimation.begin().thenLoop("run_attack");

    private static final RawAnimation TRANS_TO_SIT_RAGE = RawAnimation.begin().thenPlay("transition_to_sit_rage");

    /* ---------- Synced state ---------- */
    private static final TrackedData<BlockPos> HOME_POS =
            DataTracker.registerData(ShyGuyEntity.class, TrackedDataHandlerRegistry.BLOCK_POS);
    private static final TrackedData<Boolean> HOME_SET =
            DataTracker.registerData(ShyGuyEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    // 0=normal,1=trans_to_sit,2=sitting,3=sit_to_idle,4=rage_windup,5=enraged_run,6=trans_to_sit_rage
    private static final TrackedData<Integer> STATE =
            DataTracker.registerData(ShyGuyEntity.class, TrackedDataHandlerRegistry.INTEGER);

    // true while the current sitting state is the "post rage" sit (for client loop selection)
    private static final TrackedData<Boolean> POST_RAGE_SIT =
            DataTracker.registerData(ShyGuyEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    // Attack animation pulse, like your OuterMan style
    private static final TrackedData<Integer> ATTACK_TIME =
            DataTracker.registerData(ShyGuyEntity.class, TrackedDataHandlerRegistry.INTEGER);

    /* ---------- Tuning ---------- */
    private static final int LOOK_TRIGGER_RANGE = 32;
    private static final int WANDER_RADIUS = 14;

    private static final double WALK_NAV_SPEED = 0.85;  // slow wander
    private static final double RUN_NAV_SPEED  = 2.9;  // VERY fast (2x player-ish feel)

    // Sitting / crying
    private static final int TRANSITION_TICKS = 12;
    private static final int SIT_TICKS_DEFAULT = 20 * 20; // 20 seconds

    // Rage flow
    private static final int RAGE_WINDUP_TICKS = 4 * 20;  // 4 seconds
    private static final int ENRAGED_TICKS     = 15 * 20; // 15 seconds
    private static final int POST_RAGE_SIT_TICKS = 20 * 20;
    private long rageWindupEndsAt = 0L;

    // Attack timings (based on animation fractions 0.20 and 0.46)
    private static final int ATTACK_TOTAL_TICKS = 20; // ~1s sequence
    private static final int HIT1_TICK = 4;           // 0.20 * 20
    private static final int HIT2_TICK = 9;           // 0.46 * 20

    private static final float HIT_DAMAGE = 8.0F;     // 4 hearts
    private static final float HIT_KNOCKBACK = 0.45F;

    /* ---------- Server-only state ---------- */
    private int stateTicks = 0;               // time remaining in current state (for transitions/sit/windup/etc)
    private boolean pendingRage = false;      // used when spotted while sitting; must sit_to_idle first
    @Nullable private UUID rageTargetUuid = null;

    // attack sequence
    private int attackTicks = 0;              // mirrored to ATTACK_TIME
    private int attackSeqTicks = 0;           // drives hit timing
    private int attackCooldown = 0;

    // field for sitting duration handoff
    private int nextSitTicks = SIT_TICKS_DEFAULT;

    public ShyGuyEntity(EntityType<? extends ShyGuyEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 12;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 80.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.23D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.85D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0D);
    }

    /* ---------- Data tracker ---------- */
    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(HOME_POS, BlockPos.ORIGIN);
        this.dataTracker.startTracking(HOME_SET, false);
        this.dataTracker.startTracking(STATE, 0);
        this.dataTracker.startTracking(POST_RAGE_SIT, false);
        this.dataTracker.startTracking(ATTACK_TIME, 0);
    }

    public void setHomePos(BlockPos pos) {
        this.dataTracker.set(HOME_POS, pos);
        this.dataTracker.set(HOME_SET, true);
    }

    public BlockPos getHomePos() {
        return this.dataTracker.get(HOME_SET) ? this.dataTracker.get(HOME_POS) : this.getBlockPos();
    }

    private int getState() { return this.dataTracker.get(STATE); }
    private void setState(int s) { this.dataTracker.set(STATE, s); }

    // used by client loop controller if you add it
    public int getSyncedState() { return this.dataTracker.get(STATE); }
    public boolean isPostRageSitting() { return this.dataTracker.get(POST_RAGE_SIT); }

    private void setAttackAnim(int ticks) {
        this.attackTicks = ticks;
        this.dataTracker.set(ATTACK_TIME, ticks);
    }
    private int getAttackAnim() {
        return this.dataTracker.get(ATTACK_TIME);
    }

    /* ---------- Goals ---------- */
    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new CoreGoal(this));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 12f));
        this.goalSelector.add(9, new LookAroundGoal(this));
    }

    static final class CoreGoal extends Goal {
        private final ShyGuyEntity mob;
        CoreGoal(ShyGuyEntity mob) {
            this.mob = mob;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }
        @Override public boolean canStart() { return true; }
        @Override public boolean shouldContinue() { return true; }
        @Override public void tick() {
            if (!mob.getWorld().isClient) mob.serverTickLogic();
        }
    }

    /* ---------- Hard stop (prevents sliding during rage anim) ---------- */
    private void hardStop() {
        this.getNavigation().stop();
        this.setSprinting(false);

        Vec3d v = this.getVelocity();
        if (v.x != 0.0 || v.z != 0.0) {
            this.setVelocity(0.0, v.y, 0.0);
        }
    }

    /* ---------- Server logic ---------- */
    private void serverTickLogic() {
        // mirror attack time down
        if (attackTicks > 0) {
            attackTicks--;
            this.dataTracker.set(ATTACK_TIME, attackTicks);
        }
        if (attackCooldown > 0) attackCooldown--;

        // Try to trigger rage by "being looked at"
        if (canBeTriggeredNow()) {
            ServerPlayerEntity looker = findLookingPlayer();
            if (looker != null) {
                onSpottedBy(looker);
            }
        }

        // Drive state machine
        switch (getState()) {
            case 1 -> tickTransitionToSit();
            case 2 -> tickSitting();
            case 3 -> tickSitToIdle();
            case 4 -> tickRageWindup();
            case 5 -> tickEnraged();
            case 6 -> tickTransitionToSitRage();
            default -> tickNormal();
        }
    }

    private boolean canBeTriggeredNow() {
        int s = getState();
        return s != 4 && s != 5;
    }

    private void onSpottedBy(ServerPlayerEntity player) {
        if (!isValidVictim(player)) return;

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 4, true, false, false));
        player.playSound(SoundEvents.ENTITY_WARDEN_ANGRY, 1.0f, 0.75f);

        rageTargetUuid = player.getUuid();

        int s = getState();
        if (s == 2 || s == 1 || s == 6) {
            pendingRage = true;
            startSitToIdle();
            return;
        }
        if (s == 3) {
            pendingRage = true;
            return;
        }

        startRageWindup();
    }

    /* ---------------- NORMAL ---------------- */
    private void tickNormal() {
        setState(0);
        this.dataTracker.set(POST_RAGE_SIT, false);
        this.setSprinting(false);

        // wander near home
        if (this.age % 35 == 0 && (this.getNavigation().isIdle() || this.random.nextInt(3) == 0)) {
            BlockPos home = getHomePos();
            BlockPos dest = pickWanderPosNear(home, WANDER_RADIUS);
            if (dest != null) {
                this.getNavigation().startMovingTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, WALK_NAV_SPEED);
            }
        }

        // Sometimes sit/cry
        if (this.age % 20 == 0 && this.random.nextInt(220) == 0) {
            startTransitionToSit(SIT_TICKS_DEFAULT);
        }
    }

    private void startTransitionToSit(int sitTicks) {
        pendingRage = false;
        rageTargetUuid = null;

        this.dataTracker.set(POST_RAGE_SIT, false);

        setState(1);
        stateTicks = TRANSITION_TICKS;
        hardStop();

        this.nextSitTicks = sitTicks;
    }

    private void tickTransitionToSit() {
        hardStop();
        if (--stateTicks <= 0) {
            setState(2);
            stateTicks = nextSitTicks;
        }
    }

    private void tickSitting() {
        hardStop();
        if (--stateTicks <= 0) {
            startSitToIdle();
        }
    }

    private void startSitToIdle() {
        this.dataTracker.set(POST_RAGE_SIT, false);

        setState(3);
        stateTicks = TRANSITION_TICKS;
        hardStop();
    }

    private void tickSitToIdle() {
        hardStop();

        if (--stateTicks <= 0) {
            if (pendingRage) {
                pendingRage = false;
                startRageWindup();
            } else {
                setState(0);
            }
        }
    }

    /* ---------------- RAGE WINDUP ---------------- */
    /* ---------------- RAGE WINDUP ---------------- */
    private void startRageWindup() {
        setState(4);

        // Freeze instantly
        hardStop();

        // World-time based end tick (EXACT 4s after starting the anim)
        if (this.getWorld() instanceof ServerWorld sw) {
            long now = sw.getTime();
            this.rageWindupEndsAt = now + RAGE_WINDUP_TICKS;

            // Louder so it carries ~40 blocks
            // (~16 blocks * volume). 2.6 => ~41 blocks
            sw.playSound(null, this.getBlockPos(),
                    ModSounds.SHY_GUY_RAGE_WINDUP, SoundCategory.HOSTILE, 2.6f, 1.0f);
        } else {
            this.rageWindupEndsAt = 0L;
        }

        // keep stateTicks coherent if you use it elsewhere (optional)
        this.stateTicks = RAGE_WINDUP_TICKS;
    }


    private void tickRageWindup() {
        // If we hit the deadline, switch NOW (no extra hardStop tick)
        if (this.getWorld() instanceof ServerWorld sw && rageWindupEndsAt > 0L) {
            if (sw.getTime() >= rageWindupEndsAt) {
                startEnraged(); // starts moving immediately
                return;
            }
        }

        // Otherwise stay fully frozen during windup
        hardStop();

        ServerPlayerEntity target = getRageTarget();
        if (target != null) {
            this.getLookControl().lookAt(target, 30.0F, 30.0F);
        }
    }


    /* ---------------- ENRAGED ---------------- */
    private void startEnraged() {
        setState(5);
        stateTicks = ENRAGED_TICKS;
        this.setSprinting(true);

        attackSeqTicks = 0;
        attackCooldown = 0;

        // start moving THIS tick (fixes “stands still after rage”)
        ServerPlayerEntity target = getRageTarget();
        if (target != null && isValidVictim(target)) {
            this.getNavigation().startMovingTo(target, RUN_NAV_SPEED);
        }
    }

    private void tickEnraged() {
        this.setSprinting(true);

        ServerPlayerEntity target = getRageTarget();
        if (target == null || !isValidVictim(target)) {
            startTransitionToSitRage();
            return;
        }

        this.getLookControl().lookAt(target, 30.0F, 30.0F);
        this.getNavigation().startMovingTo(target, RUN_NAV_SPEED);

        tickAttackSequence(target);

        if (--stateTicks <= 0) {
            startTransitionToSitRage();
        }
    }

    private void tickAttackSequence(ServerPlayerEntity target) {
        if (attackSeqTicks > 0) {
            int elapsed = ATTACK_TOTAL_TICKS - attackSeqTicks;

            if (elapsed == HIT1_TICK || elapsed == HIT2_TICK) {
                applyRunHit(target);
            }

            attackSeqTicks--;
            if (attackSeqTicks <= 0) {
                attackCooldown = 10;
            }
            return;
        }

        if (attackCooldown <= 0 && this.squaredDistanceTo(target) <= (2.2 * 2.2)) {
            setAttackAnim(ATTACK_TOTAL_TICKS);
            attackSeqTicks = ATTACK_TOTAL_TICKS;

            // play attack sound ONCE (contains both swooshes)
            if (this.getWorld() instanceof ServerWorld sw) {
                sw.playSound(null, this.getBlockPos(),
                        ModSounds.SHY_GUY_ATTACK, SoundCategory.HOSTILE, 1.0f, 1.0f);
            }
        }
    }

    private void applyRunHit(ServerPlayerEntity target) {
        if (!target.isAlive()) return;
        if (!isValidVictim(target)) return;
        if (this.squaredDistanceTo(target) > (2.8 * 2.8)) return;

        target.damage(this.getDamageSources().mobAttack(this), HIT_DAMAGE);

        Vec3d dir = target.getPos().subtract(this.getPos()).normalize();
        target.takeKnockback(HIT_KNOCKBACK, -dir.x, -dir.z);
    }

    /* ---------------- POST-RAGE SIT ---------------- */
    private void startTransitionToSitRage() {
        setState(6);
        stateTicks = TRANSITION_TICKS;
        hardStop();

        this.dataTracker.set(POST_RAGE_SIT, true);
        nextSitTicks = POST_RAGE_SIT_TICKS;

        rageTargetUuid = null;
        pendingRage = false;

        attackSeqTicks = 0;
        setAttackAnim(0);
    }

    private void tickTransitionToSitRage() {
        hardStop();
        if (--stateTicks <= 0) {
            setState(2);
            stateTicks = nextSitTicks;
        }
    }

    /* ---------- Victim rules ---------- */
    private boolean isValidVictim(ServerPlayerEntity p) {
        if (p == null) return false;
        if (!p.isAlive() || p.isSpectator()) return false;
        if (p.getAbilities().creativeMode) return false;
        if (p.isInvisible()) return false;

        return !isDivinelyProtected(p);
    }

    private boolean isDivinelyProtected(PlayerEntity p) {
        if (isCultist(p)) return true;
        return p.hasStatusEffect(ModStatusEffects.DIVINE_PROTECTION);
    }

    private static boolean isCultist(PlayerEntity p) {
        if (!(p instanceof ServerPlayerEntity sp)) return false;
        String current = net.seep.odd.abilities.PowerAPI.get(sp);
        return "cultist".equals(current);
    }

    /* ---------- Looking trigger ---------- */
    @Nullable
    private ServerPlayerEntity findLookingPlayer() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return null;

        List<ServerPlayerEntity> players = sw.getPlayers(this::isValidVictim);
        ServerPlayerEntity best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (ServerPlayerEntity p : players) {
            double d = this.squaredDistanceTo(p);
            if (d > (LOOK_TRIGGER_RANGE * LOOK_TRIGGER_RANGE)) continue;

            if (!p.canSee(this)) continue;
            if (!isPlayerLookingAtMe(p)) continue;

            if (d < bestDistSq) {
                bestDistSq = d;
                best = p;
            }
        }
        return best;
    }

    private boolean isPlayerLookingAtMe(ServerPlayerEntity p) {
        Vec3d eyes = p.getCameraPosVec(1.0F);
        Vec3d look = p.getRotationVec(1.0F).normalize();

        Vec3d toMe = this.getPos().add(0, this.getStandingEyeHeight() * 0.6, 0).subtract(eyes);
        double dist = toMe.length();
        if (dist < 0.0001) return true;

        Vec3d dir = toMe.normalize();
        double dot = look.dotProduct(dir);

        return dot > 0.965;
    }

    /* ---------- Wander helper ---------- */
    @Nullable
    private BlockPos pickWanderPosNear(BlockPos home, int radius) {
        World w = this.getWorld();
        for (int tries = 0; tries < 12; tries++) {
            int dx = this.random.nextInt(radius * 2 + 1) - radius;
            int dz = this.random.nextInt(radius * 2 + 1) - radius;
            BlockPos base = home.add(dx, 0, dz);

            for (int dy = 4; dy >= -4; dy--) {
                BlockPos p = base.up(dy);
                if (w.getBlockState(p).isAir() && w.getBlockState(p.down()).isSolidBlock(w, p.down())) {
                    return p;
                }
            }
        }
        return null;
    }

    /* ---------- Target lookup ---------- */
    @Nullable
    private ServerPlayerEntity getRageTarget() {
        if (rageTargetUuid == null) return null;
        if (!(this.getWorld() instanceof ServerWorld)) return null;

        MinecraftServer server = ((ServerWorld) this.getWorld()).getServer();
        if (server == null) return null;

        ServerPlayerEntity p = server.getPlayerManager().getPlayer(rageTargetUuid);
        if (p == null) return null;
        if (p.getWorld() != this.getWorld()) return null;
        return p;
    }

    /* ---------- Never “retaliate” against protected / cultist attackers ---------- */
    @Override
    public boolean damage(net.minecraft.entity.damage.DamageSource source, float amount) {
        boolean ok = super.damage(source, amount);

        Entity attacker = source.getAttacker();
        if (attacker instanceof PlayerEntity pe) {
            if (isDivinelyProtected(pe)) {
                this.setTarget(null);
                this.setAttacker(null);
                this.rageTargetUuid = null;
            }
        }

        return ok;
    }

    /* ---------- GeckoLib ---------- */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "shyguy.controller", 0, state -> {
            int s = getState();

            if (s == 5) { // enraged
                if (getAttackAnim() > 0) {
                    state.setAndContinue(RUN_ATTACK);
                    return PlayState.CONTINUE;
                }
                state.setAndContinue(RUN);
                return PlayState.CONTINUE;
            }

            if (s == 4) { // rage windup
                state.setAndContinue(RAGE_WINDUP);
                return PlayState.CONTINUE;
            }

            if (s == 6) { // transition_to_sit_rage
                state.setAndContinue(TRANS_TO_SIT_RAGE);
                return PlayState.CONTINUE;
            }

            if (s == 1) { // transition_to_sit
                state.setAndContinue(TRANS_TO_SIT);
                return PlayState.CONTINUE;
            }

            if (s == 2) { // sitting
                state.setAndContinue(SITTING);
                return PlayState.CONTINUE;
            }

            if (s == 3) { // sit_to_idle
                state.setAndContinue(SIT_TO_IDLE);
                return PlayState.CONTINUE;
            }

            if (state.isMoving()) {
                state.setAndContinue(WALK);
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

        nbt.putInt("State", getState());
        nbt.putInt("StateTicks", stateTicks);
        nbt.putInt("NextSitTicks", nextSitTicks);
        nbt.putBoolean("PendingRage", pendingRage);
        nbt.putBoolean("PostRageSit", this.dataTracker.get(POST_RAGE_SIT));
        if (rageTargetUuid != null) nbt.putUuid("RageTarget", rageTargetUuid);

        nbt.putInt("AttackTicks", attackTicks);
        nbt.putInt("AttackSeqTicks", attackSeqTicks);
        nbt.putInt("AttackCooldown", attackCooldown);
        nbt.putLong("RageWindupEndsAt", rageWindupEndsAt);

    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);

        this.dataTracker.set(HOME_SET, nbt.getBoolean("HomeSet"));
        this.dataTracker.set(HOME_POS, new BlockPos(nbt.getInt("HomeX"), nbt.getInt("HomeY"), nbt.getInt("HomeZ")));

        setState(nbt.getInt("State"));
        stateTicks = nbt.getInt("StateTicks");
        nextSitTicks = nbt.getInt("NextSitTicks");
        pendingRage = nbt.getBoolean("PendingRage");
        this.dataTracker.set(POST_RAGE_SIT, nbt.getBoolean("PostRageSit"));
        rageTargetUuid = nbt.containsUuid("RageTarget") ? nbt.getUuid("RageTarget") : null;

        attackTicks = nbt.getInt("AttackTicks");
        this.dataTracker.set(ATTACK_TIME, attackTicks);

        attackSeqTicks = nbt.getInt("AttackSeqTicks");
        attackCooldown = nbt.getInt("AttackCooldown");
        rageWindupEndsAt = nbt.getLong("RageWindupEndsAt");

    }
}
