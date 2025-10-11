package net.seep.odd.abilities.spotted;

import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.power.Powers;
import net.seep.odd.abilities.power.SpottedPhantomPower;

import java.util.UUID;

/**
 * Common + client wiring for Spotted Phantom:
 *  - ticks power per world
 *  - cleanup on disconnect
 *  - S2C flag for "spotted-invisible" (used by client mixins to hide armor/items)
 *  - late-join sync
 */
public final class SpottedNet {
    private SpottedNet() {}

    /** S2C packet: (UUID subject, boolean active) */
    public static final Identifier S2C_SPOTTED_INVIS =
            new Identifier(Oddities.MOD_ID, "spotted_invis");

    private static boolean INIT_COMMON = false;
    private static boolean INIT_CLIENT = false;

    /** Server-side memory for late-join sync. */
    private static final Object2BooleanOpenHashMap<UUID> SERVER_SPOTTED_INVIS = new Object2BooleanOpenHashMap<>();

    /** Call from your common mod init (onInitialize). Idempotent. */
    public static void initCommon() {
        if (INIT_COMMON) return;
        INIT_COMMON = true;

        // Per-world ticking for the power
        ServerTickEvents.END_WORLD_TICK.register((ServerWorld world) -> {
            for (ServerPlayerEntity p : world.getPlayers()) {
                var pow = Powers.get(PowerAPI.get(p));
                if (pow instanceof SpottedPhantomPower) {
                    SpottedPhantomPower.serverTick(p);
                }
            }
        });

        // Cleanup on disconnect
        ServerPlayConnectionEvents.DISCONNECT.register((ServerPlayNetworkHandler handler, MinecraftServer server) -> {
            ServerPlayerEntity p = handler.player;
            SpottedPhantomPower.handleDisconnect(p); // also broadcasts false
            SERVER_SPOTTED_INVIS.removeBoolean(p.getUuid());
        });

        // Late-join sync: tell the joining player who in their world is currently spotted-invisible
        ServerPlayConnectionEvents.JOIN.register((ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) -> {
            ServerPlayerEntity viewer = handler.player;
            ServerWorld w = (ServerWorld) viewer.getWorld();
            for (ServerPlayerEntity other : w.getPlayers()) {
                boolean active = SERVER_SPOTTED_INVIS.getOrDefault(other.getUuid(), false);
                if (active) {
                    sendSpottedInvisTo(viewer, other.getUuid(), true);
                }
            }
        });
    }

    /** Client-side receiver/clear. Call from your client init. Idempotent. */
    @Environment(EnvType.CLIENT)
    public static void initClient() {
        if (INIT_CLIENT) return;
        INIT_CLIENT = true;

        ClientPlayNetworking.registerGlobalReceiver(S2C_SPOTTED_INVIS, (client, handler, buf, responseSender) -> {
            UUID id = buf.readUuid();
            boolean active = buf.readBoolean();
            client.execute(() -> SpottedClient.set(id, active));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SpottedClient.clearAll());
    }

    /** Server: broadcast to everyone in the player's world that this player's spotted-invis bit changed. */
    public static void broadcastSpottedInvis(ServerPlayerEntity player, boolean active) {
        // remember for late-join sync
        SERVER_SPOTTED_INVIS.put(player.getUuid(), active);

        ServerWorld world = (ServerWorld) player.getWorld();
        for (ServerPlayerEntity viewer : PlayerLookup.world(world)) {
            sendSpottedInvisTo(viewer, player.getUuid(), active);
        }
    }

    /* ----------------- helpers ----------------- */

    private static void sendSpottedInvisTo(ServerPlayerEntity viewer, UUID subjectId, boolean active) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeUuid(subjectId);
        out.writeBoolean(active);
        ServerPlayNetworking.send(viewer, S2C_SPOTTED_INVIS, out);
    }
}
