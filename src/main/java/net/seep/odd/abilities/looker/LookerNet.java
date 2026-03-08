package net.seep.odd.abilities.looker;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.power.LookerPower;

public final class LookerNet {
    private LookerNet() {}

    public static final Identifier S2C_OVERLAY = new Identifier(Oddities.MOD_ID, "looker_overlay");

    // ✅ NEW: client -> server request (in case your ability activation is client-side)
    public static final Identifier C2S_TOGGLE_INVIS = new Identifier(Oddities.MOD_ID, "looker_toggle_invis");

    private static boolean c2sRegistered = false;

    /** Call from common init (server-safe). Idempotent. */
    public static void registerC2S() {
        if (c2sRegistered) return;
        c2sRegistered = true;

        ServerPlayNetworking.registerGlobalReceiver(C2S_TOGGLE_INVIS, (server, player, handler, buf, responder) -> {
            server.execute(() -> {
                LookerPower.netToggleInvis(player);
            });
        });
    }

    /** Server -> client: toggle overlay + send current meter + max (ticks). */
    public static void sendOverlay(ServerPlayerEntity to, boolean on, int meter, int max) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(on);
        buf.writeVarInt(Math.max(0, meter));
        buf.writeVarInt(Math.max(1, max));
        ServerPlayNetworking.send(to, S2C_OVERLAY, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(S2C_OVERLAY, (client, handler, buf, responseSender) -> {
            boolean on  = buf.readBoolean();
            int meter   = buf.readVarInt();
            int max     = buf.readVarInt();
            client.execute(() -> LookerClient.handleOverlay(on, meter, max));
        });
    }

    /** Optional: call this from your client keybind/ability trigger if secondary isn’t reaching the server. */
    @Environment(EnvType.CLIENT)
    public static void requestToggleInvis() {
        ClientPlayNetworking.send(C2S_TOGGLE_INVIS, PacketByteBufs.create());
    }
}