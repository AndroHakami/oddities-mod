package net.seep.odd.device.social;

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

public final class SocialNetworking {
    private SocialNetworking() {}

    public static final Identifier C2S_REQUEST_SYNC = new Identifier(Oddities.MOD_ID, "social_request_sync");
    public static final Identifier S2C_SYNC         = new Identifier(Oddities.MOD_ID, "social_sync");
    public static final Identifier C2S_CREATE_POST  = new Identifier(Oddities.MOD_ID, "social_create_post");
    public static final Identifier C2S_DELETE_POST  = new Identifier(Oddities.MOD_ID, "social_delete_post");
    public static final Identifier C2S_REACT        = new Identifier(Oddities.MOD_ID, "social_react");
    public static final Identifier C2S_REPLY        = new Identifier(Oddities.MOD_ID, "social_reply");

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(SocialManager::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(SocialManager::save);

        ServerPlayNetworking.registerGlobalReceiver(C2S_REQUEST_SYNC, (server, player, handler, buf, sender) ->
                server.execute(() -> sendSync(player)));

        ServerPlayNetworking.registerGlobalReceiver(C2S_CREATE_POST, (server, player, handler, buf, sender) -> {
            String title = buf.readString(SocialManager.TITLE_MAX_LEN);
            String body = buf.readString(SocialManager.BODY_MAX_LEN);

            String mainImageUrl = null;
            if (buf.readBoolean()) {
                mainImageUrl = buf.readString(SocialManager.IMAGE_URL_MAX_LEN);
            }

            final String finalMainImageUrl = mainImageUrl;
            server.execute(() -> {
                String error = SocialManager.createPost(player, title, body, finalMainImageUrl);
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
                String error = SocialManager.deletePost(player, postId);
                if (error != null) {
                    player.sendMessage(Text.literal(error), true);
                    sendSync(player);
                    return;
                }
                broadcastSync(server);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_REACT, (server, player, handler, buf, sender) -> {
            UUID postId = buf.readUuid();
            byte vote = buf.readByte();
            server.execute(() -> {
                String error = SocialManager.react(player, postId, vote);
                if (error != null) {
                    player.sendMessage(Text.literal(error), true);
                    sendSync(player);
                    return;
                }
                broadcastSync(server);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_REPLY, (server, player, handler, buf, sender) -> {
            UUID postId = buf.readUuid();
            String body = buf.readString(SocialManager.REPLY_MAX_LEN);

            server.execute(() -> {
                String error = SocialManager.reply(player, postId, body);
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
        List<SocialPost> posts = SocialManager.getPosts();

        buf.writeVarInt(posts.size());
        for (SocialPost post : posts) {
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