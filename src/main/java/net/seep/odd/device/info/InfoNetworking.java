package net.seep.odd.device.info;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

public final class InfoNetworking {
    private InfoNetworking() {}

    public static final Identifier C2S_REQUEST_SYNC = new Identifier(Oddities.MOD_ID, "info_request_sync");
    public static final Identifier S2C_SYNC         = new Identifier(Oddities.MOD_ID, "info_sync");
    public static final Identifier C2S_CREATE_POST  = new Identifier(Oddities.MOD_ID, "info_create_post");
    public static final Identifier C2S_DELETE_POST  = new Identifier(Oddities.MOD_ID, "info_delete_post");

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(InfoManager::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(InfoManager::save);

        ServerPlayNetworking.registerGlobalReceiver(C2S_REQUEST_SYNC, (server, player, handler, buf, sender) ->
                server.execute(() -> sendSync(player)));

        ServerPlayNetworking.registerGlobalReceiver(C2S_CREATE_POST, (server, player, handler, buf, sender) -> {
            String source = buf.readString(InfoManager.SOURCE_MAX_LEN);
            String title = buf.readString(InfoManager.TITLE_MAX_LEN);
            String body = buf.readString(InfoManager.BODY_MAX_LEN);

            int imageCount = buf.readVarInt();
            List<String> imageUrls = new ArrayList<>();
            for (int i = 0; i < imageCount; i++) {
                imageUrls.add(buf.readString(InfoManager.IMAGE_URL_MAX_LEN));
            }

            server.execute(() -> {
                String error = InfoManager.createPost(player, source, title, body, imageUrls);
                if (error != null) {
                    player.sendMessage(Text.literal(error), true);
                    sendSync(player);
                    return;
                }
                broadcastSync(server);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_DELETE_POST, (server, player, handler, buf, sender) -> {
            UUID postId = buf.readUuid();
            server.execute(() -> {
                String error = InfoManager.deletePost(player, postId);
                if (error != null) {
                    player.sendMessage(Text.literal(error), true);
                    sendSync(player);
                    return;
                }
                broadcastSync(server);
            });
        });
    }

    public static void sendSync(ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeBoolean(player.hasPermissionLevel(2));

        List<InfoPost> posts = InfoManager.getPosts();
        buf.writeVarInt(posts.size());
        for (InfoPost post : posts) {
            post.write(buf);
        }

        ServerPlayNetworking.send(player, S2C_SYNC, buf);
    }

    public static void broadcastSync(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendSync(player);
        }
    }
}
