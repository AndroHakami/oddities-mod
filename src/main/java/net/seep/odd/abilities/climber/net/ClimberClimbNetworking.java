// src/main/java/net/seep/odd/abilities/climber/net/ClimberClimbNetworking.java
package net.seep.odd.abilities.climber.net;

import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.power.ClimberPower;

import java.util.UUID;

/**
 * Server decides if a player can wall-climb (ONLY climber power),
 * and broadcasts a tiny state packet so clients simulate climbing smoothly.
 */
public final class ClimberClimbNetworking {
    private ClimberClimbNetworking() {}

    public static final Identifier CLIMB_STATE =
            new Identifier(Oddities.MOD_ID, "climber_can_climb");

    // last state we sent for each player (server-side)
    private static final Object2BooleanOpenHashMap<UUID> LAST_SENT = new Object2BooleanOpenHashMap<>();

    // client-side cache: which players can climb
    @Environment(EnvType.CLIENT)
    private static final Object2BooleanOpenHashMap<UUID> CLIENT_CAN_CLIMB = new Object2BooleanOpenHashMap<>();

    /* =========================
       Common helper used by mixins
       ========================= */

    public static boolean canClimbAnySide(UUID playerId) {
        // On dedicated server this method shouldn't be used for truth,
        // but it IS used on client for simulation.
        return clientCanClimb(playerId);
    }

    @Environment(EnvType.CLIENT)
    public static boolean clientCanClimb(UUID playerId) {
        return CLIENT_CAN_CLIMB.getOrDefault(playerId, false);
    }

    /* =========================
       Server registration
       ========================= */

    public static void registerServer() {
        // When a player joins, send them everyone’s current state,
        // and send everyone the joiner’s state.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity joiner = handler.player;

            // Send all existing states to the joiner
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                boolean active = ClimberPower.hasClimber(p);
                sendTo(joiner, p.getUuid(), active);
                LAST_SENT.put(p.getUuid(), active);
            }

            // Broadcast the joiner’s state to everyone (including them)
            boolean joinerActive = ClimberPower.hasClimber(joiner);
            broadcast(server, joiner.getUuid(), joinerActive);
            LAST_SENT.put(joiner.getUuid(), joinerActive);
        });

        // Cleanup + broadcast false so clients stop simulating climb for that UUID
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            LAST_SENT.removeBoolean(id);
            broadcast(server, id, false);
        });

        // Tick: if power changed (climber <-> not climber), broadcast update.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                UUID id = p.getUuid();
                boolean active = ClimberPower.hasClimber(p);
                boolean prev = LAST_SENT.getOrDefault(id, false);

                if (active != prev) {
                    LAST_SENT.put(id, active);
                    broadcast(server, id, active);
                }
            }
        });
    }

    private static void sendTo(ServerPlayerEntity to, UUID who, boolean canClimb) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(who);
        buf.writeBoolean(canClimb);
        ServerPlayNetworking.send(to, CLIMB_STATE, buf);
    }

    private static void broadcast(MinecraftServer server, UUID who, boolean canClimb) {
        for (ServerPlayerEntity to : server.getPlayerManager().getPlayerList()) {
            sendTo(to, who, canClimb);
        }
    }

    /* =========================
       Client registration
       ========================= */

    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(CLIMB_STATE, (client, handler, buf, responseSender) -> {
            UUID who = buf.readUuid();
            boolean canClimb = buf.readBoolean();
            client.execute(() -> CLIENT_CAN_CLIMB.put(who, canClimb));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CLIENT_CAN_CLIMB.clear());
    }
}
