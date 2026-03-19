package net.seep.odd.device.social;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

public final class SocialManager {
    private SocialManager() {}

    public static final int TITLE_MAX_LEN = 80;
    public static final int BODY_MAX_LEN = 4000;
    public static final int REPLY_MAX_LEN = 600;
    public static final int IMAGE_URL_MAX_LEN = 700;

    private static final List<SocialPost> POSTS = new ArrayList<>();

    public static List<SocialPost> getPosts() {
        POSTS.sort(Comparator.comparingLong((SocialPost p) -> p.createdAt).reversed());
        return POSTS;
    }

    public static void load(MinecraftServer server) {
        POSTS.clear();

        Path file = saveFile(server);
        if (!Files.exists(file)) return;

        try (InputStream in = Files.newInputStream(file)) {
            NbtCompound root = NbtIo.readCompressed(in);
            if (root == null) return;

            NbtList posts = root.getList("Posts", 10);
            for (int i = 0; i < posts.size(); i++) {
                POSTS.add(SocialPost.fromNbt((NbtCompound) posts.get(i)));
            }

            POSTS.sort(Comparator.comparingLong((SocialPost p) -> p.createdAt).reversed());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save(MinecraftServer server) {
        try {
            Path dir = saveDir(server);
            Files.createDirectories(dir);

            NbtCompound root = new NbtCompound();
            NbtList posts = new NbtList();
            for (SocialPost post : POSTS) {
                posts.add(post.toNbt());
            }
            root.put("Posts", posts);

            try (OutputStream out = Files.newOutputStream(saveFile(server))) {
                NbtIo.writeCompressed(root, out);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String createPost(ServerPlayerEntity player, String title, String body, String mainImageUrl) {
        title = title == null ? "" : title.trim();
        body = normalizeBody(body);

        if (title.isEmpty()) return "Post title can't be empty.";
        if (body.isBlank()) return "Post body can't be empty.";
        if (title.length() > TITLE_MAX_LEN) return "Title is too long.";
        if (body.length() > BODY_MAX_LEN) return "Post is too long.";

        String mainError = validateUrl(mainImageUrl);
        if (mainError != null) return mainError;

        SocialPost post = new SocialPost(
                UUID.randomUUID(),
                player.getUuid(),
                player.getName().getString(),
                System.currentTimeMillis(),
                title,
                body,
                hasText(mainImageUrl) ? mainImageUrl.trim() : null
        );

        POSTS.add(0, post);
        save(player.getServer());
        return null;
    }

    public static String deletePost(ServerPlayerEntity actor, UUID postId) {
        SocialPost post = find(postId);
        if (post == null) return "That post doesn't exist.";

        boolean own = post.authorUuid.equals(actor.getUuid());
        boolean op = actor.hasPermissionLevel(2);
        if (!own && !op) return "You can only delete your own posts.";

        POSTS.remove(post);
        save(actor.getServer());
        return null;
    }

    public static String react(ServerPlayerEntity actor, UUID postId, byte vote) {
        SocialPost post = find(postId);
        if (post == null) return "That post doesn't exist.";

        if (vote > 1) vote = 1;
        if (vote < -1) vote = -1;

        if (vote == 0) {
            post.reactions.remove(actor.getUuid());
        } else {
            post.reactions.put(actor.getUuid(), vote);
        }

        save(actor.getServer());
        return null;
    }

    public static String reply(ServerPlayerEntity actor, UUID postId, String body) {
        SocialPost post = find(postId);
        if (post == null) return "That post doesn't exist.";

        body = normalizeBody(body);
        if (body.isBlank()) return "Reply can't be empty.";
        if (body.length() > REPLY_MAX_LEN) return "Reply is too long.";

        post.replies.add(new SocialReply(
                UUID.randomUUID(),
                actor.getUuid(),
                actor.getName().getString(),
                System.currentTimeMillis(),
                body
        ));

        save(actor.getServer());
        return null;
    }

    private static SocialPost find(UUID postId) {
        for (SocialPost post : POSTS) {
            if (post.id.equals(postId)) return post;
        }
        return null;
    }

    private static String validateUrl(String url) {
        if (!hasText(url)) return null;

        try {
            url = url.trim();

            if (url.length() > IMAGE_URL_MAX_LEN) {
                return "Image URL is too long.";
            }

            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            String path = uri.getPath();

            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return "Image URL must start with http:// or https://";
            }

            if (host == null || host.isBlank()) {
                return "Image URL is missing a host.";
            }

            if (path == null) {
                return "That image URL isn't valid.";
            }

            String lowerPath = path.toLowerCase(Locale.ROOT);
            boolean directImage =
                    lowerPath.endsWith(".png") ||
                            lowerPath.endsWith(".jpg") ||
                            lowerPath.endsWith(".jpeg") ||
                            lowerPath.endsWith(".gif");

            if (!directImage) {
                return "Use a direct image URL ending in .png, .jpg, .jpeg, or .gif.";
            }

            return null;
        } catch (Exception e) {
            return "That image URL isn't valid.";
        }
    }

    private static String normalizeBody(String body) {
        if (body == null) return "";
        return body.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static Path saveDir(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("odd_social");
    }

    private static Path saveFile(MinecraftServer server) {
        return saveDir(server).resolve("posts.nbt");
    }
}