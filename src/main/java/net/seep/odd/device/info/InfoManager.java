package net.seep.odd.device.info;

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

public final class InfoManager {
    private InfoManager() {}

    public static final int TITLE_MAX_LEN = 96;
    public static final int SOURCE_MAX_LEN = 48;
    public static final int BODY_MAX_LEN = 8000;
    public static final int IMAGE_URL_MAX_LEN = 700;
    public static final int MAX_IMAGES = 8;

    private static final List<InfoPost> POSTS = new ArrayList<>();

    public static List<InfoPost> getPosts() {
        POSTS.sort(Comparator.comparingLong((InfoPost p) -> p.createdAt).reversed());
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
                POSTS.add(InfoPost.fromNbt((NbtCompound) posts.get(i)));
            }

            POSTS.sort(Comparator.comparingLong((InfoPost p) -> p.createdAt).reversed());
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
            for (InfoPost post : POSTS) {
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

    public static String createPost(ServerPlayerEntity player, String source, String title, String body, List<String> imageUrls) {
        if (!player.hasPermissionLevel(2)) {
            return "You don't have permission to post there.";
        }

        source = normalizeInline(source);
        title = normalizeInline(title);
        body = normalizeBody(body);

        if (title.isEmpty()) return "Post title can't be empty.";
        if (body.isBlank()) return "Post body can't be empty.";
        if (title.length() > TITLE_MAX_LEN) return "Title is too long.";
        if (source.length() > SOURCE_MAX_LEN) return "Source is too long.";
        if (body.length() > BODY_MAX_LEN) return "Post is too long.";

        List<String> cleanedImages = new ArrayList<>();
        if (imageUrls != null) {
            if (imageUrls.size() > MAX_IMAGES) {
                return "Too many images.";
            }
            for (String imageUrl : imageUrls) {
                String error = validateUrl(imageUrl);
                if (error != null) return error;
                if (hasText(imageUrl)) {
                    cleanedImages.add(imageUrl.trim());
                }
            }
        }

        InfoPost post = new InfoPost(
                UUID.randomUUID(),
                player.getUuid(),
                player.getName().getString(),
                System.currentTimeMillis(),
                source,
                title,
                body
        );
        post.imageUrls.addAll(cleanedImages);

        POSTS.add(0, post);
        save(player.getServer());
        return null;
    }

    public static String deletePost(ServerPlayerEntity actor, UUID postId) {
        if (!actor.hasPermissionLevel(2)) {
            return "You don't have permission to delete that post.";
        }

        InfoPost found = null;
        for (InfoPost post : POSTS) {
            if (post.id.equals(postId)) {
                found = post;
                break;
            }
        }

        if (found == null) return "That post doesn't exist.";

        POSTS.remove(found);
        save(actor.getServer());
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
                    lowerPath.endsWith(".gif") ||
                    lowerPath.endsWith(".webp");

            if (!directImage) {
                return "Use a direct image URL ending in .png, .jpg, .jpeg, .gif, or .webp.";
            }

            return null;
        } catch (Exception e) {
            return "That image URL isn't valid.";
        }
    }

    private static String normalizeInline(String s) {
        if (s == null) return "";
        return s.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String normalizeBody(String body) {
        if (body == null) return "";
        return body.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static Path saveDir(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("odd_info");
    }

    private static Path saveFile(MinecraftServer server) {
        return saveDir(server).resolve("posts.nbt");
    }
}
