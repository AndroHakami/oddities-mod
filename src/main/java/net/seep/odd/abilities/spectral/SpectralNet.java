package net.seep.odd.abilities.spectral;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.UUID;

/** Networking for Spectral Phase (broadcast who is phased + HUD). */
public final class SpectralNet {
    private SpectralNet() {}

    // C2S: client pressed the toggle key
    public static final Identifier TOGGLE = id("spectral/toggle");
    // S2C: tell clients "<uuid> is phased"
    public static final Identifier SET    = id("spectral/set");
    // S2C: HUD payload for local player
    public static final Identifier PHASE_HUD = id("spectral/hud");

    private static Identifier id(String p) { return new Identifier("odd", p); }

    /* ------------ server/common ------------ */
    public static void registerServer() {
        // C2S: client pressed toggle
        ServerPlayNetworking.registerGlobalReceiver(
                TOGGLE,
                (server, player, handler, buf, rs) ->
                        server.execute(() -> net.seep.odd.abilities.power.SpectralPhasePower.handleToggle(player))
        );

        // When a player starts tracking an entity, mirror state if that entity is a phased player
        EntityTrackingEvents.START_TRACKING.register((tracked, viewer) -> {
            if (tracked instanceof ServerPlayerEntity sp) {
                if (net.seep.odd.abilities.power.SpectralPhasePower.isPhased(sp)) {
                    sendSet(viewer, sp.getUuid(), true);
                }
            }
        });
    }

    /** Helper: broadcast current phase state to everyone (and to self). */
    public static void broadcastPhaseState(ServerPlayerEntity who, boolean phased) {
        // self
        sendSet(who, who.getUuid(), phased);
        // everyone tracking this player
        for (ServerPlayerEntity other : PlayerLookup.tracking(who)) {
            sendSet(other, who.getUuid(), phased);
        }
    }

    private static void sendSet(ServerPlayerEntity to, UUID whoId, boolean phased) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeUuid(whoId);
        b.writeBoolean(phased);
        ServerPlayNetworking.send(to, SET, b);
    }

    /** Send HUD payload to the specific player. */
    public static void sendHud(ServerPlayerEntity to, boolean active, int charge, int max, int cooldown) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(active);
        buf.writeVarInt(charge);
        buf.writeVarInt(max);
        buf.writeVarInt(cooldown);
        ServerPlayNetworking.send(to, PHASE_HUD, buf);
    }

    /* ------------ client-only ------------ */
    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        // Phase state → flags + CPM (for local player)
        ClientPlayNetworking.registerGlobalReceiver(SET, (client, handler, buf, rs) -> {
            UUID id = buf.readUuid();
            boolean on = buf.readBoolean();
            client.execute(() -> {
                // existing flags in your project (keep them)
                SpectralRenderState.setPhased(id, on);
                SpectralPhaseClientFlag.set(id, on);

                // If this is our local player, tell CPM to play/stop "phase"
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player != null && mc.player.getUuid().equals(id)) {
                    net.seep.odd.abilities.spectral.client.PhaseCpmBridge.onPhaseActiveChanged(on);
                }
            });
        });

        // HUD payload → SpectralPhasePower.Client
        ClientPlayNetworking.registerGlobalReceiver(PHASE_HUD, (client, handler, buf, rs) -> {
            boolean a = buf.readBoolean();
            int c     = buf.readVarInt();
            int m     = buf.readVarInt();
            int cd    = buf.readVarInt();
            client.execute(() ->
                    net.seep.odd.abilities.power.SpectralPhasePower.Client.onHud(a, c, m, cd)
            );
        });
    }
}
