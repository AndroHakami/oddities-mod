package net.seep.odd.abilities.tamer;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.FlyingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;
import net.seep.odd.abilities.tamer.projectile.EmeraldShurikenEntity;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class TamerAI {
    private TamerAI() {}

    private static final Map<MobEntity, Boolean> PASSIVE = new WeakHashMap<>();
    public static void setPassive(MobEntity mob, boolean passive) { PASSIVE.put(mob, passive); }

    private enum MoveType { WALK, SWIM, FLY }
    private static MoveType detectMoveType(MobEntity mob) {
        if (mob instanceof FlyingEntity) return MoveType.FLY;
        if (mob.isTouchingWater()) return MoveType.SWIM;
        return MoveType.WALK;
    }

    /** Convenience: treat target as invalid if it's the pet itself or the owner. */
    private static boolean isBadTarget(LivingEntity target, MobEntity mob, ServerPlayerEntity owner) {
        if (target == null) return true;
        if (target == mob)  return true;
        if (target == owner) return true;
        return false;
    }

    public static void install(MobEntity mob, ServerPlayerEntity owner) {
        mob.setPersistent();
        mob.setTarget(null);
        mob.setAttacking(false);

        var atkAttr = mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (atkAttr != null && atkAttr.getBaseValue() < 2.0) atkAttr.setBaseValue(2.0);

        MoveType mt = detectMoveType(mob);
        double followSpeed = switch (mt) {
            case FLY  -> 1.35;
            case SWIM -> 1.15;
            default   -> 1.20;
        };

        var goals   = ((MobEntityAccessor)(Object)mob).odd$getGoalSelector();
        var targets = ((MobEntityAccessor)(Object)mob).odd$getTargetSelector();

        goals.add(0, new SwimGoal(mob));

        // Ender Fall (Endermen only)
        goals.add(1, new UseEnderLevitateThenLaunchGoal(mob, owner.getUuid()));

        // Ranged shuriken
        goals.add(2, new UseShurikenGoal(mob, owner.getUuid()));

        if (mob instanceof PathAwareEntity paw) {
            if (atkAttr != null) goals.add(3, new MeleeAttackGoal(paw, 1.2, true));
            else goals.add(3, new FixedDamageMeleeGoal(paw, 1.2, 2.0f, 18));

            goals.add(4, new FollowOwnerGoalGeneric(mob, owner.getUuid(), followSpeed, 2.5, 10.0));
            goals.add(8, new WanderAroundFarGoal(paw, 1.0));
        }

        goals.add(5, new LookAtEntityGoal(mob, LivingEntity.class, 8.0f));
        goals.add(6, new LookAroundGoal(mob));

        targets.add(0, new NoAggroGoal(mob));
        targets.add(2, new ProtectOwnerTargetGoal(mob, owner.getUuid()));
    }

    /* ---------- Goals ---------- */

    static final class NoAggroGoal extends Goal {
        private final MobEntity mob;
        NoAggroGoal(MobEntity mob) { this.mob = mob; setControls(EnumSet.of(Control.TARGET)); }
        @Override public boolean canStart()        { return Boolean.TRUE.equals(PASSIVE.get(mob)); }
        @Override public boolean shouldContinue()  { return canStart(); }
        @Override public void start()              { mob.setTarget(null); mob.setAttacking(false); }
        @Override public void tick()               { mob.setTarget(null); mob.setAttacking(false); }
    }

    static final class FollowOwnerGoalGeneric extends Goal {
        private final MobEntity mob; private final UUID ownerId; private final double speed, minSq, maxSq;
        private ServerPlayerEntity owner; private int recalc;
        FollowOwnerGoalGeneric(MobEntity mob, UUID ownerId, double speed, double min, double max) {
            this.mob = mob; this.ownerId = ownerId; this.speed = speed; this.minSq = min*min; this.maxSq = max*max;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }
        private ServerPlayerEntity findOwner() {
            if (!(mob.getWorld() instanceof ServerWorld sw)) return null;
            var p = sw.getServer().getPlayerManager().getPlayer(ownerId);
            return (p != null && p.isAlive()) ? p : null;
        }
        @Override public boolean canStart()       { owner = findOwner(); return owner != null; }
        @Override public boolean shouldContinue() { return owner != null && owner.isAlive(); }
        @Override public void tick() {
            if (owner == null) return;
            if (--recalc > 0) return;
            recalc = 8;
            double d2 = mob.squaredDistanceTo(owner);
            if (d2 > maxSq) mob.getNavigation().startMovingTo(owner, speed);
            else if (d2 < minSq) mob.getNavigation().stop();
            mob.getLookControl().lookAt(owner, 30f, 30f);

            // Safety: never keep self/owner as target if something else set it
            var t = mob.getTarget();
            if (t == mob || t == owner) { mob.setTarget(null); mob.setAttacking(false); }
        }
    }

    static final class ProtectOwnerTargetGoal extends Goal {
        private final MobEntity mob; private final UUID ownerId;
        private ServerPlayerEntity owner; private int cooldown;
        ProtectOwnerTargetGoal(MobEntity mob, UUID ownerId) { this.mob = mob; this.ownerId = ownerId; setControls(EnumSet.of(Control.TARGET)); }
        @Override public boolean canStart() {
            if (!(mob.getWorld() instanceof ServerWorld sw)) return false;
            var p = sw.getServer().getPlayerManager().getPlayer(ownerId);
            if (p == null || !p.isAlive()) return false; owner = p; return true;
        }
        @Override public boolean shouldContinue() { return owner != null && owner.isAlive(); }
        @Override public void tick() {
            if (--cooldown > 0 || Boolean.TRUE.equals(PASSIVE.get(mob))) return;
            cooldown = 10;

            HostileEntity best = null; double bestScore = Double.MAX_VALUE;
            Box area = owner.getBoundingBox().expand(18.0);
            for (HostileEntity h : mob.getWorld().getEntitiesByClass(HostileEntity.class, area, HostileEntity::isAlive)) {
                if (h == mob) continue; // <<< critical: skip self
                if (h.getTarget() == owner) { mob.setTarget(h); return; }
                double s = h.squaredDistanceTo(owner); if (s < bestScore) { bestScore = s; best = h; }
            }
            if (best != null && best != mob && mob.getTarget() != best) mob.setTarget(best);
        }
    }

    static final class FixedDamageMeleeGoal extends Goal {
        private final PathAwareEntity mob; private final double speed; private final float damage; private final int cooldownTicks; private int cooldown;
        FixedDamageMeleeGoal(PathAwareEntity mob, double speed, float damage, int cooldownTicks) {
            this.mob = mob; this.speed = speed; this.damage = damage; this.cooldownTicks = cooldownTicks;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }
        @Override public boolean canStart()       { var t = mob.getTarget(); return t != null && t.isAlive() && t != mob; }
        @Override public boolean shouldContinue() { var t = mob.getTarget(); return t != null && t.isAlive() && t != mob; }
        @Override public void tick() {
            var target = mob.getTarget(); if (target == null || target == mob) return;
            mob.getLookControl().lookAt(target, 30.0f, 30.0f);
            mob.getNavigation().startMovingTo(target, speed);
            double reachSq = (mob.getWidth() * 2.0F) * (mob.getWidth() * 2.0F) + target.getWidth();
            if (--cooldown <= 0 && mob.squaredDistanceTo(target) <= reachSq && mob.getVisibilityCache().canSee(target)) {
                target.damage(mob.getDamageSources().mobAttack(mob), damage);
                cooldown = cooldownTicks;
            }
        }
    }

    /* ---------------- Ranged moves (with self/owner guards) ---------------- */

    static final class UseShurikenGoal extends Goal {
        private static final Identifier SHURIKEN_ID = new Identifier("odd","shuriken");
        private static final int    COOLDOWN = 20;
        private static final double MIN_RANGE = 4.0;
        private static final double MAX_RANGE = 16.0;

        private final MobEntity mob; private final UUID ownerId; private int cd;

        UseShurikenGoal(MobEntity mob, UUID ownerId) { this.mob = mob; this.ownerId = ownerId; setControls(EnumSet.of(Control.LOOK)); }

        @Override public boolean canStart() {
            if (Boolean.TRUE.equals(PASSIVE.get(mob))) return false;
            LivingEntity t = mob.getTarget();
            if (!(mob.getWorld() instanceof ServerWorld sw)) return false;
            var owner = sw.getServer().getPlayerManager().getPlayer(ownerId);
            if (isBadTarget(t, mob, owner)) return false;

            double d2 = mob.squaredDistanceTo(t);
            if (d2 < MIN_RANGE*MIN_RANGE || d2 > MAX_RANGE*MAX_RANGE) return false;
            if (!mob.getVisibilityCache().canSee(t)) return false;

            TamerState st = TamerState.get(sw);
            var a = st.getActive(ownerId);
            if (a == null || !mob.getUuid().equals(a.entity)) return false;
            var party = st.partyOf(ownerId);
            if (a.index < 0 || a.index >= party.size()) return false;
            var pm = party.get(a.index);
            return TamerMoves.knows(pm, "odd:shuriken");
        }

        @Override public boolean shouldContinue() { return cd > 0 || canStart(); }

        @Override public void tick() {
            if (cd > 0) { cd--; return; }
            LivingEntity t = mob.getTarget();
            if (!(mob.getWorld() instanceof ServerWorld sw)) return;
            var owner = sw.getServer().getPlayerManager().getPlayer(ownerId);
            if (isBadTarget(t, mob, owner)) return;

            mob.getLookControl().lookAt(t, 30f, 30f);

            ProjectileEntity proj = createShuriken();
            if (proj == null) return;
            proj.setOwner(mob);
            proj.refreshPositionAndAngles(mob.getX(), mob.getEyeY() - 0.1, mob.getZ(), mob.getYaw(), mob.getPitch());

            double dx = t.getX() - mob.getX();
            double dy = t.getBodyY(0.33) - proj.getY();
            double dz = t.getZ() - mob.getZ();
            float speed = 1.6f;
            float inaccuracy = 6.0f;
            proj.setVelocity(dx, dy + 0.2 * Math.sqrt(dx*dx + dz*dz), dz, speed, inaccuracy);

            mob.getWorld().spawnEntity(proj);
            mob.playSound(SoundEvents.ENTITY_ARROW_SHOOT, 1.0f, 0.9f + mob.getRandom().nextFloat() * 0.2f);
            cd = COOLDOWN;
        }

        private ProjectileEntity createShuriken() {
            Entity maybe = Registries.ENTITY_TYPE.getOrEmpty(SHURIKEN_ID)
                    .map(t -> t.create(mob.getWorld())).orElse(null);
            if (maybe instanceof ProjectileEntity p) return p;
            return new EmeraldShurikenEntity(mob.getWorld(), mob);
        }
    }



    // ===================== REPLACE THE WHOLE CLASS BELOW =====================
    static final class UseEnderLevitateThenLaunchGoal extends Goal {
        private static final boolean DEBUG = false;      // set false to silence action-bar notes
        private static final int    COOLDOWN   = 80;    // 4s between uses
        private static final double MIN_RANGE  = 3.0;
        private static final double MAX_RANGE  = 10.0;

        // Phase timings
        private static final int    LEVITATE_TICKS = 20; // ≈1s
        // Vertical launch strength: ~2.6–3.0 sends ≈25–35 blocks in vanilla gravity
        private static final double Y_LAUNCH       = 1.9;

        private final MobEntity mob;
        private final UUID ownerId;
        private int cd;

        // simple 2-step state machine
        private enum Phase { IDLE, LEVITATE }
        private Phase phase = Phase.IDLE;
        private int phaseTicks = 0;
        private LivingEntity latchedTarget = null; // stick to one target for the whole move

        UseEnderLevitateThenLaunchGoal(MobEntity mob, UUID ownerId) {
            this.mob = mob; this.ownerId = ownerId;
            setControls(EnumSet.of(Control.LOOK));
        }

        @Override public boolean canStart() {
            if (!(mob instanceof EndermanEntity)) return false;
            if (Boolean.TRUE.equals(PASSIVE.get(mob))) return false;

            LivingEntity t = mob.getTarget();
            if (!(mob.getWorld() instanceof ServerWorld sw)) return false;
            var owner = sw.getServer().getPlayerManager().getPlayer(ownerId);
            if (isBadTarget(t, mob, owner)) { dbg(owner, "ender: bad target"); return false; }

            double d2 = mob.squaredDistanceTo(t);
            if (d2 < MIN_RANGE*MIN_RANGE || d2 > MAX_RANGE*MAX_RANGE) { dbg(owner, "ender: range"); return false; }

            // Must be owner's active & know the move
            TamerState st = TamerState.get(sw);
            var a = st.getActive(ownerId);
            if (a == null || !mob.getUuid().equals(a.entity)) { dbg(owner, "ender: not active"); return false; }
            var party = st.partyOf(ownerId);
            if (a.index < 0 || a.index >= party.size()) { dbg(owner, "ender: no party slot"); return false; }
            var pm = party.get(a.index);
            if (!TamerMoves.knows(pm, "odd:ender_fall")) { dbg(owner, "ender: move missing"); return false; }

            // Ready to begin the two-phase action
            latchedTarget = t;
            phase = Phase.LEVITATE;
            phaseTicks = LEVITATE_TICKS;
            // Apply levitation now (single application is enough)
            t.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, LEVITATE_TICKS + 4, 0, false, true));
            vfxAt(sw, t.getX(), t.getBodyY(0.5), t.getZ(), true);
            dbg(owner, "Ender Fall • levitate");
            return true;
        }

        @Override public boolean shouldContinue() {
            // Keep running during levitation or while cooling down, otherwise allow normal canStart logic
            return cd > 0 || phase != Phase.IDLE || canStart();
        }

        @Override public void tick() {
            if (!(mob.getWorld() instanceof ServerWorld sw)) return;

            // Cooldown countdown
            if (cd > 0) { cd--; return; }

            var owner = sw.getServer().getPlayerManager().getPlayer(ownerId);

            // If at any time the target becomes invalid, abort gracefully and short cooldown
            if (latchedTarget == null || !latchedTarget.isAlive() || isBadTarget(latchedTarget, mob, owner)) {
                resetState(20);
                return;
            }

            // Face the target while we “channel”
            mob.getLookControl().lookAt(latchedTarget, 30f, 30f);

            if (phase == Phase.LEVITATE) {
                // Hold levitation window
                if (--phaseTicks <= 0) {
                    // Phase transition -> LAUNCH
                    // Small flair again
                    vfxAt(sw, latchedTarget.getX(), latchedTarget.getBodyY(0.5), latchedTarget.getZ(), true);

                    // damp horizontal speed for a clean vertical yeet
                    var v = latchedTarget.getVelocity();
                    double vx = v.x * 0.15;
                    double vz = v.z * 0.15;

                    // Strong vertical impulse
                    latchedTarget.setVelocity(vx, Y_LAUNCH, vz);
                    // Sync to clients (players need a packet)
                    if (latchedTarget instanceof ServerPlayerEntity sp) {
                        sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(latchedTarget));
                    }
                    // Don’t accumulate fall distance on the *way up* (they’ll still take fall damage)
                    latchedTarget.fallDistance = 0f;

                    dbg(owner, "Ender Fall • launch ✓");

                    // Finish – start cooldown
                    resetState(COOLDOWN);
                }
                return;
            }

            // If somehow not in a phase and no cooldown, allow re-trigger via canStart next tick
        }

        private void resetState(int cooldown) {
            this.phase = Phase.IDLE;
            this.phaseTicks = 0;
            this.latchedTarget = null;
            this.cd = cooldown;
        }

        private void vfxAt(ServerWorld sw, double x, double y, double z, boolean from) {
            Random r = mob.getRandom();
            int count = from ? 28 : 36;
            for (int i = 0; i < count; i++) {
                double ox = (r.nextDouble() - 0.5) * 1.2;
                double oy = (r.nextDouble() - 0.5) * 1.0;
                double oz = (r.nextDouble() - 0.5) * 1.2;
                sw.spawnParticles(ParticleTypes.PORTAL, x + ox, y + oy, z + oz, 1, 0, 0, 0, 0.0);
            }
            sw.playSound(null, BlockPos.ofFloored(x, y, z),
                    SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                    mob.getSoundCategory(),
                    1.0f,
                    from ? 0.9f + r.nextFloat()*0.2f : 1.1f + r.nextFloat()*0.2f);
        }

        private void dbg(ServerPlayerEntity owner, String msg) {
            if (!DEBUG || owner == null) return;
            owner.sendMessage(Text.literal(msg), true); // action bar
        }
    }

}
