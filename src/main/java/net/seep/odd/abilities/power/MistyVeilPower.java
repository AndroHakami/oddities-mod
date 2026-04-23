// FILE: src/main/java/net/seep/odd/abilities/power/MistyVeilPower.java
package net.seep.odd.abilities.power;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.net.MistyNet; // ✅ hover overlay sync
import net.seep.odd.status.ModStatusEffects;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class MistyVeilPower implements Power {
    @Override public String id() { return "misty_veil"; }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot);
    }

    @Override public long cooldownTicks()          { return 0; }
    @Override public long secondaryCooldownTicks() { return 10L * 20L; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier("odd", "textures/gui/abilities/misty_steps.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/misty_bubble.png");
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
            case "primary" -> "Condense a mist to glide through the air.";
            case "secondary" -> "Condense a misty bubble on the targeted entity, providing them a temporary mist veil along side regeneration and speed!";
            default -> "Misty Veil";
        };
    }

    @Override
    public String longDescription() {
        return """
The Mist Veil protects you from hostile mobs vision, protect your allies and navigate the world with ease using mist powered abilites!
           """;
    }

    /* ===================== FEEL CONFIG ===================== */

    private static final double BASE_FALL_MULT      = 0.92;
    private static final double HOVER_FALL_MULT     = 0.50;
    private static final double HOVER_GLIDE_MIN_VY  = -0.02;
    private static final double DOWNWARD_THRESH     = -0.08;

    private static final double HOVER_XZ_ACCEL      = 0.030;
    private static final double HOVER_XZ_MAX_SPEED  = 1.00;
    private static final double HOVER_XZ_STEER      = 0.12;

    private static final int    ALWAYS_SPEED_AMPL   = 0;
    private static final int    ALWAYS_SPEED_T      = 12;

    private static final int    BUBBLE_TIME         = 20 * 20;
    private static final int    BUBBLE_REGEN_LVL    = 0;

    public static final int MASK_JUMP_HELD    = 1;
    public static final int MASK_JUMP_PRESSED = 2;

    private static final int HELD_FRESH_WINDOW_T        = 6;
    private static final int PRESSED_FRESH_WINDOW_T     = 2;
    private static final int MIN_AIR_TICKS_FOR_HOLD_ARM = 6;
    private static final int MIN_TICKS_AFTER_AIR_FOR_PRESS_ARM = 3;

    /* ===================== POWERLESS override helpers ===================== */

    private static final Map<UUID, Long> WARN_UNTIL = new HashMap<>();

    public static boolean isPowerless(ServerPlayerEntity p) {
        return p != null && p.hasStatusEffect(ModStatusEffects.POWERLESS);
    }

    private static void warnPowerlessOncePerSec(ServerPlayerEntity p) {
        if (p == null) return;
        long now = p.getWorld().getTime();
        long next = WARN_UNTIL.getOrDefault(p.getUuid(), 0L);
        if (now < next) return;
        WARN_UNTIL.put(p.getUuid(), now + 20);
        p.sendMessage(net.minecraft.text.Text.literal("§cYou are powerless."), true);
    }

    /* ===================== STATE ===================== */

    private static final class State {
        int  lastInputMask = 0;
        int  lastInputAt   = 0;
        int  tickCounter   = 0;

        float intentX = 0f;
        float intentZ = 0f;
        int   intentAt = 0;

        boolean hoverEnabled = true;

        boolean wasOnGround   = true;
        int     airTicks      = 0;
        int     leftGroundAt  = -1;
        boolean heldAtTakeoff = false;
        boolean hoverActive   = false;

        boolean lastSentEnabled = false;
        boolean lastSentActive  = false;
        int     lastSentAt      = -999999;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static State S(ServerPlayerEntity p) { return STATES.computeIfAbsent(p.getUuid(), u -> new State()); }

    private static void syncHoverOverlay(ServerPlayerEntity p, State st, boolean enabled, boolean active) {
        int now = (int) p.getWorld().getTime();
        boolean changed = (enabled != st.lastSentEnabled) || (active != st.lastSentActive);
        boolean stale   = (now - st.lastSentAt) >= 20;
        if (!changed && !stale) return;

        st.lastSentEnabled = enabled;
        st.lastSentActive  = active;
        st.lastSentAt      = now;

        MistyNet.sendHoverState(p, enabled, active);
    }

    /* ===================== BUBBLE AGGRO FIX (amp=1) ===================== */

    private static final class BubbleAggro {
        boolean wasBubble = false;          // last tick bubble state
        int lastScanAt = -999999;           // tick throttling
        final HashSet<UUID> mobs = new HashSet<>();
    }

    private static final Map<UUID, BubbleAggro> BUBBLE = new HashMap<>();
    private static final int AGGRO_SCAN_PERIOD = 10;     // every 0.5s
    private static final double AGGRO_SCAN_R = 24.0;     // radius to remember attackers
    private static final double RESTORE_MAX_R = 40.0;    // only restore if still nearby-ish

    private static boolean hasBubbleVeil(ServerPlayerEntity p) {
        var mv = p.getStatusEffect(ModStatusEffects.MIST_VEIL);
        return mv != null && mv.getAmplifier() == 1;
    }

    private static void rememberCurrentAggressors(ServerWorld sw, ServerPlayerEntity protectedPlayer) {
        BubbleAggro st = BUBBLE.computeIfAbsent(protectedPlayer.getUuid(), u -> new BubbleAggro());
        st.wasBubble = true;
        st.lastScanAt = (int) sw.getTime();

        Box box = protectedPlayer.getBoundingBox().expand(AGGRO_SCAN_R);
        for (MobEntity mob : sw.getEntitiesByClass(MobEntity.class, box, m -> m != null && m.isAlive())) {
            if (mob.getTarget() == protectedPlayer) {
                st.mobs.add(mob.getUuid());
            }
        }
    }

    private static void rememberAggressorFromAttack(ServerPlayerEntity protectedPlayer, LivingEntity target) {
        if (!(target instanceof MobEntity mob)) return;
        BubbleAggro st = BUBBLE.computeIfAbsent(protectedPlayer.getUuid(), u -> new BubbleAggro());
        st.mobs.add(mob.getUuid());
    }

    private static void restoreAggroOnce(ServerWorld sw, ServerPlayerEntity p, BubbleAggro st) {
        if (st == null || st.mobs.isEmpty()) return;
        if (!p.isAlive() || p.isSpectator() || p.isCreative()) return;

        double maxD2 = RESTORE_MAX_R * RESTORE_MAX_R;

        for (UUID id : st.mobs) {
            Entity e = sw.getEntity(id);
            if (!(e instanceof MobEntity mob) || !mob.isAlive()) continue;

            if (mob.squaredDistanceTo(p) > maxD2) continue;

            // If it already has a target, don't overwrite unless it was us
            if (mob.getTarget() != null && mob.getTarget() != p) continue;

            mob.setTarget(p);
            mob.setAttacker(p);

            if (mob instanceof Angerable ang) {
                ang.setAngryAt(p.getUuid());
                ang.chooseRandomAngerTime();
            }
        }
    }

    private static void bubbleAggroServerTick(MinecraftServer server) {
        var players = server.getPlayerManager().getPlayerList();
        HashSet<UUID> live = new HashSet<>();
        for (ServerPlayerEntity p : players) {
            live.add(p.getUuid());

            boolean bubble = hasBubbleVeil(p);
            BubbleAggro st = BUBBLE.get(p.getUuid());
            boolean was = st != null && st.wasBubble;

            // while bubble is active, keep remembering mobs targeting them (covers “was targeting before bubble” cases)
            if (bubble && p.getWorld() instanceof ServerWorld sw) {
                if (st == null) st = new BubbleAggro();
                int now = (int) sw.getTime();
                if (now - st.lastScanAt >= AGGRO_SCAN_PERIOD) {
                    st.lastScanAt = now;
                    Box box = p.getBoundingBox().expand(AGGRO_SCAN_R);
                    for (MobEntity mob : sw.getEntitiesByClass(MobEntity.class, box, m -> m != null && m.isAlive())) {
                        if (mob.getTarget() == p) st.mobs.add(mob.getUuid());
                    }
                }
                st.wasBubble = true;
                BUBBLE.put(p.getUuid(), st);
                continue;
            }

            // bubble ended this tick -> restore aggro once
            if (was && !bubble && p.getWorld() instanceof ServerWorld sw) {
                restoreAggroOnce(sw, p, st);
                st.mobs.clear();
                st.wasBubble = false;
            }

            // cleanup
            if (!bubble && st != null && !st.wasBubble && st.mobs.isEmpty()) {
                BUBBLE.remove(p.getUuid());
            }
        }

        // drop offline entries
        Iterator<UUID> it = BUBBLE.keySet().iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            if (!live.contains(id)) it.remove();
        }
    }

    /* ===================== hooks ===================== */
    static {
        // ✅ Global server tick for bubble-aggro fix (covers bubbled allies too)
        ServerTickEvents.END_SERVER_TICK.register(MistyVeilPower::bubbleAggroServerTick);

        AttackEntityCallback.EVENT.register((player, world, hand, target, hit) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!(target instanceof LivingEntity leTarget)) return ActionResult.PASS;

            // If they have Mist Veil (amp 0 = normal/self), break it for 10s.
            var mv = sp.getStatusEffect(ModStatusEffects.MIST_VEIL);
            if (mv != null && mv.getAmplifier() == 0) {
                sp.removeStatusEffect(ModStatusEffects.MIST_VEIL);
                sp.addStatusEffect(new StatusEffectInstance(
                        ModStatusEffects.MIST_VEIL_BROKEN,
                        20 * 10,
                        0,
                        true,
                        false,
                        false
                ));
            }

            // ✅ If they are bubbled (amp 1), remember what they hit so it can aggro after bubble ends
            if (mv != null && mv.getAmplifier() == 1) {
                rememberAggressorFromAttack(sp, leTarget);
            }

            return ActionResult.PASS;
        });
    }

    @Override
    public void onAssigned(ServerPlayerEntity player) {
        State st = S(player);
        st.hoverActive = false;
        st.wasOnGround = true;
        syncHoverOverlay(player, st, st.hoverEnabled, false);
    }

    /* ===================== PRIMARY (TOGGLE HOVER) ===================== */

    @Override
    public void activate(ServerPlayerEntity player) {
        if (isPowerless(player)) {
            warnPowerlessOncePerSec(player);
            return;
        }

        State st = S(player);
        st.hoverEnabled = !st.hoverEnabled;
        st.hoverActive = false;

        if (player.getWorld() instanceof ServerWorld sw) {
            if (st.hoverEnabled) {
                sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.65f, 1.45f);
            } else {
                sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundCategory.PLAYERS, 0.55f, 0.75f);
            }
        }

        syncHoverOverlay(player, st, st.hoverEnabled, false);
    }

    /* ===================== SECONDARY (bubble target) ===================== */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (isPowerless(player)) {
            warnPowerlessOncePerSec(player);
            return;
        }

        Entity t = raycastTarget(player, 24.0);

        if (!(t instanceof LivingEntity le) || le == player) {
            if (player.getWorld() instanceof ServerWorld sw) {
                sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_BOAT_PADDLE_WATER, SoundCategory.PLAYERS, 0.45f, 0.65f);
            }
            return;
        }

        if (player.getWorld() instanceof ServerWorld sw) {
            var bubble = new net.seep.odd.entity.misty.MistyBubbleEntity(sw, le.getUuid(), BUBBLE_TIME);
            bubble.refreshPositionAndAngles(le.getX(), le.getY(), le.getZ(), 0, 0);
            sw.spawnEntity(bubble);

            sw.playSound(null, le.getX(), le.getY(), le.getZ(),
                    SoundEvents.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT,
                    SoundCategory.PLAYERS, 0.9f, 1.15f);

            sw.spawnParticles(ParticleTypes.SPLASH, le.getX(), le.getY() + 0.9, le.getZ(), 10, 0.35, 0.25, 0.35, 0.02);

            // ✅ If bubbling a player, snapshot current aggressors immediately
            if (le instanceof ServerPlayerEntity prot) {
                rememberCurrentAggressors(sw, prot);
            }
        }

        le.removeStatusEffect(ModStatusEffects.MIST_VEIL_BROKEN);
        le.addStatusEffect(new StatusEffectInstance(ModStatusEffects.MIST_VEIL, BUBBLE_TIME, 1, true, false, true));
        le.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, BUBBLE_TIME, BUBBLE_REGEN_LVL, true, true, true));
        le.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED,        BUBBLE_TIME, 0,               true, true, true));
    }

    @Override
    public void forceDisable(ServerPlayerEntity player) {
        State st = STATES.get(player.getUuid());
        if (st != null) st.hoverActive = false;

        if (st != null) syncHoverOverlay(player, st, false, false);
        else MistyNet.sendHoverState(player, false, false);

        var mv = player.getStatusEffect(ModStatusEffects.MIST_VEIL);
        if (mv != null && mv.getAmplifier() == 0) {
            player.removeStatusEffect(ModStatusEffects.MIST_VEIL);
        }
    }

    /* ===================== SERVER TICK (movement only for power owners) ===================== */

    public static void serverTick(ServerPlayerEntity p) {
        World w = p.getWorld();
        if (!(w instanceof ServerWorld sw)) return;

        if (!PowerAPI.has(p) || !(Powers.get(PowerAPI.get(p)) instanceof MistyVeilPower)) return;

        State st = S(p);
        int now = (int) sw.getTime();
        st.tickCounter++;

        if (isPowerless(p)) {
            st.hoverActive = false;
            syncHoverOverlay(p, st, false, false);

            var mv = p.getStatusEffect(ModStatusEffects.MIST_VEIL);
            if (mv != null && mv.getAmplifier() == 0) p.removeStatusEffect(ModStatusEffects.MIST_VEIL);

            st.wasOnGround = p.isOnGround();
            return;
        }

        boolean onGround = p.isOnGround();

        boolean jumpHeld    = (st.lastInputMask & MASK_JUMP_HELD)    != 0 && (now - st.lastInputAt) <= HELD_FRESH_WINDOW_T;
        boolean jumpPressed = (st.lastInputMask & MASK_JUMP_PRESSED) != 0 && (now - st.lastInputAt) <= PRESSED_FRESH_WINDOW_T;
        boolean intentFresh = (now - st.intentAt) <= 8;

        if (onGround) {
            st.airTicks = 0;
            st.leftGroundAt = -1;
            st.hoverActive = false;
        } else {
            if (st.wasOnGround) {
                st.airTicks = 1;
                st.leftGroundAt = now;
                st.heldAtTakeoff = jumpHeld;
                st.hoverActive = false;
            } else {
                st.airTicks++;
            }
        }

        if (st.hoverEnabled && !onGround) {
            boolean pressMidair = jumpPressed && st.leftGroundAt >= 0 && st.lastInputAt >= (st.leftGroundAt + MIN_TICKS_AFTER_AIR_FOR_PRESS_ARM);
            boolean holdMidair  = jumpHeld && !st.heldAtTakeoff && st.airTicks >= MIN_AIR_TICKS_FOR_HOLD_ARM;

            if (!st.hoverActive && (pressMidair || holdMidair)) st.hoverActive = true;
            if (st.hoverActive && !jumpHeld) st.hoverActive = false;
        } else {
            st.hoverActive = false;
        }

        syncHoverOverlay(p, st, st.hoverEnabled, st.hoverActive);

        if (!p.hasStatusEffect(ModStatusEffects.MIST_VEIL_BROKEN)) {
            ensureMistVeil(p, 0);
        } else {
            var mv = p.getStatusEffect(ModStatusEffects.MIST_VEIL);
            if (mv != null && mv.getAmplifier() == 0) p.removeStatusEffect(ModStatusEffects.MIST_VEIL);
        }

        if (st.hoverEnabled && !onGround && !p.isClimbing() && !p.isTouchingWater() && !p.isInLava() && !p.isFallFlying()) {
            Vec3d v = p.getVelocity();
            boolean hover = st.hoverActive;

            double vx = v.x, vz = v.z, vy = v.y;

            if (vy < DOWNWARD_THRESH) {
                vy = vy * (hover ? HOVER_FALL_MULT : BASE_FALL_MULT);
            } else if (hover && vy < 0) {
                vy = Math.max(vy, HOVER_GLIDE_MIN_VY);
            }

            if (hover && intentFresh && !p.horizontalCollision) {
                Vec3d look = p.getRotationVec(1.0f);
                Vec3d fwd = new Vec3d(look.x, 0, look.z);
                double flen = fwd.length();
                if (flen < 1e-6) fwd = new Vec3d(0, 0, 1); else fwd = fwd.multiply(1.0 / flen);
                Vec3d left = new Vec3d(-fwd.z, 0, fwd.x);

                Vec3d intent = left.multiply(st.intentX).add(fwd.multiply(st.intentZ));
                double ilen = intent.length();
                if (ilen > 1e-6) intent = intent.multiply(1.0 / ilen);

                Vec3d vXZ = new Vec3d(vx, 0, vz);
                double speed = vXZ.length();
                Vec3d dir = speed > 1e-6 ? vXZ.multiply(1.0 / speed) : intent;

                dir = dir.lerp(intent, HOVER_XZ_STEER);
                double dlen = dir.length();
                if (dlen > 1e-6) dir = dir.multiply(1.0 / dlen);

                double targetSpeed = Math.min(speed + HOVER_XZ_ACCEL, HOVER_XZ_MAX_SPEED);
                Vec3d vXZnew = dir.multiply(targetSpeed);
                vx = vXZnew.x; vz = vXZnew.z;
            }

            if (vy != v.y || vx != v.x || vz != v.z) {
                p.setVelocity(vx, vy, vz);
                p.velocityModified = true;
                p.fallDistance = 0;
            }

            if (hover) {
                for (int i = 0; i < 6; i++) {
                    double ox = (p.getRandom().nextDouble() - 0.5) * 0.7;
                    double oz = (p.getRandom().nextDouble() - 0.5) * 0.7;
                    double y  = p.getY() - 0.1 - p.getRandom().nextDouble() * 0.6;
                    sw.spawnParticles(ParticleTypes.DRIPPING_WATER, p.getX() + ox, y, p.getZ() + oz, 1, 0, 0, 0, 0.0);
                }
            }
        }

        if ((st.tickCounter % ALWAYS_SPEED_T) == 0) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, ALWAYS_SPEED_T + 2, ALWAYS_SPEED_AMPL, true, false, false));
        }

        st.wasOnGround = onGround;
    }

    private static void ensureMistVeil(ServerPlayerEntity p, int amp) {
        var cur = p.getStatusEffect(ModStatusEffects.MIST_VEIL);
        if (cur != null && cur.getAmplifier() > amp) return;

        if (cur == null || cur.getAmplifier() != amp || cur.getDuration() < 25) {
            p.addStatusEffect(new StatusEffectInstance(
                    ModStatusEffects.MIST_VEIL,
                    60,
                    amp,
                    true,
                    false,
                    false
            ));
        }
    }

    /* ===================== INPUT (from client) ===================== */

    public static void onClientInput(ServerPlayerEntity player, int mask, float strafe, float forward) {
        State st = S(player);
        st.lastInputMask = mask;
        st.lastInputAt   = (int) player.getWorld().getTime();

        float ix = Math.max(-1f, Math.min(1f, strafe));
        float iz = Math.max(-1f, Math.min(1f, forward));
        float len = (float)Math.sqrt(ix*ix + iz*iz);
        if (len > 1f) { ix /= len; iz /= len; }

        st.intentX = ix;
        st.intentZ = iz;
        st.intentAt = st.lastInputAt;
    }

    public static void onClientInput(ServerPlayerEntity player, int mask) {
        onClientInput(player, mask, 0f, 0f);
    }

    /* ===================== target helper ===================== */

    private static Entity raycastTarget(ServerPlayerEntity p, double range) {
        Vec3d eye  = p.getCameraPosVec(1.0f);
        Vec3d look = p.getRotationVec(1.0f);
        Vec3d end  = eye.add(look.multiply(range));

        Box box = p.getBoundingBox().stretch(look.multiply(range)).expand(2.0D);

        EntityHitResult hit = net.minecraft.entity.projectile.ProjectileUtil.raycast(
                p, eye, end, box,
                e -> e instanceof LivingEntity le && le.isAlive() && !e.isSpectator() && e != p,
                range * range
        );
        return hit != null ? hit.getEntity() : null;
    }
}