// FILE: src/main/java/net/seep/odd/abilities/sniper/net/SniperGlideServer.java
package net.seep.odd.abilities.sniper.net;

import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import net.seep.odd.Oddities;

import java.util.UUID;

/**
 * Sniper "third ability":
 * Hold SPACE while falling => hidden Slow Falling "parachute glide".
 * Uses an energy meter that only recharges on ground (very fast).
 *
 * Server-authoritative: denies fall damage and preserves full vanilla air control.
 */
public final class SniperGlideServer {
    private SniperGlideServer() {}

    // C2S: input flags (we only need jump)
    public static final Identifier GLIDE_CTRL_C2S = new Identifier(Oddities.MOD_ID, "sniper_glide_ctrl_c2s");
    public static final byte IN_JUMP = 1 << 0;

    // S2C: glide state (energy + active)
    public static final Identifier GLIDE_STATE_S2C = new Identifier(Oddities.MOD_ID, "sniper_glide_state_s2c");

    // Tunables
    private static final float ENERGY_MAX = 140.0f;          // ~7s at drain=1/tick
    private static final float DRAIN_PER_TICK = 1.0f;        // drain while gliding
    private static final float RECHARGE_PER_TICK = 14.0f;    // fast ground recharge (~0.5s)

    private static final Object2ByteOpenHashMap<UUID> INPUT = new Object2ByteOpenHashMap<>();
    private static final Object2FloatOpenHashMap<UUID> ENERGY = new Object2FloatOpenHashMap<>();
    private static final Object2ByteOpenHashMap<UUID> ACTIVE = new Object2ByteOpenHashMap<>(); // 0/1

    // throttle S2C
    private static final Object2FloatOpenHashMap<UUID> LAST_SENT_E = new Object2FloatOpenHashMap<>();
    private static final Object2ByteOpenHashMap<UUID> LAST_SENT_A = new Object2ByteOpenHashMap<>();
    private static final Object2IntOpenHashMap<UUID> LAST_SENT_T = new Object2IntOpenHashMap<>();
    private static int serverTick = 0;

    private static boolean inited = false;

    /** Call once in common init. */
    public static void init() {
        if (inited) return;
        inited = true;

        ServerPlayNetworking.registerGlobalReceiver(GLIDE_CTRL_C2S, (server, player, handler, buf, responseSender) -> {
            final byte flags = buf.readByte();
            server.execute(() -> INPUT.put(player.getUuid(), flags));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            INPUT.removeByte(id);
            ENERGY.removeFloat(id);
            ACTIVE.removeByte(id);
            LAST_SENT_E.removeFloat(id);
            LAST_SENT_A.removeByte(id);
            LAST_SENT_T.removeInt(id);
        });

        // ✅ IMPORTANT: run at START so the effect is present during the physics/move tick
        ServerTickEvents.START_SERVER_TICK.register(SniperGlideServer::tickServer);
    }

    private static void tickServer(MinecraftServer server) {
        serverTick++;
        for (ServerPlayerEntity sp : server.getPlayerManager().getPlayerList()) tickPlayer(sp);
    }

    private static boolean hasSniper(ServerPlayerEntity sp) {
        String current = net.seep.odd.abilities.PowerAPI.get(sp);
        return "sniper".equals(current);
    }

    private static void tickPlayer(ServerPlayerEntity sp) {
        UUID id = sp.getUuid();

        // If not sniper power, force off and remove any lingering slow-fall we applied
        if (!hasSniper(sp) || sp.isSpectator()) {
            INPUT.removeByte(id);
            ENERGY.removeFloat(id);
            setActive(id, (byte)0);

            sp.removeStatusEffect(StatusEffects.SLOW_FALLING);

            maybeSend(sp, 0f, false, true);
            return;
        }

        float e = ENERGY.getOrDefault(id, ENERGY_MAX);
        byte in = INPUT.getOrDefault(id, (byte)0);
        boolean jumpHeld = (in & IN_JUMP) != 0;

        boolean onGround = sp.isOnGround();
        boolean falling = sp.getVelocity().y < -0.01;

        boolean canGlide =
                !onGround
                        && !sp.isTouchingWater()
                        && !sp.isInLava()
                        && !sp.isFallFlying()
                        && !sp.getAbilities().flying
                        && falling;

        boolean glidingNow = false;

        if (jumpHeld && canGlide && e > 0.0f) {
            glidingNow = true;

            // ✅ Hidden slow falling = keeps full air-strafing control
            // duration small but refreshed every tick while gliding
            sp.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SLOW_FALLING,
                    6,   // ticks
                    0,   // amp
                    true,  // ambient
                    false, // showParticles
                    false  // showIcon
            ));

            // deny fall damage while gliding
            sp.fallDistance = 0.0f;

            e = Math.max(0.0f, e - DRAIN_PER_TICK);
        } else {
            // Recharge ONLY on ground
            if (onGround) {
                e = Math.min(ENERGY_MAX, e + RECHARGE_PER_TICK);
            }
        }

        ENERGY.put(id, e);
        setActive(id, (byte)(glidingNow ? 1 : 0));

        float norm = (ENERGY_MAX <= 0.001f) ? 0f : (e / ENERGY_MAX);
        maybeSend(sp, norm, glidingNow, false);
    }

    private static void setActive(UUID id, byte a) {
        ACTIVE.put(id, a);
    }

    private static void maybeSend(ServerPlayerEntity sp, float energyNorm, boolean active, boolean force) {
        UUID id = sp.getUuid();
        float lastE = LAST_SENT_E.getOrDefault(id, -999f);
        byte lastA  = LAST_SENT_A.getOrDefault(id, (byte)2);
        int lastT   = LAST_SENT_T.getOrDefault(id, -999999);

        boolean timeOk = active ? (serverTick - lastT) >= 2 : (serverTick - lastT) >= 10;
        boolean changed = (Math.abs(energyNorm - lastE) >= 0.02f) || ((byte)(active ? 1 : 0) != lastA);

        if (!force && !(timeOk && changed)) return;

        PacketByteBuf out = PacketByteBufs.create();
        out.writeFloat(MathHelper.clamp(energyNorm, 0f, 1f));
        out.writeBoolean(active);

        ServerPlayNetworking.send(sp, GLIDE_STATE_S2C, out);

        LAST_SENT_E.put(id, energyNorm);
        LAST_SENT_A.put(id, (byte)(active ? 1 : 0));
        LAST_SENT_T.put(id, serverTick);
    }
}
