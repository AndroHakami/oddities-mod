package net.seep.odd.abilities.artificer.mixer.brew;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.seep.odd.sound.ModSounds;

import java.util.UUID;

/**
 * Atomic Refraction:
 * - Server-authoritative timer (uses server ticks).
 * - Client overlay + one-shot sound.
 * - "Zero gravity" while active via setNoGravity(true) (NO velocity hacks).
 *
 * NOTE:
 * - Phasing itself should be done via your mixin (pretendSpectator) using isActive()/shouldPhase().
 */
public final class AtomicRefractionEffect {
    private AtomicRefractionEffect() {}

    private static final Identifier S2C_REFRACTION = new Identifier("odd", "atomic_refraction");

    // store end tick in SERVER ticks (not world time)
    private static final Object2LongOpenHashMap<UUID> END_TICK = new Object2LongOpenHashMap<>();
    private static boolean inited = false;

    /** Call from common init if you want; start() also calls it. */
    public static void bootstrapCommon() {
        initCommon();
    }

    private static void initCommon() {
        if (inited) return;
        inited = true;

        // START tick so our flags are applied before players tick that frame
        ServerTickEvents.START_SERVER_TICK.register(AtomicRefractionEffect::serverTick);

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            END_TICK.removeLong(id);

            // Cleanup anything we toggled
            handler.player.setNoGravity(false);
            send(handler.player, false, 0);
        });
    }

    /**
     * Used by the MIXIN.
     * Works on both sides safely:
     * - client uses local timer
     * - server uses END_TICK
     */
    public static boolean isActive(PlayerEntity player) {
        if (player == null) return false;

        // client: rely on local counter
        if (player.getWorld() != null && player.getWorld().isClient) {
            return ClientState.clientTicksLeft > 0;
        }

        // server: rely on END_TICK
        if (player instanceof ServerPlayerEntity sp) {
            long end = END_TICK.getOrDefault(sp.getUuid(), Long.MIN_VALUE);
            if (end == Long.MIN_VALUE) return false;
            long now = sp.getServer().getTicks();
            return now < end;
        }

        return false;
    }

    /**
     * Optional helper for your "don't fall through ground" approach:
     * Only phase when you are actually pushing into something (so you keep floor collision).
     *
     * If you already implemented this elsewhere, you can ignore it.
     */
    public static boolean shouldPhase(PlayerEntity player) {
        if (!isActive(player)) return false;
        return player.horizontalCollision; // keeps ground solid, but lets you slide through walls
    }

    public static void start(ServerPlayerEntity player, int durationTicks) {
        if (player == null || player.getServer() == null) return;
        initCommon();

        MinecraftServer server = player.getServer();
        long end = (long) server.getTicks() + (long) durationTicks;

        END_TICK.put(player.getUuid(), end);

        // Zero gravity immediately (no velocity changes)
        player.setNoGravity(true);
        player.fallDistance = 0.0f;

        // tell client to start overlay + play sound once
        send(player, true, durationTicks);
    }

    private static void serverTick(MinecraftServer server) {
        long now = server.getTicks();

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            long end = END_TICK.getOrDefault(p.getUuid(), Long.MIN_VALUE);
            if (end == Long.MIN_VALUE) continue;

            if (p.isSpectator()) {
                // don't fight actual spectator mode
                END_TICK.removeLong(p.getUuid());
                p.setNoGravity(false);
                send(p, false, 0);
                continue;
            }

            if (now < end) {
                // Keep zero gravity on the server the whole time
                p.setNoGravity(true);
                p.fallDistance = 0.0f;
            } else {
                // expire
                END_TICK.removeLong(p.getUuid());
                p.setNoGravity(false);
                send(p, false, 0);
            }
        }
    }

    private static void send(ServerPlayerEntity p, boolean active, int durationTicks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(active);
        buf.writeVarInt(Math.max(0, durationTicks));
        ServerPlayNetworking.send(p, S2C_REFRACTION, buf);
    }

    /* ================= CLIENT ================= */

    // kept here to avoid referencing client-only classes in common mixins
    @Environment(EnvType.CLIENT)
    private static final class ClientState {
        private static int clientTicksLeft = 0;
        private static boolean clientInited = false;
    }

    @Environment(EnvType.CLIENT)
    public static final class Client {
        public static void init() {
            if (ClientState.clientInited) return;
            ClientState.clientInited = true;

            // Satin overlay init (you already have this)
            net.seep.odd.abilities.artificer.mixer.brew.client.RefractionFx.init();

            ClientPlayNetworking.registerGlobalReceiver(S2C_REFRACTION, (client, handler, buf, rs) -> {
                boolean active = buf.readBoolean();
                int dur = buf.readVarInt();
                client.execute(() -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc == null || mc.player == null) return;

                    if (active) {
                        ClientState.clientTicksLeft = Math.max(ClientState.clientTicksLeft, dur);

                        net.seep.odd.abilities.artificer.mixer.brew.client.RefractionFx.start(dur);

                        // play ONCE, emitted from the player (follows them)
                        mc.player.playSound(ModSounds.REFRACTION, 0.9f, 1.0f);

                        // local feel matches server (no velocity hacks)
                        mc.player.setNoGravity(true);
                        mc.player.fallDistance = 0.0f;
                    } else {
                        ClientState.clientTicksLeft = 0;
                        net.seep.odd.abilities.artificer.mixer.brew.client.RefractionFx.stop();

                        if (!mc.player.isSpectator()) mc.player.setNoGravity(false);
                    }
                });
            });

            ClientTickEvents.END_CLIENT_TICK.register(mc -> {
                if (mc == null || mc.player == null) return;

                // purely for visuals + local feel; server is authoritative
                if (ClientState.clientTicksLeft > 0) {
                    ClientState.clientTicksLeft--;

                    mc.player.setNoGravity(true);
                    mc.player.fallDistance = 0.0f;

                    net.seep.odd.abilities.artificer.mixer.brew.client.RefractionFx.tickClient();

                    if (ClientState.clientTicksLeft <= 0) {
                        net.seep.odd.abilities.artificer.mixer.brew.client.RefractionFx.stop();
                        if (!mc.player.isSpectator()) mc.player.setNoGravity(false);
                    }
                } else {
                    net.seep.odd.abilities.artificer.mixer.brew.client.RefractionFx.tickClient();
                }
            });
        }
    }
}
