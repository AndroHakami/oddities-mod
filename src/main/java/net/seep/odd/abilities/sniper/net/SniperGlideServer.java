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
import net.seep.odd.status.ModStatusEffects;

import java.util.UUID;

/**
 * Sniper parachute glide (server-authoritative).
 * Hold SPACE while falling => hidden Slow Falling "parachute glide".
 *
 * NOW gated by an "ARMED" toggle (set by SniperPower primary).
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

    private static final Object2ByteOpenHashMap<UUID> INPUT  = new Object2ByteOpenHashMap<>();
    private static final Object2FloatOpenHashMap<UUID> ENERGY = new Object2FloatOpenHashMap<>();
    private static final Object2ByteOpenHashMap<UUID> ACTIVE = new Object2ByteOpenHashMap<>(); // 0/1

    // ✅ NEW: toggle gate (0/1). Default = OFF unless set by SniperPower.
    private static final Object2ByteOpenHashMap<UUID> ARMED = new Object2ByteOpenHashMap<>();

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
            ARMED.removeByte(id);
            LAST_SENT_E.removeFloat(id);
            LAST_SENT_A.removeByte(id);
            LAST_SENT_T.removeInt(id);
        });

        // run at START so the effect is present during the physics/move tick
        ServerTickEvents.START_SERVER_TICK.register(SniperGlideServer::tickServer);
    }

    /* ===================== PUBLIC API FOR SniperPower ===================== */

    /** SniperPower primary toggle should call this. */
    public static void setArmed(ServerPlayerEntity sp, boolean armed) {
        if (sp == null) return;
        UUID id = sp.getUuid();
        if (armed) ARMED.put(id, (byte) 1);
        else ARMED.removeByte(id);

        // turning OFF should instantly cancel glide + clear our slow-fall
        if (!armed) {
            ACTIVE.removeByte(id);
            removeOurSlowFalling(sp);
            maybeSend(sp, energyNorm(id), false, true);
        }
    }

    public static boolean isArmed(ServerPlayerEntity sp) {
        if (sp == null) return false;
        return ARMED.getOrDefault(sp.getUuid(), (byte)0) != 0;
    }

    /* ===================== ticking ===================== */

    private static void tickServer(MinecraftServer server) {
        serverTick++;
        for (ServerPlayerEntity sp : server.getPlayerManager().getPlayerList()) tickPlayer(sp);
    }

    private static boolean hasSniper(ServerPlayerEntity sp) {
        String current = net.seep.odd.abilities.PowerAPI.get(sp);
        return "sniper".equals(current);
    }

    private static boolean isPowerless(ServerPlayerEntity sp) {
        return sp != null && sp.hasStatusEffect(ModStatusEffects.POWERLESS);
    }

    private static float energyNorm(UUID id) {
        float e = ENERGY.getOrDefault(id, ENERGY_MAX);
        return (ENERGY_MAX <= 0.001f) ? 0f : MathHelper.clamp(e / ENERGY_MAX, 0f, 1f);
    }

    private static void tickPlayer(ServerPlayerEntity sp) {
        UUID id = sp.getUuid();

        // Not sniper: hard reset
        if (!hasSniper(sp) || sp.isSpectator()) {
            INPUT.removeByte(id);
            ENERGY.removeFloat(id);
            ACTIVE.removeByte(id);
            ARMED.removeByte(id);

            removeOurSlowFalling(sp);
            maybeSend(sp, 0f, false, true);
            return;
        }

        // POWERLESS: force off (but keep energy stored)
        if (isPowerless(sp)) {
            INPUT.removeByte(id);
            ACTIVE.removeByte(id);
            removeOurSlowFalling(sp);
            maybeSend(sp, energyNorm(id), false, true);
            return;
        }

        float e = ENERGY.getOrDefault(id, ENERGY_MAX);
        byte in = INPUT.getOrDefault(id, (byte)0);
        boolean jumpHeld = (in & IN_JUMP) != 0;

        boolean onGround = sp.isOnGround();
        boolean falling = sp.getVelocity().y < -0.01;

        // ✅ NEW: must be ARMED to glide
        boolean armed = ARMED.getOrDefault(id, (byte)0) != 0;

        boolean canGlide =
                armed
                        && !onGround
                        && !sp.isTouchingWater()
                        && !sp.isInLava()
                        && !sp.isFallFlying()
                        && !sp.getAbilities().flying
                        && falling;

        boolean glidingNow = false;

        if (jumpHeld && canGlide && e > 0.0f) {
            glidingNow = true;

            sp.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SLOW_FALLING,
                    6,     // ticks
                    0,     // amp
                    true,  // ambient
                    false, // showParticles
                    false  // showIcon
            ));

            sp.fallDistance = 0.0f;
            e = Math.max(0.0f, e - DRAIN_PER_TICK);
        } else {
            // If we were gliding but stopped, remove ONLY our tiny hidden slow-fall (snappy stop)
            boolean wasActive = ACTIVE.getOrDefault(id, (byte)0) != 0;
            if (wasActive && !glidingNow) removeOurSlowFalling(sp);

            // Recharge only on ground
            if (onGround) e = Math.min(ENERGY_MAX, e + RECHARGE_PER_TICK);
        }

        ENERGY.put(id, e);
        ACTIVE.put(id, (byte)(glidingNow ? 1 : 0));

        maybeSend(sp, (ENERGY_MAX <= 0.001f) ? 0f : (e / ENERGY_MAX), glidingNow, false);
    }

    /**
     * Only remove OUR slow-fall (short duration, hidden icon/particles, ambient).
     * Won't break legit potions / other mods.
     */
    private static void removeOurSlowFalling(ServerPlayerEntity sp) {
        StatusEffectInstance inst = sp.getStatusEffect(StatusEffects.SLOW_FALLING);
        if (inst == null) return;

        boolean looksLikeOurs =
                inst.getDuration() <= 6
                        && inst.isAmbient()
                        && !inst.shouldShowParticles()
                        && !inst.shouldShowIcon();

        if (looksLikeOurs) sp.removeStatusEffect(StatusEffects.SLOW_FALLING);
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