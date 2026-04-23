package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.particles.OddParticles;
import net.seep.odd.sound.ModSounds;
import net.seep.odd.status.ModStatusEffects;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SplashPower implements Power, HoldReleasePower {

    @Override public String id() { return "splash"; }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot) || "third".equals(slot);
    }

    // Primary is a TOGGLE (simple + reliable)
    @Override public long cooldownTicks() { return 0; }

    // Secondary is HOLD; cooldown applied on release by PowerAPI.holdRelease.
    @Override public long secondaryCooldownTicks() { return 10; } // (note: 10 ticks = 0.5s)

    // Third: mode switch press (small anti-spam)
    @Override public long thirdCooldownTicks() { return 6; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier("odd", "textures/gui/abilities/splash_bubbles.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/splash_hose.png");
            case "third"     -> new Identifier("odd", "textures/gui/abilities/splash_mode.png");
            default          -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" ->
                    "Provide a buff to any entity within your area of effect, buff depends on the current MODE.";
            case "secondary" ->
                    "Fire an empowering beam that heals and provides every single mode effect at once.";
            case "third" ->
                    "Change your current MODE.";
            default -> "Splash";
        };
    }
    @Override
    public String longDescription() {
        return "Support your allies with your bubbles, and leap high with your passive speed and jump!";
    }

    @Override
    public String slotTitle(String slot) {
        return switch (slot) {
            case "primary" -> "BUBBLES";
            case "secondary" -> "WATER-RAY";
            case "third" -> "MODE SWITCH";
            default -> Power.super.slotTitle(slot);
        };
    }




    /** Call once from common init: SplashPower.init(); */
    private static boolean INITED = false;

    /** Hooks ticking + cleanup automatically, and safely boots client visuals when on client. */
    public static void init() {
        if (INITED) return;
        INITED = true;

        // Drive the power every server tick
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                serverTick(p);
            }
        });

        // Cleanup per-player maps on disconnect
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.getPlayer() != null) onLogout(handler.getPlayer().getUuid());
        });

        // Client-only init (HUD + beam + loop sounds) without classloading client classes on a dedicated server.
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            try {
                Class.forName("net.seep.odd.abilities.splash.client.SplashPowerClient")
                        .getMethod("init")
                        .invoke(null);
            } catch (Throwable t) {
                System.out.println("[Oddities][Splash] Client init failed: " + t);
            }
        }
    }

    /** Power check (NO tags). */
    public static boolean hasSplash(ServerPlayerEntity sp) {
        String current = net.seep.odd.abilities.PowerAPI.get(sp);
        return "splash".equals(current);
    }

    /* =========================
       Networking (S2C)
       ========================= */

    /** Owner-only (resource + flags) */
    public static final Identifier S2C_SPLASH_STATE  = new Identifier("odd", "splash_state");

    /** Everyone (for world visuals: aura hue + smooth hose render) */
    public static final Identifier S2C_SPLASH_VISUAL = new Identifier("odd", "splash_visual");

    /* =========================
       Mode
       ========================= */

    public enum Mode {
        LEAP(0x2EF46A, Text.literal("Leap")),
        TONGUE(0x42F5F5, Text.literal("Sharp-Tongue")),
        SKIN(0xFF66CC, Text.literal("Thick-Skin"));

        public final int rgb;
        public final Text display;

        Mode(int rgb, Text display) {
            this.rgb = rgb;
            this.display = display;
        }

        public Mode next() {
            int i = (this.ordinal() + 1) % values().length;
            return values()[i];
        }
    }

    /* =========================
       Tunables
       ========================= */

    private static final float AURA_RADIUS = 10.0f;

    private static final int RESOURCE_MAX = 1000;

    // shared meter drains
    private static final int PRIMARY_DRAIN_PER_TICK = 7;   // ~14%/sec
    private static final int HOSE_DRAIN_PER_TICK    = 16;  // drains faster
    private static final int RESOURCE_REGEN_PER_TICK = 6;  // regen

    private static final int AURA_REFRESH_EVERY_TICKS = 10;
    private static final int AURA_EFFECT_DURATION_HELD = 40;   // 2s
    private static final int AURA_EFFECT_DURATION_LINGER = 20; // 1s

    // visuals
    private static final int AURA_RING_PARTICLE_EVERY_TICKS = 10;

    private static final int CAST_SLOWNESS_AMP = 2;          // Slowness III (0=I,1=II,2=III)
    private static final int CAST_SLOWNESS_DURATION = 6;     // short refresh, hidden

    private static final double CAST_HORIZ_DAMP = 0.55;
    private static final double CAST_UP_DAMP    = 0.70;
    private static final double CAST_DOWN_DAMP  = 0.45;
    private static final double CAST_MAX_FALL_SPEED = -0.12;
    private static final double CAST_INPUT_DAMP = 0.80;

    /** Used by client render too (smooth beam like Fairy) */
    public static final int HOSE_RANGE = 18;

    private static final int HOSE_EFFECT_DURATION = 60; // refresh while hit
    private static final int HOSE_HEAL_EVERY_TICKS = 10;
    private static final float HOSE_HEAL_AMOUNT = 1.0f; // 0.5 hearts

    // Passive frog feel
    private static final int PASSIVE_REFRESH_TICKS = 20;
    private static final int PASSIVE_HIDDEN_DURATION = 30;

    // “gravity 0.8” feel when NOT casting
    private static final double PASSIVE_FALL_FACTOR = 0.80;

    // while casting: freeze fully
    private static final double CAST_VEL_EPS = 0.00001;

    /* =========================
       Per-player state
       ========================= */

    private static final class State {
        Mode mode = Mode.LEAP;

        int resource = RESOURCE_MAX;

        // Primary toggle
        boolean auraOn = false;

        int auraRefreshTimer = 0;
        int auraParticleTimer = 0;

        // beam (server-side only for hit logic)
        Vec3d beamStart = Vec3d.ZERO;
        Vec3d beamEnd = Vec3d.ZERO;
        boolean beamWasActive = false;

        // healing throttle
        int hoseHealTimer = 0;

        // prevent “hold through empty meter and instantly resume”
        boolean hoseStalled = false;

        // hit sound pacing
        int streamAppliedTimer = 0;

        // passive
        int passiveTimer = 0;

        // owner HUD/state pacing
        int syncTimer = 0;
        boolean firstSync = true;

        // broadcast visuals pacing + change detection
        int visualTimer = 0;
        boolean lastVisAura = false;
        boolean lastVisHose = false;
        int lastVisModeOrd = -1;
    }

    private static final Map<UUID, State> STATE = new Object2ObjectOpenHashMap<>();
    private static State getState(ServerPlayerEntity p) {
        return STATE.computeIfAbsent(p.getUuid(), u -> new State());
    }

    // Track players we set noGravity on, so we can safely reset on power swap
    private static final Set<UUID> SUSPENDED = new ObjectOpenHashSet<>();

    public static void onLogout(UUID playerId) {
        STATE.remove(playerId);
        SUSPENDED.remove(playerId);
    }

    /* =========================
       Powerless override hook
       ========================= */

    @Override
    public void forceDisable(ServerPlayerEntity player) {
        State s = STATE.get(player.getUuid());
        if (s == null) return;

        s.auraOn = false;
        s.hoseStalled = true;
        s.hoseHealTimer = 0;
        s.streamAppliedTimer = 0;

        clearSuspensionIfNeeded(player);
        applyGravityDamp(player, PASSIVE_FALL_FACTOR);

        if (player.getWorld() instanceof ServerWorld sw) {
            // stop world visuals immediately for everyone
            broadcastVisual(sw, player, s, false, false, true);
        }

        // owner loops/UI stop immediately
        syncState(player, s, true);
    }

    /* =========================
       Third ability: Mode switch
       ========================= */

    @Override
    public void activateThird(ServerPlayerEntity player) {
        if (!hasSplash(player)) return;
        if (player.hasStatusEffect(ModStatusEffects.POWERLESS)) {
            player.sendMessage(Text.literal("Splash: disabled (POWERLESS)."), true);
            return;
        }

        State s = getState(player);
        s.mode = s.mode.next();
        player.sendMessage(Text.literal("Mode: ").append(s.mode.display), true);

        syncState(player, s, true);
        if (player.getWorld() instanceof ServerWorld sw) {
            boolean auraActive = s.auraOn && s.resource > 0;
            boolean hoseActive = PowerAPI.isHeld(player, "secondary") && !s.hoseStalled && s.resource > 0;
            broadcastVisual(sw, player, s, auraActive, hoseActive, true);
        }
    }

    /* =========================
       Primary ability: TOGGLE
       ========================= */

    @Override
    public void activate(ServerPlayerEntity player) {
        if (!hasSplash(player)) return;
        if (player.hasStatusEffect(ModStatusEffects.POWERLESS)) {
            player.sendMessage(Text.literal("Splash: disabled (POWERLESS)."), true);
            return;
        }

        State s = getState(player);

        s.auraOn = !s.auraOn;
        if (!s.auraOn) {
            // on toggle-off: apply 1s linger one last time
            if (player.getWorld() instanceof ServerWorld sw) {
                applyAuraOnce(sw, player, s.mode, AURA_EFFECT_DURATION_LINGER);
            }
        }



        syncState(player, s, true);
        if (player.getWorld() instanceof ServerWorld sw) {
            boolean auraActive = s.auraOn && s.resource > 0;
            boolean hoseActive = PowerAPI.isHeld(player, "secondary") && !s.hoseStalled && s.resource > 0;
            broadcastVisual(sw, player, s, auraActive, hoseActive, true);
        }
    }

    /* =========================
       Secondary tap (if someone calls activateSecondary directly)
       ========================= */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {

    }

    /* =========================
       HoldReleasePower hooks (Secondary only)
       ========================= */

    @Override
    public void onHoldStart(ServerPlayerEntity player, String slot) {
        if (!hasSplash(player)) return;
        if (player.hasStatusEffect(ModStatusEffects.POWERLESS)) {

            return;
        }

        State s = getState(player);

        if ("secondary".equals(slot)) {
            s.hoseHealTimer = 0;
            s.hoseStalled = false;
            s.streamAppliedTimer = 0;

            syncState(player, s, true);
            if (player.getWorld() instanceof ServerWorld sw) {
                boolean auraActive = s.auraOn && s.resource > 0;
                boolean hoseActive = true && s.resource > 0; // will become true in tick if meter allows
                broadcastVisual(sw, player, s, auraActive, hoseActive, true);
            }
        }
    }

    @Override
    public void onHoldTick(ServerPlayerEntity player, String slot, int heldTicks) {
        // we run everything in serverTick so it’s robust even if holdTick isn’t spammed
    }

    @Override
    public void onHoldRelease(ServerPlayerEntity player, String slot, int heldTicks, boolean canceled) {
        if (!hasSplash(player)) return;
        State s = getState(player);

        if ("secondary".equals(slot)) {
            s.hoseStalled = false;
            s.beamWasActive = false;

            syncState(player, s, true);
            if (player.getWorld() instanceof ServerWorld sw) {
                boolean auraActive = s.auraOn && s.resource > 0;
                broadcastVisual(sw, player, s, auraActive, false, true);
            }
        }
    }

    /* =========================
       Server tick (auto-wired by init())
       ========================= */

    public static void serverTick(ServerPlayerEntity player) {
        // If you swapped powers while suspended, ensure cleanup + stop visuals once.
        if (!hasSplash(player)) {
            State s = STATE.get(player.getUuid());
            if (s != null) {
                // kill any leftover visuals immediately (one-shot)
                if (player.getWorld() instanceof ServerWorld sw) {
                    if (s.lastVisAura || s.lastVisHose) {
                        broadcastVisual(sw, player, s, false, false, true);
                    }
                }
                // stop owner loops quickly
                if (s.auraOn || s.beamWasActive) {
                    s.auraOn = false;
                    s.beamWasActive = false;
                    syncState(player, s, true);
                }
            }

            clearSuspensionIfNeeded(player);
            applyGravityDamp(player, PASSIVE_FALL_FACTOR);
            return;
        }

        State s = getState(player);

        // POWERLESS override (hard stop)
        if (player.hasStatusEffect(ModStatusEffects.POWERLESS)) {
            // regen meter while powerless, but never allow casting
            s.resource = Math.min(RESOURCE_MAX, s.resource + RESOURCE_REGEN_PER_TICK);

            boolean hadAny = s.auraOn || s.beamWasActive || s.lastVisAura || s.lastVisHose;
            s.auraOn = false;
            s.hoseStalled = true;
            s.hoseHealTimer = 0;
            s.streamAppliedTimer = 0;
            s.beamWasActive = false;

            clearSuspensionIfNeeded(player);
            applyGravityDamp(player, PASSIVE_FALL_FACTOR);

            if (hadAny && player.getWorld() instanceof ServerWorld sw) {
                broadcastVisual(sw, player, s, false, false, true);
            }

            // keep owner state fresh enough to stop loops/UI immediately
            s.syncTimer++;
            if (s.syncTimer >= 10) {
                s.syncTimer = 0;
                syncState(player, s, true);
            }
            return;
        }

        // First tick after state creation: sync immediately
        if (s.firstSync) {
            s.firstSync = false;
            syncState(player, s, true);
            if (player.getWorld() instanceof ServerWorld sw) {
                boolean auraActive = s.auraOn && s.resource > 0;
                boolean hoseActive = false;
                broadcastVisual(sw, player, s, auraActive, hoseActive, true);
            }
        }

        // Passive frog feel (always)
        s.passiveTimer++;
        if (s.passiveTimer >= PASSIVE_REFRESH_TICKS) {
            s.passiveTimer = 0;

            player.addStatusEffect(new StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.SPEED,
                    PASSIVE_HIDDEN_DURATION,
                    0,
                    false, false, false
            ));
            player.addStatusEffect(new StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.JUMP_BOOST,
                    PASSIVE_HIDDEN_DURATION,
                    0,
                    false, false, false
            ));
        }

        boolean secondaryHeld = PowerAPI.isHeld(player, "secondary");

        // If they released secondary, clear stall
        if (!secondaryHeld) s.hoseStalled = false;

        // Drain (shared meter) ONLY while actually casting (and not stalled)
        boolean willAuraCast = s.auraOn && s.resource > 0;
        boolean willHoseCast = secondaryHeld && !s.hoseStalled && s.resource > 0;

        int drain = 0;
        if (willAuraCast) drain += PRIMARY_DRAIN_PER_TICK;
        if (willHoseCast) drain += HOSE_DRAIN_PER_TICK;

        if (drain > 0) {
            s.resource = Math.max(0, s.resource - drain);
        } else {
            s.resource = Math.min(RESOURCE_MAX, s.resource + RESOURCE_REGEN_PER_TICK);
        }

        // If we hit 0, stop abilities safely
        if (s.resource <= 0) {
            if (s.auraOn) {
                s.auraOn = false;

            }
            if (secondaryHeld && !s.hoseStalled) {
                s.hoseStalled = true;

            }
        }

        // Recompute active flags AFTER drain
        boolean auraActive = s.auraOn && s.resource > 0;
        boolean hoseActive = secondaryHeld && !s.hoseStalled && s.resource > 0;

        // Casting suspension
        if (auraActive || hoseActive) {
            applySuspension(player);
            applyCastSlowMo(player);
        } else {
            clearSuspensionIfNeeded(player);
            applyGravityDamp(player, PASSIVE_FALL_FACTOR);
        }

        // Primary: Aura while active
        if (auraActive) {
            s.auraRefreshTimer++;
            s.auraParticleTimer++;

            if (s.auraParticleTimer >= AURA_RING_PARTICLE_EVERY_TICKS) {
                s.auraParticleTimer = 0;
                if (player.getWorld() instanceof ServerWorld sw) spawnAuraRing(sw, player, s.mode);
            }

            if (s.auraRefreshTimer >= AURA_REFRESH_EVERY_TICKS) {
                s.auraRefreshTimer = 0;
                if (player.getWorld() instanceof ServerWorld sw) {
                    applyAuraOnce(sw, player, s.mode, AURA_EFFECT_DURATION_HELD);
                }
            }
        } else {
            s.auraRefreshTimer = 0;
            s.auraParticleTimer = 0;
        }

        // Secondary: Beam while active (server hit logic)
        if (hoseActive) {
            if (player.getWorld() instanceof ServerWorld sw) {
                computeBeam(player, s);

                // heal throttle
                s.hoseHealTimer++;
                boolean doHeal = false;
                if (s.hoseHealTimer >= HOSE_HEAL_EVERY_TICKS) {
                    s.hoseHealTimer = 0;
                    doHeal = true;
                }

                boolean hitSomeone = applyBeamEffects(sw, player, s, doHeal);

                // only ModSounds for others, and played FROM THE PLAYER
                if (hitSomeone) {
                    s.streamAppliedTimer++;
                    if (s.streamAppliedTimer >= 10) {
                        s.streamAppliedTimer = 0;
                        sw.playSoundFromEntity(null, player, ModSounds.STREAM_APPLIED,
                                net.minecraft.sound.SoundCategory.PLAYERS, 0.9f, 1.0f);
                    }
                } else {
                    s.streamAppliedTimer = 0;
                }

                // beam particles: for everyone
                if ((player.age % 4) == 0) spawnBeamBubbles(sw, s.beamStart, s.beamEnd, 4);

                s.beamWasActive = true;
            }
        } else {
            s.hoseHealTimer = 0;
            s.streamAppliedTimer = 0;
            s.beamWasActive = false;
        }

        // Owner sync pacing (resource for UI + LOCAL loops)
        s.syncTimer++;
        int rate = (auraActive || hoseActive) ? 5 : 20;
        if (s.syncTimer >= rate) {
            s.syncTimer = 0;
            syncState(player, s, false);
        }

        // World visual broadcast pacing (for smooth beam render + aura hue)
        if (player.getWorld() instanceof ServerWorld sw) {
            broadcastVisual(sw, player, s, auraActive, hoseActive, false);
        }
    }

    /* =========================
       Aura helpers
       ========================= */
    private static void spawnBeamHitRainbow(ServerWorld sw, LivingEntity e, int total) {
        // total stays the same; just split across colors (10 -> 4/3/3)
        int base = total / 3;
        int rem = total - base * 3;

        int green = base + (rem > 0 ? 1 : 0);
        int aqua  = base + (rem > 1 ? 1 : 0);
        int pink  = base;

        double cx = e.getX();
        double cy = e.getBodyY(0.6);
        double cz = e.getZ();

        sw.spawnParticles(OddParticles.SPLASH_BUBBLE_GREEN, cx, cy, cz, green, 0.35, 0.35, 0.35, 0.0);
        sw.spawnParticles(OddParticles.SPLASH_BUBBLE_AQUA,  cx, cy, cz, aqua,  0.35, 0.35, 0.35, 0.0);
        sw.spawnParticles(OddParticles.SPLASH_BUBBLE_PINK,  cx, cy, cz, pink,  0.35, 0.35, 0.35, 0.0);
    }

    private static StatusEffectInstance modeEffect(Mode mode, int durationTicks) {
        return switch (mode) {
            case LEAP   -> new StatusEffectInstance(ModStatusEffects.FROG_LEAP,   durationTicks, 0, false, false, true);
            case TONGUE -> new StatusEffectInstance(ModStatusEffects.FROG_TONGUE, durationTicks, 0, false, false, true);
            case SKIN   -> new StatusEffectInstance(ModStatusEffects.FROG_SKIN,   durationTicks, 0, false, false, true);
        };
    }

    private static ParticleEffect modeBubbleParticle(Mode mode) {
        return switch (mode) {
            case LEAP   -> OddParticles.SPLASH_BUBBLE_GREEN;
            case TONGUE -> OddParticles.SPLASH_BUBBLE_AQUA;
            case SKIN   -> OddParticles.SPLASH_BUBBLE_PINK;
        };
    }

    private static void applyAuraOnce(ServerWorld sw, ServerPlayerEntity caster, Mode mode, int durationTicks) {
        Box box = new Box(
                caster.getX() - AURA_RADIUS, caster.getY() - 2, caster.getZ() - AURA_RADIUS,
                caster.getX() + AURA_RADIUS, caster.getY() + 3, caster.getZ() + AURA_RADIUS
        );

        List<LivingEntity> targets = sw.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive());
        for (LivingEntity e : targets) {
            if (e.squaredDistanceTo(caster) > (AURA_RADIUS * AURA_RADIUS)) continue;

            e.addStatusEffect(modeEffect(mode, durationTicks));
            spawnStatusBubbles(sw, e, mode, 6);
        }
    }

    private static void spawnAuraRing(ServerWorld sw, ServerPlayerEntity caster, Mode mode) {
        final int points = 36;
        final double r = AURA_RADIUS;
        final double y = caster.getY() + 0.20;

        ParticleEffect bubble = modeBubbleParticle(mode);

        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2.0) * (i / (double) points);
            double px = caster.getX() + Math.cos(a) * r;
            double pz = caster.getZ() + Math.sin(a) * r;

            sw.spawnParticles(bubble, px, y, pz, 1, 0.02, 0.01, 0.02, 0.0);
        }
    }

    private static void spawnStatusBubbles(ServerWorld sw, LivingEntity e, Mode mode, int count) {
        ParticleEffect bubble = modeBubbleParticle(mode);
        double cx = e.getX();
        double cy = e.getBodyY(0.6);
        double cz = e.getZ();

        sw.spawnParticles(bubble, cx, cy, cz, count, 0.35, 0.35, 0.35, 0.0);
    }

    /* =========================
       Beam helpers (server hit logic)
       ========================= */

    private static void computeBeam(ServerPlayerEntity player, State s) {
        Vec3d start = player.getEyePos().add(0, -0.18, 0);
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d end = start.add(look.multiply(HOSE_RANGE));

        HitResult hit = player.getWorld().raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.ANY,
                player
        ));
        if (hit.getType() != HitResult.Type.MISS) end = hit.getPos();

        s.beamStart = start;
        s.beamEnd = end;
    }

    /** @return true if at least one entity was hit this tick */
    private static boolean applyBeamEffects(ServerWorld sw, ServerPlayerEntity caster, State s, boolean doHealThisTick) {
        Vec3d start = s.beamStart;
        Vec3d end = s.beamEnd;

        Vec3d seg = end.subtract(start);
        double segLen2 = seg.lengthSquared();
        if (segLen2 < 0.0001) return false;

        boolean hitAny = false;

        Box box = new Box(start, end).expand(1.35);
        List<LivingEntity> hits = sw.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive());

        for (LivingEntity e : hits) {
            if (e == caster) continue;

            Vec3d p = e.getPos().add(0, e.getHeight() * 0.5, 0);

            double t = p.subtract(start).dotProduct(seg) / segLen2;
            t = MathHelper.clamp(t, 0.0, 1.0);

            Vec3d closest = start.add(seg.multiply(t));
            double d2 = closest.squaredDistanceTo(p);

            if (d2 <= (0.85 * 0.85)) {
                hitAny = true;

                e.addStatusEffect(new StatusEffectInstance(ModStatusEffects.FROGGY_TIME, HOSE_EFFECT_DURATION, 0, false, false, true));
                if (doHealThisTick) e.heal(HOSE_HEAL_AMOUNT);

                spawnBeamHitRainbow(sw, e, 10);
            }
        }

        return hitAny;
    }

    private static void spawnBeamBubbles(ServerWorld sw, Vec3d start, Vec3d end, int count) {
        Vec3d dir = end.subtract(start);
        double len = dir.length();
        if (len < 0.2) return;

        Vec3d n = dir.normalize();

        for (int i = 0; i < count; i++) {
            double t = sw.random.nextDouble();
            Vec3d p = start.add(n.multiply(len * t));

            double ox = (sw.random.nextDouble() - 0.5) * 0.10;
            double oy = (sw.random.nextDouble() - 0.5) * 0.10;
            double oz = (sw.random.nextDouble() - 0.5) * 0.10;

            sw.spawnParticles(ParticleTypes.BUBBLE_POP, p.x + ox, p.y + oy, p.z + oz, 1, 0, 0, 0, 0);
        }
    }

    /* =========================
       S2C state (owner only)
       ========================= */

    private static void syncState(ServerPlayerEntity player, State s, boolean force) {
        if (!ServerPlayNetworking.canSend(player, S2C_SPLASH_STATE)) return;

        boolean secondaryHeld = PowerAPI.isHeld(player, "secondary");
        boolean auraActive = s.auraOn && s.resource > 0;
        boolean hoseActive = secondaryHeld && !s.hoseStalled && s.resource > 0;

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(player.getUuid());
        buf.writeVarInt(s.mode.ordinal());
        buf.writeVarInt(s.resource);
        buf.writeVarInt(RESOURCE_MAX);
        buf.writeBoolean(auraActive);
        buf.writeBoolean(hoseActive);

        ServerPlayNetworking.send(player, S2C_SPLASH_STATE, buf);
    }

    private static void syncState(ServerPlayerEntity player, State s, boolean force, boolean ignored) {
        syncState(player, s, force);
    }

    private static void syncState(ServerPlayerEntity player, State s) {
        syncState(player, s, false);
    }

    /* =========================
       S2C visuals (everyone)
       ========================= */

    private static void broadcastVisual(ServerWorld sw, ServerPlayerEntity owner, State s, boolean auraActive, boolean hoseActive, boolean force) {
        int modeOrd = s.mode.ordinal();

        s.visualTimer++;
        int rate = (auraActive || hoseActive) ? 5 : 20;

        boolean changed =
                (auraActive != s.lastVisAura) ||
                        (hoseActive != s.lastVisHose) ||
                        (modeOrd != s.lastVisModeOrd);

        if (!force && !changed && s.visualTimer < rate) return;
        s.visualTimer = 0;

        s.lastVisAura = auraActive;
        s.lastVisHose = hoseActive;
        s.lastVisModeOrd = modeOrd;

        for (ServerPlayerEntity p : sw.getPlayers()) {
            if (!ServerPlayNetworking.canSend(p, S2C_SPLASH_VISUAL)) continue;

            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(owner.getUuid());
            buf.writeVarInt(modeOrd);
            buf.writeBoolean(auraActive);
            buf.writeBoolean(hoseActive);

            ServerPlayNetworking.send(p, S2C_SPLASH_VISUAL, buf);
        }
    }

    /* =========================
       Suspension + gravity helpers
       ========================= */

    private static void applySuspension(ServerPlayerEntity p) {
        if (p.hasVehicle()) return;

        Vec3d v = p.getVelocity();
        if (Math.abs(v.x) > CAST_VEL_EPS || Math.abs(v.y) > CAST_VEL_EPS || Math.abs(v.z) > CAST_VEL_EPS) {
            p.setVelocity(0, 0, 0);
            p.velocityDirty = true;
        } else {
            p.setVelocity(0, 0, 0);
            p.velocityDirty = true;
        }

        p.fallDistance = 0.0f;
        p.setNoGravity(true);
        SUSPENDED.add(p.getUuid());
    }

    private static void clearSuspensionIfNeeded(ServerPlayerEntity p) {
        UUID id = p.getUuid();
        if (!SUSPENDED.contains(id)) return;

        p.setNoGravity(false);
        SUSPENDED.remove(id);
    }

    private static void applyCastSlowMo(ServerPlayerEntity p) {
        if (p.hasVehicle()) return;

        p.setNoGravity(false);

        p.addStatusEffect(new StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.SLOWNESS,
                CAST_SLOWNESS_DURATION,
                CAST_SLOWNESS_AMP,
                false, false, false
        ));

        Vec3d v = p.getVelocity();

        double vy = v.y;
        if (vy > 0) vy *= CAST_UP_DAMP;
        else vy = Math.max(vy * CAST_DOWN_DAMP, CAST_MAX_FALL_SPEED);

        double vx = v.x * CAST_HORIZ_DAMP;
        double vz = v.z * CAST_HORIZ_DAMP;

        vx *= CAST_INPUT_DAMP;
        vz *= CAST_INPUT_DAMP;

        p.setVelocity(vx, vy, vz);
        p.velocityDirty = true;

        p.fallDistance = 0.0f;
    }

    private static void applyGravityDamp(LivingEntity e, double fallFactor) {
        if (e.isOnGround() || e.isClimbing() || e.isTouchingWater() || e.hasVehicle()) return;

        Vec3d v = e.getVelocity();
        if (v.y < 0.0D) {
            e.setVelocity(v.x, v.y * fallFactor, v.z);
            e.velocityDirty = true;
        }
    }
}