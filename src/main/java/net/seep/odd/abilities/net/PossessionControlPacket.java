package net.seep.odd.abilities.net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.function.BiConsumer;

public final class PossessionControlPacket {
    public static final Identifier ID = new Identifier("odd", "possess_ctrl");

    public record State(boolean f, boolean b, boolean l, boolean r,
                        boolean jump, boolean sprint,
                        float yaw, float pitch,
                        boolean actionPressed) {}

    private PossessionControlPacket() {}

    // client -> server
    public static void send(State s) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(s.f());
        buf.writeBoolean(s.b());
        buf.writeBoolean(s.l());
        buf.writeBoolean(s.r());
        buf.writeBoolean(s.jump());
        buf.writeBoolean(s.sprint());
        buf.writeFloat(s.yaw());
        buf.writeFloat(s.pitch());
        buf.writeBoolean(s.actionPressed());
        ClientPlayNetworking.send(ID, buf);
    }

    // register once in common init
    public static void registerServer(BiConsumer<ServerPlayerEntity, State> handler) {
        ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, h, buf, rs) -> {
            State s = new State(
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                    buf.readBoolean(), buf.readBoolean(),
                    buf.readFloat(), buf.readFloat(),
                    buf.readBoolean()
            );
            server.execute(() -> handler.accept(player, s));
        });
    }
}
