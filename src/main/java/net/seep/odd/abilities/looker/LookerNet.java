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

public final class LookerNet {
    private LookerNet() {}

    public static final Identifier S2C_OVERLAY = new Identifier(Oddities.MOD_ID, "looker_overlay");

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
}
