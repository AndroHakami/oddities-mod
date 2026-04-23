package net.seep.odd.client.device.info;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.seep.odd.device.info.InfoNetworking;
import net.seep.odd.device.info.InfoPost;

@Environment(EnvType.CLIENT)
public final class InfoClientCache {
    private InfoClientCache() {}

    private static final List<InfoPost> POSTS = new ArrayList<>();
    private static boolean canPost = false;
    private static boolean inited = false;

    public static void initClient() {
        if (inited) return;
        inited = true;

        ClientPlayNetworking.registerGlobalReceiver(InfoNetworking.S2C_SYNC, (client, handler, buf, sender) -> {
            boolean incomingCanPost = buf.readBoolean();

            List<InfoPost> incoming = new ArrayList<>();
            int count = buf.readVarInt();
            for (int i = 0; i < count; i++) {
                incoming.add(InfoPost.read(buf));
            }

            incoming.sort(Comparator.comparingLong((InfoPost p) -> p.createdAt).reversed());

            client.execute(() -> {
                canPost = incomingCanPost;
                POSTS.clear();
                POSTS.addAll(incoming);
            });
        });
    }

    public static List<InfoPost> posts() {
        return POSTS;
    }

    public static boolean canPost() {
        return canPost;
    }
}
