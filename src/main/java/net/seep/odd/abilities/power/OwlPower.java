// FILE: src/main/java/net/seep/odd/abilities/power/OwlPower.java
package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.Oddities;

import java.util.List;
import java.util.UUID;

public final class OwlPower implements Power {

    // ===== Packets =====
    public static final Identifier OWL_TOGGLE_FLIGHT_C2S = new Identifier(Oddities.MOD_ID, "owl_toggle_flight_c2s");
    public static final Identifier OWL_STATE_S2C = new Identifier(Oddities.MOD_ID, "owl_state_s2c");
    public static final Identifier OWL_METER_S2C = new Identifier(Oddities.MOD_ID, "owl_meter_s2c");
    public static final Identifier OWL_SONAR_S2C = new Identifier(Oddities.MOD_ID, "owl_sonar_s2c");

    // ✅ sonar vision toggle packet
    public static final Identifier OWL_SONAR_VISION_S2C = new Identifier(Oddities.MOD_ID, "owl_sonar_vision_s2c");

    // ===== Flight tuning =====
    public static final int   FLIGHT_METER_MAX_TICKS = 10 * 10;  // 10s
    public static final float FLIGHT_DRAIN_PER_TICK  = 1.0f;
    public static final float FLIGHT_RECHARGE_PER_TICK = 0.35f;

    public static final float FLIGHT_PUSH_PER_TICK = 0.055f;
    public static final float FLIGHT_MIN_UPWARD    = 0.010f;
    public static final double FLIGHT_MAX_SPEED    = 1.9;

    // ===== Sonar tuning =====
    public static final int   SONAR_RANGE = 100;
    public static final float SONAR_WAVE_SPEED = 4.0f;
    public static final int   SONAR_TAG_TICKS = 20 * 30;
    private static final int  SONAR_PULSE_CD_TICKS = 20 * 8; // pulse cooldown only

    // ===== Per-player state =====
    private static final Object2FloatOpenHashMap<UUID> METER = new Object2FloatOpenHashMap<>();
    private static final Object2ByteOpenHashMap<UUID>  STATE = new Object2ByteOpenHashMap<>();
    private static final Object2LongOpenHashMap<UUID>  LAST_TOGGLE_TICK = new Object2LongOpenHashMap<>();
    private static final Object2ByteOpenHashMap<UUID>  KNOWN_OWL = new Object2ByteOpenHashMap<>();

    // sonar vision toggle state + pulse cooldown
    private static final Object2ByteOpenHashMap<UUID>  SONAR_VISION = new Object2ByteOpenHashMap<>();
    private static final Object2LongOpenHashMap<UUID>  LAST_SONAR_PULSE = new Object2LongOpenHashMap<>();

    private static final byte ST_FLYING = 1 << 0;

    @Override public String id() { return "owl"; }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot);
    }

    // ✅ toggle must be instant ON/OFF (PowerAPI cooldown would block turning OFF)
    @Override public long cooldownTicks() { return 0; }
    @Override public long secondaryCooldownTicks() { return 0; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary" -> new Identifier("odd", "textures/gui/abilities/owl_sonar.png");
            default -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" -> "TOGGLE: Sonar mode (desaturated vision). While ON: living entities within 100m show YELLOW outlines (only you).";
            default -> "Owl";
        };
    }

    @Override
    public String longDescription() {
        return "Owl: Elytra-like flight trigger + meter. Press Space again while flying to cancel. Primary: toggle sonar mode + pulse wave.";
    }

    public static boolean hasOwl(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity sp)) return false;
        String current = net.seep.odd.abilities.PowerAPI.get(sp);
        return "owl".equals(current);
    }

    public static boolean hasOwlAnySide(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity sp) return hasOwl(sp);
        return net.seep.odd.abilities.owl.net.OwlNetworking.hasOwl(player.getUuid());
    }

    public static void registerNetworking() {
        ServerPlayNetworking.registerGlobalReceiver(OWL_TOGGLE_FLIGHT_C2S, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                if (!hasOwl(player)) return;

                long now = player.getWorld().getTime();
                long last = LAST_TOGGLE_TICK.getOrDefault(player.getUuid(), 0L);
                if (now - last < 4) return; // anti-spam
                LAST_TOGGLE_TICK.put(player.getUuid(), now);

                // ✅ Space while flying cancels; otherwise behaves like elytra trigger to start
                if (isFlying(player)) {
                    setFlying(player, false);
                    syncMeter(player, METER.getOrDefault(player.getUuid(), FLIGHT_METER_MAX_TICKS), false);
                } else {
                    tryStartFlight(player);
                }
            });
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity joiner = handler.getPlayer();

            for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
                sendOwlStateTo(joiner, other.getUuid(), hasOwl(other));
            }
            broadcastOwlState(server, joiner.getUuid(), hasOwl(joiner));

            if (hasOwl(joiner) && !METER.containsKey(joiner.getUuid())) {
                METER.put(joiner.getUuid(), FLIGHT_METER_MAX_TICKS);
            }

            // ✅ ensure sonar vision explicitly synced on join (prevents stuck shader)
            boolean v = SONAR_VISION.getOrDefault(joiner.getUuid(), (byte)0) != 0;
            sendSonarVision(joiner, v);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            METER.removeFloat(id);
            STATE.removeByte(id);
            LAST_TOGGLE_TICK.removeLong(id);
            KNOWN_OWL.removeByte(id);

            SONAR_VISION.removeByte(id);
            LAST_SONAR_PULSE.removeLong(id);

            broadcastOwlState(server, id, false);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                serverTick(p);
            }
        });
    }

    public static void serverTick(ServerPlayerEntity player) {
        UUID id = player.getUuid();

        boolean owl = hasOwl(player);
        boolean wasKnownOwl = (KNOWN_OWL.getOrDefault(id, (byte)0) != 0);

        if (owl != wasKnownOwl) {
            KNOWN_OWL.put(id, (byte)(owl ? 1 : 0));
            MinecraftServer server = player.getServer();
            if (server != null) broadcastOwlState(server, id, owl);

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

        if (!METER.containsKey(id)) METER.put(id, FLIGHT_METER_MAX_TICKS);

        boolean flying = isFlying(player);
        float meter = METER.getFloat(id);

        if (flying) {
            if (player.isOnGround() || player.isTouchingWater() || player.isInLava()) {
                setFlying(player, false);
                flying = false;
            } else {
                meter = Math.max(0f, meter - FLIGHT_DRAIN_PER_TICK);
                if (meter <= 0.1f) {
                    setFlying(player, false);
                    flying = false;
                } else {
                    if (!player.isFallFlying()) player.startFallFlying();

                    Vec3d look = player.getRotationVec(1.0f).normalize();
                    Vec3d push = look.multiply(FLIGHT_PUSH_PER_TICK);
                    if (push.y < FLIGHT_MIN_UPWARD) push = new Vec3d(push.x, FLIGHT_MIN_UPWARD, push.z);

                    Vec3d vel = player.getVelocity().add(push);
                    if (vel.lengthSquared() > FLIGHT_MAX_SPEED * FLIGHT_MAX_SPEED) {
                        vel = vel.normalize().multiply(FLIGHT_MAX_SPEED);
                    }

                    player.setVelocity(vel);
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

        if (player.isOnGround() || player.isTouchingWater() || player.isInLava()) return;
        if (player.getVelocity().y > -0.05) return;

        float meter = METER.getOrDefault(id, FLIGHT_METER_MAX_TICKS);
        if (meter <= 5f) return;

        Vec3d look = player.getRotationVec(1.0f).normalize();
        Vec3d kick = look.multiply(0.35).add(0, 0.20, 0);
        player.setVelocity(player.getVelocity().add(kick));
        player.velocityModified = true;

        setFlying(player, true);
        syncMeter(player, meter, true);
    }

    private static boolean isFlying(ServerPlayerEntity p) {
        return (STATE.getOrDefault(p.getUuid(), (byte)0) & ST_FLYING) != 0;
    }

    private static void setFlying(ServerPlayerEntity p, boolean flying) {
        UUID id = p.getUuid();
        byte st = STATE.getOrDefault(id, (byte)0);
        if (flying) st |= ST_FLYING;
        else st &= ~ST_FLYING;
        STATE.put(id, st);

        if (!flying && p.isFallFlying()) p.stopFallFlying();
        if (flying && !p.isFallFlying()) p.startFallFlying();
    }

    /* =========================
       PRIMARY: SONAR MODE TOGGLE
       ========================= */

    @Override
    public void activate(ServerPlayerEntity player) {
        if (player.getWorld().isClient) return;
        if (!hasOwl(player)) return;

        UUID id = player.getUuid();
        boolean active = SONAR_VISION.getOrDefault(id, (byte)0) != 0;
        boolean nowActive = !active;

        SONAR_VISION.put(id, (byte)(nowActive ? 1 : 0));
        sendSonarVision(player, nowActive);

        // Pulse only when turning ON, and only if pulse cooldown is ready
        if (nowActive) {
            long now = player.getWorld().getTime();
            long last = LAST_SONAR_PULSE.getOrDefault(id, -999999L);
            if (now - last >= SONAR_PULSE_CD_TICKS) {
                LAST_SONAR_PULSE.put(id, now);
                sendSonarPulse(player);
            }
        }
    }

    private static void sendSonarVision(ServerPlayerEntity player, boolean active) {
        PacketByteBuf out = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        out.writeBoolean(active);
        ServerPlayNetworking.send(player, OWL_SONAR_VISION_S2C, out);
    }

    private static void sendSonarPulse(ServerPlayerEntity player) {
        Vec3d origin = player.getPos();

        player.getWorld().playSound(
                null,
                player.getBlockPos(),
                SoundEvents.BLOCK_SCULK_SENSOR_CLICKING,
                SoundCategory.PLAYERS,
                1.0f,
                1.35f
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

        ServerPlayNetworking.send(player, OWL_SONAR_S2C, out);
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        // none
    }

    private static void syncMeter(ServerPlayerEntity player, float meterTicks, boolean flying) {
        float meter01 = MathHelper.clamp(meterTicks / (float) FLIGHT_METER_MAX_TICKS, 0f, 1f);
        PacketByteBuf out = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        out.writeFloat(meter01);
        out.writeBoolean(flying);
        ServerPlayNetworking.send(player, OWL_METER_S2C, out);
    }

    private static void sendOwlStateTo(ServerPlayerEntity to, UUID who, boolean owl) {
        PacketByteBuf out = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        out.writeUuid(who);
        out.writeBoolean(owl);
        ServerPlayNetworking.send(to, OWL_STATE_S2C, out);
    }

    private static void broadcastOwlState(MinecraftServer server, UUID who, boolean owl) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            sendOwlStateTo(p, who, owl);
        }
    }
}
