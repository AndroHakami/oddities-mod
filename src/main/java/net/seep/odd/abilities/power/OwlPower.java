// FILE: src/main/java/net/seep/odd/abilities/power/OwlPower.java
package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.sound.ModSounds;
import net.seep.odd.status.ModStatusEffects;

import java.util.List;
import java.util.UUID;

public final class OwlPower implements Power {

    // ===== Packets =====
    public static final Identifier OWL_TOGGLE_FLIGHT_C2S   = new Identifier(Oddities.MOD_ID, "owl_toggle_flight_c2s");
    public static final Identifier OWL_STATE_S2C           = new Identifier(Oddities.MOD_ID, "owl_state_s2c");
    public static final Identifier OWL_METER_S2C           = new Identifier(Oddities.MOD_ID, "owl_meter_s2c");
    public static final Identifier OWL_SONAR_S2C           = new Identifier(Oddities.MOD_ID, "owl_sonar_s2c");
    public static final Identifier OWL_SONAR_VISION_S2C    = new Identifier(Oddities.MOD_ID, "owl_sonar_vision_s2c");

    // ===== Flight tuning =====
    public static final int   FLIGHT_METER_MAX_TICKS       = 10 * 10;  // 10s
    public static final float FLIGHT_DRAIN_PER_TICK        = 1.0f;
    public static final float FLIGHT_RECHARGE_PER_TICK     = 0.35f;

    public static final float FLIGHT_PUSH_PER_TICK         = 0.060f;   // slightly stronger
    public static final double FLIGHT_MAX_HORIZ_SPEED      = 1.9;      // horizontal cap (NOT total cap)

    // NEW: climb authority (this is what makes “fly up” feel powered)
    private static final double FLIGHT_LIFT_BASE           = 0.020;    // always helps a bit
    private static final double FLIGHT_LIFT_LOOK_SCALE     = 0.095;    // extra lift when looking up
    private static final double FLIGHT_SINK_FLOOR          = -0.30;    // don’t sink faster than this while powered
    private static final double FLIGHT_MAX_UP              = 0.85;     // max upward velocity cap

    // ===== Sonar tuning =====
    public static final int   SONAR_RANGE                  = 50;
    public static final float SONAR_WAVE_SPEED             = 4.0f;
    public static final int   SONAR_TAG_TICKS              = 20 * 30;
    private static final int  SONAR_PULSE_CD_TICKS         = 20 * 8;

    // ===== Per-player state =====
    private static final Object2FloatOpenHashMap<UUID> METER            = new Object2FloatOpenHashMap<>();
    private static final Object2ByteOpenHashMap<UUID>  STATE            = new Object2ByteOpenHashMap<>();
    private static final Object2LongOpenHashMap<UUID>  LAST_TOGGLE_TICK = new Object2LongOpenHashMap<>();
    private static final Object2ByteOpenHashMap<UUID>  KNOWN_OWL        = new Object2ByteOpenHashMap<>();

    private static final Object2ByteOpenHashMap<UUID>  SONAR_VISION     = new Object2ByteOpenHashMap<>();
    private static final Object2LongOpenHashMap<UUID>  LAST_SONAR_PULSE = new Object2LongOpenHashMap<>();

    // flight enabled toggle (secondary)
    private static final Object2ByteOpenHashMap<UUID>  FLIGHT_ENABLED   = new Object2ByteOpenHashMap<>();

    private static final byte ST_FLYING = 1 << 0;

    // POWERLESS warn throttle
    private static final Object2LongOpenHashMap<UUID> WARN_UNTIL = new Object2LongOpenHashMap<>();

    @Override public String id() { return "owl"; }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot);
    }

    @Override public long cooldownTicks() { return 0; }
    @Override public long secondaryCooldownTicks() { return 0; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier("odd", "textures/gui/abilities/owl_sonar.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/owl_flight.png");
            default          -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" ->
                    "TOGGLE: Sonar vision. While ON: pulse wave on enable and show living entities within 100m.";
            case "secondary" ->
                    "TOGGLE: Owl Flight ENABLE/DISABLE. When disabled you cannot start Owl flight (and it cancels if active).";
            default -> "Owl";
        };
    }

    @Override
    public String longDescription() {
        return "Owl: powered flight + meter (jump to start/cancel) and a sonar-vision mode.";
    }

    public static boolean hasOwl(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity sp)) return false;
        String current = PowerAPI.get(sp);
        return "owl".equals(current);
    }

    public static boolean hasOwlAnySide(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity sp) return hasOwl(sp);
        return net.seep.odd.abilities.owl.net.OwlNetworking.hasOwl(player.getUuid());
    }

    private static boolean isPowerless(ServerPlayerEntity p) {
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

    private static boolean isFlightEnabled(ServerPlayerEntity p) {
        return FLIGHT_ENABLED.getOrDefault(p.getUuid(), (byte)1) != 0;
    }

    private static void setFlightEnabled(ServerPlayerEntity p, boolean enabled) {
        FLIGHT_ENABLED.put(p.getUuid(), (byte)(enabled ? 1 : 0));
    }

    public static void ensureInit(ServerPlayerEntity p) {
        UUID id = p.getUuid();
        if (!METER.containsKey(id)) METER.put(id, FLIGHT_METER_MAX_TICKS);
        if (!FLIGHT_ENABLED.containsKey(id)) FLIGHT_ENABLED.put(id, (byte)1);
    }

    public static void onDisconnect(ServerPlayerEntity p) {
        UUID id = p.getUuid();
        METER.removeFloat(id);
        STATE.removeByte(id);
        LAST_TOGGLE_TICK.removeLong(id);
        KNOWN_OWL.removeByte(id);
        SONAR_VISION.removeByte(id);
        LAST_SONAR_PULSE.removeLong(id);
        FLIGHT_ENABLED.removeByte(id);
        WARN_UNTIL.removeLong(id);
    }

    @Override
    public void forceDisable(ServerPlayerEntity p) {
        if (p == null) return;

        if (isFlying(p)) setFlying(p, false);

        UUID id = p.getUuid();
        if (SONAR_VISION.getOrDefault(id, (byte)0) != 0) {
            SONAR_VISION.removeByte(id);
            LAST_SONAR_PULSE.removeLong(id);
            sendSonarVision(p, false);
        }

        syncMeter(p, METER.getOrDefault(id, FLIGHT_METER_MAX_TICKS), false);
    }

    /* =========================
       PRIMARY: SONAR MODE TOGGLE
       ========================= */

    @Override
    public void activate(ServerPlayerEntity player) {
        if (player.getWorld().isClient) return;
        if (!hasOwl(player)) return;

        if (isPowerless(player)) {
            forceDisable(player);
            warnPowerlessOncePerSec(player);
            return;
        }

        UUID id = player.getUuid();
        boolean active = SONAR_VISION.getOrDefault(id, (byte)0) != 0;
        boolean nowActive = !active;

        SONAR_VISION.put(id, (byte)(nowActive ? 1 : 0));
        sendSonarVision(player, nowActive);

        if (nowActive) {
            long now = player.getWorld().getTime();
            long last = LAST_SONAR_PULSE.getOrDefault(id, -999999L);
            if (now - last >= SONAR_PULSE_CD_TICKS) {
                LAST_SONAR_PULSE.put(id, now);
                sendSonarPulse(player);
            }
        }
    }

    /* =========================
       SECONDARY: FLIGHT ENABLE TOGGLE
       ========================= */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (player.getWorld().isClient) return;
        if (!hasOwl(player)) return;

        if (isPowerless(player)) {
            forceDisable(player);
            warnPowerlessOncePerSec(player);
            return;
        }

        boolean enabled = isFlightEnabled(player);
        boolean nowEnabled = !enabled;
        setFlightEnabled(player, nowEnabled);

        if (!nowEnabled) {
            if (isFlying(player)) setFlying(player, false);
        }

        player.getWorld().playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PHANTOM_FLAP,
                SoundCategory.PLAYERS,
                0.8f,
                nowEnabled ? 0.5f : 0.25f
        );

        syncMeter(player, METER.getOrDefault(player.getUuid(), FLIGHT_METER_MAX_TICKS), isFlying(player));
    }

    /* ===================== Flight packet entry ===================== */

    public static void onClientToggleFlightRequest(ServerPlayerEntity player) {
        if (player == null) return;
        if (!hasOwl(player)) return;

        ensureInit(player);

        if (isPowerless(player)) {
            forceDisableStatic(player);
            warnPowerlessOncePerSec(player);
            return;
        }

        UUID id = player.getUuid();

        long now = player.getWorld().getTime();
        long last = LAST_TOGGLE_TICK.getOrDefault(id, 0L);
        if (now - last < 4) return;
        LAST_TOGGLE_TICK.put(id, now);

        if (isFlying(player)) {
            setFlying(player, false);
            syncMeter(player, METER.getOrDefault(id, FLIGHT_METER_MAX_TICKS), false);
            return;
        }

        if (!isFlightEnabled(player)) {
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 0.55f, 0.65f);
            return;
        }

        tryStartFlight(player);
    }

    private static void forceDisableStatic(ServerPlayerEntity p) {
        if (p == null) return;

        if (isFlying(p)) setFlying(p, false);

        UUID id = p.getUuid();
        if (SONAR_VISION.getOrDefault(id, (byte)0) != 0) {
            SONAR_VISION.removeByte(id);
            LAST_SONAR_PULSE.removeLong(id);
            sendSonarVision(p, false);
        }

        syncMeter(p, METER.getOrDefault(id, FLIGHT_METER_MAX_TICKS), false);
    }

    /* ===================== Server tick ===================== */

    public static void serverTick(ServerPlayerEntity player) {
        UUID id = player.getUuid();

        boolean owl = hasOwl(player);
        boolean wasKnownOwl = (KNOWN_OWL.getOrDefault(id, (byte)0) != 0);

        if (owl != wasKnownOwl) {
            KNOWN_OWL.put(id, (byte)(owl ? 1 : 0));

            var server = player.getServer();
            if (server != null) {
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    PacketByteBuf out = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                    out.writeUuid(id);
                    out.writeBoolean(owl);
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, OWL_STATE_S2C, out);
                }
            }

            if (!owl) {
                setFlying(player, false);

                if (SONAR_VISION.getOrDefault(id, (byte)0) != 0) {
                    SONAR_VISION.removeByte(id);
                    LAST_SONAR_PULSE.removeLong(id);
                    sendSonarVision(player, false);
                }
                return;
            }
        }

        if (!owl) return;

        ensureInit(player);

        if (isPowerless(player)) {
            forceDisableStatic(player);
            return;
        }

        boolean flightEnabled = isFlightEnabled(player);

        boolean flying = isFlying(player);
        float meter = METER.getFloat(id);

        if (!flightEnabled && flying) {
            setFlying(player, false);
            flying = false;
        }

        if (flying) {
            if (!flightEnabled || player.isOnGround() || player.isTouchingWater() || player.isInLava()) {
                setFlying(player, false);
                flying = false;
            } else {
                meter = Math.max(0f, meter - FLIGHT_DRAIN_PER_TICK);
                if (meter <= 0.1f) {
                    setFlying(player, false);
                    flying = false;
                } else {
                    // ✅ ensure elytra physics is actually active (this restores “powered” feeling)
                    if (!player.isFallFlying()) player.startFallFlying();

                    Vec3d look = player.getRotationVec(1.0f).normalize();

                    // forward push
                    Vec3d push = look.multiply(FLIGHT_PUSH_PER_TICK);

                    // ✅ real lift: always some, plus a LOT more when looking up
                    double lift = FLIGHT_LIFT_BASE + FLIGHT_LIFT_LOOK_SCALE * Math.max(0.0, look.y);
                    if (push.y < lift) push = new Vec3d(push.x, lift, push.z);

                    Vec3d v2 = player.getVelocity().add(push);

                    // keep “powered glide” from dropping too hard
                    double vy = Math.max(v2.y, FLIGHT_SINK_FLOOR);
                    vy = MathHelper.clamp(vy, -1.0, FLIGHT_MAX_UP);

                    // ✅ clamp HORIZONTAL only (don’t choke climb)
                    Vec3d horiz = new Vec3d(v2.x, 0, v2.z);
                    double h2 = horiz.lengthSquared();
                    double maxH2 = FLIGHT_MAX_HORIZ_SPEED * FLIGHT_MAX_HORIZ_SPEED;
                    if (h2 > maxH2) {
                        double h = Math.sqrt(h2);
                        horiz = horiz.multiply(FLIGHT_MAX_HORIZ_SPEED / h);
                    }

                    v2 = new Vec3d(horiz.x, vy, horiz.z);

                    player.setVelocity(v2);
                    player.velocityModified = true;
                    player.fallDistance = 0f;
                }
            }
        } else {
            meter = Math.min(FLIGHT_METER_MAX_TICKS, meter + FLIGHT_RECHARGE_PER_TICK);
        }

        METER.put(id, meter);

        if ((player.getWorld().getTime() % 5) == 0) {
            syncMeter(player, meter, flying);
        }
    }

    private static void tryStartFlight(ServerPlayerEntity player) {
        UUID id = player.getUuid();

        if (!hasOwl(player)) return;
        if (isFlying(player)) return;
        if (!isFlightEnabled(player)) return;

        if (player.isOnGround() || player.isTouchingWater() || player.isInLava()) return;
        if (player.getVelocity().y > -0.05) return;

        float meter = METER.getOrDefault(id, FLIGHT_METER_MAX_TICKS);
        if (meter <= 5f) return;

        Vec3d look = player.getRotationVec(1.0f).normalize();
        Vec3d kick = look.multiply(0.35).add(0, 0.22, 0);
        player.setVelocity(player.getVelocity().add(kick));
        player.velocityModified = true;



        setFlying(player, true);

        // ✅ start fall-flying immediately so you get proper glide control instantly
        if (!player.isFallFlying()) player.startFallFlying();

        syncMeter(player, meter, true);
    }

    private static boolean isFlying(ServerPlayerEntity p) {
        return (STATE.getOrDefault(p.getUuid(), (byte)0) & ST_FLYING) != 0;
    }

    private static void setFlying(ServerPlayerEntity p, boolean flying) {
        UUID id = p.getUuid();
        byte st = STATE.getOrDefault(id, (byte)0);
        boolean wasFlying = (st & ST_FLYING) != 0;

        if (flying) st |= ST_FLYING;
        else st &= ~ST_FLYING;
        STATE.put(id, st);

        if (flying) {
            if (!p.isFallFlying()) p.startFallFlying();
        }

        if (wasFlying && !flying) {
            if (p.isFallFlying()) {
                try { p.stopFallFlying(); } catch (Throwable ignore) {}
            }


        }
    }

    private static void sendSonarVision(ServerPlayerEntity player, boolean active) {
        PacketByteBuf out = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        out.writeBoolean(active);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, OWL_SONAR_VISION_S2C, out);
    }

    private static void sendSonarPulse(ServerPlayerEntity player) {
        Vec3d origin = player.getPos();

        player.getWorld().playSound(
                null,
                player.getBlockPos(),
                ModSounds.OWL_SONAR,
                SoundCategory.PLAYERS,
                1.0f,
                1.0f
        );

        Box box = player.getBoundingBox().expand(SONAR_RANGE);
        List<Entity> found = player.getWorld().getOtherEntities(player, box, e ->
                e.isAlive() && (e instanceof LivingEntity) && !e.isSpectator()
        );

        PacketByteBuf out = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        out.writeDouble(origin.x);
        out.writeDouble(origin.y);
        out.writeDouble(origin.z);
        out.writeLong(player.getWorld().getTime());

        out.writeVarInt(SONAR_RANGE);
        out.writeFloat(SONAR_WAVE_SPEED);
        out.writeVarInt(SONAR_TAG_TICKS);

        out.writeVarInt(found.size());
        for (Entity e : found) {
            double dist = Math.sqrt(player.squaredDistanceTo(e));
            int delay = Math.max(0, MathHelper.floor(dist / SONAR_WAVE_SPEED));
            out.writeVarInt(e.getId());
            out.writeVarInt(delay);
        }

        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, OWL_SONAR_S2C, out);
    }

    private static void syncMeter(ServerPlayerEntity player, float meterTicks, boolean flying) {
        ensureInit(player);

        float meter01 = MathHelper.clamp(meterTicks / (float) FLIGHT_METER_MAX_TICKS, 0f, 1f);
        boolean flightEnabled = isFlightEnabled(player);

        PacketByteBuf out = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        out.writeFloat(meter01);
        out.writeBoolean(flying);
        out.writeBoolean(flightEnabled);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, OWL_METER_S2C, out);
    }
}