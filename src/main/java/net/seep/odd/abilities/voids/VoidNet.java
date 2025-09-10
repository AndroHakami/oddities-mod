package net.seep.odd.abilities.voids;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class VoidNet {
    private VoidNet() {}

    // S → C
    public static final Identifier S2C_OPEN_START = new Identifier("odd", "void/open_start");
    public static final Identifier S2C_OPEN_END   = new Identifier("odd", "void/open_end");

    /* ---------------- server sends ---------------- */

    public static void sendOpenStart(ServerPlayerEntity p, float zoomSeconds) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeFloat(zoomSeconds);
        ServerPlayNetworking.send(p, S2C_OPEN_START, buf);
    }

    public static void sendOpenEnd(ServerPlayerEntity p) {
        ServerPlayNetworking.send(p, S2C_OPEN_END, PacketByteBufs.create());
    }

    /* ---------------- client registers ---------------- */

    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(S2C_OPEN_START, (client, handler, buf, rs) -> {
            float secs = buf.readFloat();
            client.execute(() -> {
                net.seep.odd.abilities.voids.client.VoidCpmBridge.startOpenCinematic(secs);
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(S2C_OPEN_END, (client, handler, buf, rs) -> {
            client.execute(() -> {
                net.seep.odd.abilities.voids.client.VoidCpmBridge.endOpenCinematic();
            });
        });
    }
}
