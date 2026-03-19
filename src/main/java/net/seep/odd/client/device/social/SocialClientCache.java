package net.seep.odd.client.device.social;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.seep.odd.device.social.SocialNetworking;
import net.seep.odd.device.social.SocialPost;


@Environment(EnvType.CLIENT)
public final class SocialClientCache {
    private SocialClientCache() {}

    private static final List<SocialPost> POSTS = new ArrayList<>();

    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(SocialNetworking.S2C_SYNC, (client, handler, buf, sender) -> {
            List<SocialPost> incoming = new ArrayList<>();
            int count = buf.readVarInt();
            for (int i = 0; i < count; i++) {
                incoming.add(SocialPost.read(buf));
            }

            incoming.sort(Comparator.comparingLong((SocialPost p) -> p.createdAt).reversed());

            client.execute(() -> {
                POSTS.clear();
                POSTS.addAll(incoming);
            });
        });
    }

    public static List<SocialPost> posts() {
        return POSTS;
    }
}