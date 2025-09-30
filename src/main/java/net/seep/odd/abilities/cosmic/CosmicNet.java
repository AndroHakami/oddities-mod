package net.seep.odd.abilities.cosmic;


import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.power.CosmicPower;

/**
 * Networking for the Cosmic power:
 * C2S: primary_press/release, secondary_press/release
 * S2C: slash_ping (tiny HUD flash)
 */
public final class CosmicNet {
    private CosmicNet() {}

    // Channel IDs
    public static final Identifier C2S_PRIMARY_PRESS   = id("cosmic_primary_press");
    public static final Identifier C2S_PRIMARY_RELEASE = id("cosmic_primary_release");
    public static final Identifier C2S_SECONDARY_PRESS = id("cosmic_secondary_press");
    public static final Identifier C2S_SECONDARY_RELEASE = id("cosmic_secondary_release");

    public static final Identifier S2C_SLASH_PING      = id("cosmic_slash_ping");

    private static Identifier id(String path) {
        return new Identifier(Oddities.MOD_ID, path);
    }

    /* ======================= Common (server) ======================= */
    public static void register() {
        // PRIMARY press -> start stance/charge
        ServerPlayNetworking.registerGlobalReceiver(C2S_PRIMARY_PRESS, (server, player, handler, buf, response) ->
                server.execute(() -> CosmicPower.INSTANCE.activate(player)));

        // PRIMARY release -> fire slash (if your input layer sends releases)
        ServerPlayNetworking.registerGlobalReceiver(C2S_PRIMARY_RELEASE, (server, player, handler, buf, response) ->
                server.execute(() -> CosmicPower.primaryRelease(player)));

        // SECONDARY press -> begin hover (or toggle fire if already hovering)
        ServerPlayNetworking.registerGlobalReceiver(C2S_SECONDARY_PRESS, (server, player, handler, buf, response) ->
                server.execute(() -> CosmicPower.INSTANCE.activateSecondary(player)));

        // SECONDARY release -> fire queued volley (if using hold-to-aim UX)
        ServerPlayNetworking.registerGlobalReceiver(C2S_SECONDARY_RELEASE, (server, player, handler, buf, response) ->
                server.execute(() -> CosmicPower.secondaryHoldEnd(player)));
    }

    /* ======================= Client-only ======================= */
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(S2C_SLASH_PING, (client, handler, buf, response) ->
                client.execute(() -> CosmicPower.Client.pingSlash()));
    }

    /* ======================= Helpers ======================= */
    // Server -> client: tiny HUD ping after slash
    public static void sendSlashPing(ServerPlayerEntity to) {
        ServerPlayNetworking.send(to, S2C_SLASH_PING, PacketByteBufs.empty());
    }

    // Client -> server senders (call these from your keybind/input layer)
    public static void sendPrimaryPress()   { ClientPlayNetworking.send(C2S_PRIMARY_PRESS,   PacketByteBufs.empty()); }
    public static void sendPrimaryRelease() { ClientPlayNetworking.send(C2S_PRIMARY_RELEASE, PacketByteBufs.empty()); }
    public static void sendSecondaryPress() { ClientPlayNetworking.send(C2S_SECONDARY_PRESS, PacketByteBufs.empty()); }
    public static void sendSecondaryRelease(){ClientPlayNetworking.send(C2S_SECONDARY_RELEASE,PacketByteBufs.empty()); }
}
