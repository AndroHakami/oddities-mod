package net.seep.odd.abilities.anim;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.UUID;

/** Broadcast CPM variable changes so all clients can apply them. */
public final class CpmNet {
    private CpmNet() {}

    public static final Identifier SET_BOOL = new Identifier("odd","cpm_set_bool");
    public static final Identifier SET_FLOAT = new Identifier("odd","cpm_set_float");
    public static final Identifier PLAY_GESTURE = new Identifier("odd","cpm_play_gesture");

    // ----- server -> client send -----
    public static void sendBool(ServerPlayerEntity audience, UUID subject, String var, boolean v) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeUuid(subject); b.writeString(var); b.writeBoolean(v);
        ServerPlayNetworking.send(audience, SET_BOOL, b);
    }
    public static void sendFloat(ServerPlayerEntity audience, UUID subject, String var, float v) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeUuid(subject); b.writeString(var); b.writeFloat(v);
        ServerPlayNetworking.send(audience, SET_FLOAT, b);
    }
    public static void sendPlay(ServerPlayerEntity audience, UUID subject, String gesture, float speed, boolean loop) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeUuid(subject); b.writeString(gesture); b.writeFloat(speed); b.writeBoolean(loop);
        ServerPlayNetworking.send(audience, PLAY_GESTURE, b);
    }

    // ----- client handlers -----
    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(SET_BOOL, (client, h, buf, rs) -> {
            UUID who = buf.readUuid(); String var = buf.readString(128); boolean v = buf.readBoolean();
            client.execute(() -> CpmHolder.get().setBool(who, var, v));
        });
        ClientPlayNetworking.registerGlobalReceiver(SET_FLOAT, (client, h, buf, rs) -> {
            UUID who = buf.readUuid(); String var = buf.readString(128); float v = buf.readFloat();
            client.execute(() -> CpmHolder.get().setFloat(who, var, v));
        });
        ClientPlayNetworking.registerGlobalReceiver(PLAY_GESTURE, (client, h, buf, rs) -> {
            UUID who = buf.readUuid(); String g = buf.readString(128); float s = buf.readFloat(); boolean loop = buf.readBoolean();
            client.execute(() -> CpmHolder.get().playGesture(who, g, s, loop));
        });
    }
}
