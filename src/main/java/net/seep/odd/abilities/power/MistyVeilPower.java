package net.seep.odd.abilities.power;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.abilities.PowerAPI;

import java.util.*;

public final class MistyVeilPower implements Power {
    @Override public String id() { return "misty_veil"; }

    @Override public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot);
    }

    // Primary is toggle (no cooldown). Secondary keeps 30s CD.
    @Override public long cooldownTicks()          { return 0; }
    @Override public long secondaryCooldownTicks() { return 30L * 20L; }
    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier("odd", "textures/gui/abilities/mist_steps.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/mist_bubble.png"); // set this texture
            default          -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }
    @Override public String slotTitle(String slot) {
        return switch (slot) {
            case "primary" -> "RAINY STEPS";
            case "secondary" -> "MIST BUBBLE";
            default -> Power.super.slotTitle(slot);
        };
    }
    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" -> "Condense a mist to glide through the air by holding space mid-air [Press abiltiy button to toggle ON/OFF]";
            case "secondary" -> "Condense a swifty and regenerative bubble on an ally, protecting them from the dangerous prey for 20 seconds";
            case "overview" -> "Mist Veil passively protects you by hiding you from dangerous entities, glide with mist steps or protect someone else with your Mist bubble!";
            default -> "Snow-caster brings ice-cold abilities to the fight: explosives and teleports at your fingertips.";
        };
    }
    @Override
    public String longDescription() {
        return """
           Mist Veil passively protects you by hiding you from dangerous entities.
           Glide with mist steps or protect someone else with your Mist bubble!
           """;
    }

    /* ===================== FEEL CONFIG ===================== */
    // Vertical-only “low-grav” (momentum-preserving)
    private static final double BASE_FALL_MULT      = 0.92;   // subtle when not hovering
    private static final double HOVER_FALL_MULT     = 0.50;   // stronger low-grav while hovering
    private static final double HOVER_GLIDE_MIN_VY  = -0.02;  // don't fall faster than this when hovering
    private static final double DOWNWARD_THRESH     = -0.08;  // only modify when clearly falling

    // Hover horizontal flow (keys-based): accelerate + steer toward WASD intent, capped
    private static final double HOVER_XZ_ACCEL      = 0.030;  // per-tick speed gain
    private static final double HOVER_XZ_MAX_SPEED  = 1.00;   // max horizontal speed while hovering
    private static final double HOVER_XZ_STEER      = 0.12;   // turn rate toward intent [0..1]

    // Slight constant speed so it doesn't feel sluggish overall
    private static final int    ALWAYS_SPEED_AMPL   = 0;      // Speed I
    private static final int    ALWAYS_SPEED_T      = 12;     // refresh cadence (ticks)

    // Aggro masking
    private static final int    PASSIVE_CLEAR_RAD   = 48;
    private static final int    AGGRESSIVE_TAG_T    = 60;     // 3s after you attack

    // Bubble secondary
    private static final int    BUBBLE_TIME         = 20 * 20;
    private static final int    BUBBLE_REGEN_LVL    = 0;      // Regeneration I

    // Client -> server input bits
    public static final int MASK_JUMP_HELD    = 1; // jump currently held
    public static final int MASK_JUMP_PRESSED = 2; // jump edge this tick

    // Sensitivity gates (less trigger-happy midair)
    private static final int HELD_FRESH_WINDOW_T        = 6; // ticks to consider "held" fresh
    private static final int PRESSED_FRESH_WINDOW_T     = 2; // ticks to consider "pressed" fresh
    private static final int MIN_AIR_TICKS_FOR_HOLD_ARM = 6; // must be this long in air before hold can arm
    private static final int MIN_TICKS_AFTER_AIR_FOR_PRESS_ARM = 3; // press must be this long after takeoff

    // Close-quarters suppress + arrow nullify
    private static final double CLOSE_SUPPRESS_RANGE = 2.5;  // meters
    private static final double ARROW_RADIUS         = 3.5;
    private static final double ARROW_DOT_MIN        = 0.7;  // arrow roughly flying toward player

    /* ===================== STATE ===================== */
    private static final class State {
        int  lastInputMask = 0;
        int  lastInputAt   = 0;       // world time of last C2S input
        int  tickCounter   = 0;

        // movement intent from client (local player-space: strafe X, forward Z)
        float intentX = 0f;           // [-1..1] A/D
        float intentZ = 0f;           // [-1..1] S/W
        int   intentAt = 0;           // time intent was last updated

        // stealth window
        boolean flaggedAggressive = false;
        int     aggressiveTicksLeft = 0;

        // hover on/off (primary toggles this)
        boolean hoverEnabled = true;

        // hover gating (midair-arm)
        boolean wasOnGround   = true; // previous tick
        int     airTicks      = 0;    // ticks since leaving ground
        int     leftGroundAt  = -1;   // world time we left ground
        boolean heldAtTakeoff = false;
        boolean hoverActive   = false; // currently hovering (requires jump held)
    }
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static State S(ServerPlayerEntity p) { return STATES.computeIfAbsent(p.getUuid(), u -> new State()); }

    // Secondary: bubbled targets with remaining ticks
    private static final Map<UUID, Integer> BUBBLED = new HashMap<>();

    /* ===================== PASSIVE HOOK ===================== */
    static {
        AttackEntityCallback.EVENT.register((player, world, hand, target, hit) -> {
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            Power current = Powers.get(PowerAPI.get(sp));
            if (!(current instanceof MistyVeilPower)) return ActionResult.PASS;
            State st = S(sp);
            st.flaggedAggressive = true;
            st.aggressiveTicksLeft = AGGRESSIVE_TAG_T;
            return ActionResult.PASS;
        });
    }

    /* ===================== PRIMARY (TOGGLE HOVER) ===================== */
    @Override
    public void activate(ServerPlayerEntity player) {
        State st = S(player);
        st.hoverEnabled = !st.hoverEnabled;
        st.hoverActive = false; // reset midair state immediately
        player.sendMessage(Text.literal(st.hoverEnabled ? "Misty Hover: ON" : "Misty Hover: OFF"), true); // actionbar
    }

    /* ===================== SECONDARY (bubble target) ===================== */
    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        Entity target = raycastTarget(player, 20.0);
        if (!(target instanceof LivingEntity le)) return;

        BUBBLED.put(le.getUuid(), BUBBLE_TIME);

        if (player.getWorld() instanceof ServerWorld sw) {
            var bubble = new net.seep.odd.entity.misty.MistyBubbleEntity(sw, le.getUuid(), BUBBLE_TIME);
            bubble.refreshPositionAndAngles(le.getX(), le.getY(), le.getZ(), 0, 0);
            sw.spawnEntity(bubble);
        }

        le.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 40, BUBBLE_REGEN_LVL, true, true, true));
        le.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 0, true, true, true));
    }

    /* ===================== SERVER TICK ===================== */
    public static void serverTick(ServerPlayerEntity p) {
        World w = p.getWorld();
        if (!(w instanceof ServerWorld sw)) return;
        if (!PowerAPI.has(p) || !(Powers.get(PowerAPI.get(p)) instanceof MistyVeilPower)) return;

        State st = S(p);
        int now = (int) sw.getTime();
        st.tickCounter++;

        boolean onGround = p.isOnGround();

        // --- input ---
        boolean jumpHeld      = (st.lastInputMask & MASK_JUMP_HELD)    != 0 && (now - st.lastInputAt) <= HELD_FRESH_WINDOW_T;
        boolean jumpPressed   = (st.lastInputMask & MASK_JUMP_PRESSED) != 0 && (now - st.lastInputAt) <= PRESSED_FRESH_WINDOW_T;
        boolean intentFresh   = (now - st.intentAt) <= 8;

        // --- track air state ---
        if (onGround) {
            st.airTicks = 0;
            st.leftGroundAt = -1;
            st.hoverActive = false;
        } else {
            if (st.wasOnGround) {
                st.airTicks = 1;
                st.leftGroundAt = now;
                st.heldAtTakeoff = jumpHeld;
                st.hoverActive = false; // must arm in-air
            } else {
                st.airTicks++;
            }
        }

        // --- midair-only hover arming (if enabled) ---
        if (st.hoverEnabled && !onGround) {
            boolean pressMidair = jumpPressed && st.leftGroundAt >= 0 && st.lastInputAt >= (st.leftGroundAt + MIN_TICKS_AFTER_AIR_FOR_PRESS_ARM);
            boolean holdMidair  = jumpHeld && !st.heldAtTakeoff && st.airTicks >= MIN_AIR_TICKS_FOR_HOLD_ARM;

            if (!st.hoverActive && (pressMidair || holdMidair)) st.hoverActive = true;
            if (st.hoverActive && !jumpHeld) st.hoverActive = false; // release when you let go
        } else {
            st.hoverActive = false;
        }

        // ===== Movement: low-grav + keys-flow (only when enabled) =====
        if (st.hoverEnabled && !onGround && !p.isClimbing() && !p.isTouchingWater() && !p.isInLava() && !p.isFallFlying()) {
            Vec3d v = p.getVelocity();
            boolean hover = st.hoverActive;

            double vx = v.x, vz = v.z, vy = v.y;

            // Vertical — scale only downward motion (momentum-preserving)
            if (vy < DOWNWARD_THRESH) {
                vy = vy * (hover ? HOVER_FALL_MULT : BASE_FALL_MULT);
            } else if (hover && vy < 0) {
                vy = Math.max(vy, HOVER_GLIDE_MIN_VY); // soft glide floor
            }

            // Horizontal — WASD intent (if fresh) while hovering
            if (hover && intentFresh && !p.horizontalCollision) {
                // Local to world: forward/left on XZ from yaw
                Vec3d look = p.getRotationVec(1.0f);
                Vec3d fwd = new Vec3d(look.x, 0, look.z);
                double flen = fwd.length();
                if (flen < 1e-6) fwd = new Vec3d(0, 0, 1); else fwd = fwd.multiply(1.0 / flen);
                Vec3d left = new Vec3d(-fwd.z, 0, fwd.x);

                // Intent in world space (normalized)
                Vec3d intent = left.multiply(st.intentX).add(fwd.multiply(st.intentZ));
                double ilen = intent.length();
                if (ilen > 1e-6) intent = intent.multiply(1.0 / ilen);

                // Current horizontal
                Vec3d vXZ = new Vec3d(vx, 0, vz);
                double speed = vXZ.length();
                Vec3d dir = speed > 1e-6 ? vXZ.multiply(1.0 / speed) : intent;

                // Steer + speed gain up to cap
                dir = dir.lerp(intent, HOVER_XZ_STEER);
                double dlen = dir.length();
                if (dlen > 1e-6) dir = dir.multiply(1.0 / dlen);

                double targetSpeed = Math.min(speed + HOVER_XZ_ACCEL, HOVER_XZ_MAX_SPEED);
                Vec3d vXZnew = dir.multiply(targetSpeed);
                vx = vXZnew.x; vz = vXZnew.z;
            }

            // Apply
            if (vy != v.y || vx != v.x || vz != v.z) {
                p.setVelocity(vx, vy, vz);
                p.velocityModified = true;
                p.fallDistance = 0;
            }

            // Blue rain while hovering
            if (hover) {
                for (int i = 0; i < 6; i++) {
                    double ox = (p.getRandom().nextDouble() - 0.5) * 0.7;
                    double oz = (p.getRandom().nextDouble() - 0.5) * 0.7;
                    double y  = p.getY() - 0.1 - p.getRandom().nextDouble() * 0.6;
                    sw.spawnParticles(ParticleTypes.DRIPPING_WATER, p.getX() + ox, y, p.getZ() + oz, 1, 0, 0, 0, 0.0);
                    sw.spawnParticles(ParticleTypes.FALLING_WATER,  p.getX() + ox, y, p.getZ() + oz, 0, 0, 0, 0, 0.0);
                }
            }
        }

        // ===== Always-on slight speed =====
        if ((st.tickCounter % ALWAYS_SPEED_T) == 0) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, ALWAYS_SPEED_T + 2, ALWAYS_SPEED_AMPL, true, false, false));
        }

        // ===== Passive stealth unless recently aggressive =====
        if (st.aggressiveTicksLeft > 0) {
            st.aggressiveTicksLeft--;
            if (st.aggressiveTicksLeft == 0) st.flaggedAggressive = false;
        }
        if (!st.flaggedAggressive) {
            // Clear targets in a big radius
            Box big = p.getBoundingBox().expand(PASSIVE_CLEAR_RAD);
            for (HostileEntity mob : sw.getEntitiesByClass(HostileEntity.class, big, m -> m.isAlive())) {
                if (mob.getTarget() == p) {
                    mob.setTarget(null);
                    mob.setAttacking(false);
                }
            }

            // Close-quarters: fully suppress attacks (no nudging)
            Box close = p.getBoundingBox().expand(CLOSE_SUPPRESS_RANGE);
            for (HostileEntity mob : sw.getEntitiesByClass(HostileEntity.class, close, m -> m.isAlive())) {
                mob.setAttacking(false);
                if (mob.getNavigation() != null) mob.getNavigation().stop();
                if (mob.getTarget() == p) mob.setTarget(null);

                // wipe brain memories so they don't instantly re-acquire
                var brain = mob.getBrain();
                if (brain != null) {
                    brain.forget(net.minecraft.entity.ai.brain.MemoryModuleType.ATTACK_TARGET);
                    brain.forget(net.minecraft.entity.ai.brain.MemoryModuleType.ANGRY_AT);
                    brain.forget(net.minecraft.entity.ai.brain.MemoryModuleType.HURT_BY);
                }
            }

            // Nullify hostile arrows clearly flying toward the player
            Box arrowBox = p.getBoundingBox().expand(ARROW_RADIUS);
            for (PersistentProjectileEntity proj : sw.getEntitiesByClass(PersistentProjectileEntity.class, arrowBox, e -> e.isAlive())) {
                Entity owner = proj.getOwner();
                if (!(owner instanceof HostileEntity)) continue;

                Vec3d toPlayer = p.getPos().add(0, p.getStandingEyeHeight() * 0.5, 0).subtract(proj.getPos());
                Vec3d vel = proj.getVelocity();
                double vlen = vel.length();
                if (vlen <= 1e-6) continue;
                double dot = vel.normalize().dotProduct(toPlayer.normalize());
                if (dot >= ARROW_DOT_MIN) {
                    proj.discard(); // poof
                }
            }
        }

        // ===== Secondary: tick bubbles =====
        if (!BUBBLED.isEmpty()) {
            List<UUID> toRemove = new ArrayList<>();
            for (var e : BUBBLED.entrySet()) {
                UUID id = e.getKey();
                int left = e.getValue();
                Entity ent = sw.getEntity(id);
                if (!(ent instanceof LivingEntity le) || left <= 0) {
                    toRemove.add(id); continue;
                }
                if ((left % 10) == 0) {
                    le.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 12, BUBBLE_REGEN_LVL, true, true, true));
                    le.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 12, 1, true, true, true));
                }
                if (!(ent instanceof ServerPlayerEntity)) {
                    Box b = ent.getBoundingBox().expand(32);
                    for (HostileEntity mob : sw.getEntitiesByClass(HostileEntity.class, b, m -> true)) {
                        if (mob.getTarget() == le) {
                            mob.setTarget(null);
                            mob.setAttacking(false);
                        }
                    }
                }
                e.setValue(left - 1);
            }
            for (UUID r : toRemove) BUBBLED.remove(r);
        }

        st.wasOnGround = onGround;
    }

    /* ===================== INPUT (from client) ===================== */
    // New: include movement intent (strafeX, forwardZ) in player-space, clamped [-1..1]
    public static void onClientInput(ServerPlayerEntity player, int mask, float strafe, float forward) {
        State st = S(player);
        st.lastInputMask = mask;
        st.lastInputAt   = (int) player.getWorld().getTime();

        // clamp + normalize to length 1
        float ix = Math.max(-1f, Math.min(1f, strafe));
        float iz = Math.max(-1f, Math.min(1f, forward));
        float len = (float)Math.sqrt(ix*ix + iz*iz);
        if (len > 1f) { ix /= len; iz /= len; }

        st.intentX = ix;
        st.intentZ = iz;
        st.intentAt = st.lastInputAt;
    }

    // Back-compat (mask-only packet)
    public static void onClientInput(ServerPlayerEntity player, int mask) {
        onClientInput(player, mask, 0f, 0f);
    }

    /* ===================== Helpers ===================== */
    private static Entity raycastTarget(ServerPlayerEntity p, double range) {
        Vec3d eye  = p.getCameraPosVec(1.0f);
        Vec3d look = p.getRotationVec(1.0f);
        Vec3d end  = eye.add(look.multiply(range));
        Box box = p.getBoundingBox().stretch(look.multiply(range)).expand(1.0D);

        EntityHitResult hit = net.minecraft.entity.projectile.ProjectileUtil.raycast(
                p, eye, end, box,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != p,
                range * range
        );
        return hit != null ? hit.getEntity() : null;
    }
}
