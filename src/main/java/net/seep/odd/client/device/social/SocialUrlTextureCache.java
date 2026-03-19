package net.seep.odd.client.device.social;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public final class SocialUrlTextureCache {
    private SocialUrlTextureCache() {}

    public record LoadedImage(Identifier id, int width, int height) {}
    public record Fit(int drawW, int drawH) {}

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 7000;
    private static final int MAX_BYTES = 5_000_000;
    private static final int PLACEHOLDER_HEIGHT = 36;
    private static final int PLACEHOLDER_WIDTH = 110;
    private static final int MIN_FRAME_DELAY_MS = 20;

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

    private static final class Entry {
        volatile boolean loading;
        volatile boolean failed;
        volatile String failReason = "";

        volatile boolean animated;
        volatile LoadedImage image;
        volatile List<LoadedImage> frames;
        volatile int[] frameDelaysMs;
        volatile int width;
        volatile int height;
    }

    private static final class DecodedGif {
        final List<BufferedImage> frames = new ArrayList<>();
        final List<Integer> delaysMs = new ArrayList<>();
        int width;
        int height;
    }

    private static final class FrameInfo {
        int left;
        int top;
        int width;
        int height;
        int delayMs;
        String disposal = "none";
    }

    public static LoadedImage get(String url) {
        if (url == null || url.isBlank()) return null;

        Entry entry = CACHE.computeIfAbsent(url, k -> {
            Entry e = new Entry();
            startLoad(k, e);
            return e;
        });

        return getCurrentImage(entry);
    }

    public static Fit measureFit(String url, int maxW, int maxH) {
        if (url == null || url.isBlank()) return new Fit(0, 0);

        Entry entry = CACHE.computeIfAbsent(url, k -> {
            Entry e = new Entry();
            startLoad(k, e);
            return e;
        });

        int sourceW = entry.width;
        int sourceH = entry.height;

        if (sourceW <= 0 || sourceH <= 0) {
            LoadedImage img = getCurrentImage(entry);
            if (img != null) {
                sourceW = img.width();
                sourceH = img.height();
            }
        }

        if (sourceW <= 0 || sourceH <= 0) {
            return new Fit(Math.min(maxW, PLACEHOLDER_WIDTH), Math.min(maxH, PLACEHOLDER_HEIGHT));
        }

        float scale = Math.min((float) maxW / (float) sourceW, (float) maxH / (float) sourceH);
        scale = Math.min(scale, 1.0f);

        int drawW = Math.max(1, Math.round(sourceW * scale));
        int drawH = Math.max(1, Math.round(sourceH * scale));
        return new Fit(drawW, drawH);
    }

    public static int fittedHeight(String url, int maxW, int maxH) {
        return measureFit(url, maxW, maxH).drawH();
    }

    public static int drawContained(DrawContext context, String url, int x, int y, int maxW, int maxH) {
        if (url == null || url.isBlank()) return 0;

        Entry entry = CACHE.computeIfAbsent(url, k -> {
            Entry e = new Entry();
            startLoad(k, e);
            return e;
        });

        Fit fit = measureFit(url, maxW, maxH);
        int drawX = x + ((maxW - fit.drawW()) / 2);
        int drawY = y;

        LoadedImage img = getCurrentImage(entry);
        if (img == null) {
            int color = entry.failed ? 0x885A2A2A : 0x66304060;
            context.fill(drawX, drawY, drawX + fit.drawW(), drawY + fit.drawH(), color);

            context.drawTextWithShadow(
                    MinecraftClient.getInstance().textRenderer,
                    Text.literal(entry.failed ? "image failed" : "loading..."),
                    drawX + 6,
                    drawY + Math.max(4, (fit.drawH() / 2) - 4),
                    entry.failed ? 0xFFFFC0C0 : 0xFFD8E6FF
            );
            return fit.drawH();
        }

        context.drawTexture(
                img.id(),
                drawX,
                drawY,
                fit.drawW(),
                fit.drawH(),
                0.0f,
                0.0f,
                img.width(),
                img.height(),
                img.width(),
                img.height()
        );

        return fit.drawH();
    }

    private static LoadedImage getCurrentImage(Entry entry) {
        if (entry == null) return null;

        if (entry.animated && entry.frames != null && !entry.frames.isEmpty()) {
            int idx = getGifFrameIndex(entry.frameDelaysMs);
            if (idx < 0 || idx >= entry.frames.size()) idx = 0;
            return entry.frames.get(idx);
        }

        return entry.image;
    }

    private static int getGifFrameIndex(int[] delaysMs) {
        if (delaysMs == null || delaysMs.length == 0) return 0;

        int total = 0;
        for (int delay : delaysMs) {
            total += Math.max(delay, MIN_FRAME_DELAY_MS);
        }
        if (total <= 0) return 0;

        int t = (int) (System.currentTimeMillis() % total);

        int acc = 0;
        for (int i = 0; i < delaysMs.length; i++) {
            acc += Math.max(delaysMs[i], MIN_FRAME_DELAY_MS);
            if (t < acc) return i;
        }

        return 0;
    }

    private static void startLoad(String url, Entry entry) {
        if (entry.loading) return;
        entry.loading = true;
        entry.failed = false;
        entry.failReason = "";

        CompletableFuture.runAsync(() -> {
            try {
                byte[] bytes = download(url);
                boolean gif = isGif(bytes, url);

                if (gif) {
                    DecodedGif decoded = decodeGif(bytes);

                    MinecraftClient client = MinecraftClient.getInstance();
                    client.execute(() -> {
                        try {
                            registerGif(client, url, decoded, entry);
                            entry.failed = false;
                            entry.failReason = "";
                        } catch (Exception e) {
                            entry.failed = true;
                            entry.failReason = "GIF registration failed: " + e.getMessage();
                            System.out.println("[Oddities Social] Failed to register GIF for URL: " + url);
                            e.printStackTrace();
                        } finally {
                            entry.loading = false;
                        }
                    });
                } else {
                    NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
                    if (image == null) {
                        throw new IllegalStateException("NativeImage.read returned null");
                    }

                    MinecraftClient client = MinecraftClient.getInstance();
                    client.execute(() -> {
                        try {
                            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
                            Identifier id = client.getTextureManager().registerDynamicTexture(
                                    "odd_social_" + Integer.toHexString(url.hashCode()),
                                    texture
                            );
                            entry.animated = false;
                            entry.image = new LoadedImage(id, image.getWidth(), image.getHeight());
                            entry.frames = null;
                            entry.frameDelaysMs = null;
                            entry.width = image.getWidth();
                            entry.height = image.getHeight();
                            entry.failed = false;
                            entry.failReason = "";
                        } catch (Exception e) {
                            entry.failed = true;
                            entry.failReason = "Texture registration failed: " + e.getMessage();
                            System.out.println("[Oddities Social] Failed to register texture for URL: " + url);
                            e.printStackTrace();
                        } finally {
                            entry.loading = false;
                        }
                    });
                }
            } catch (Exception e) {
                entry.failed = true;
                entry.loading = false;
                entry.failReason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();

                System.out.println("[Oddities Social] Failed to load image URL: " + url);
                System.out.println("[Oddities Social] Reason: " + entry.failReason);
                e.printStackTrace();
            }
        });
    }

    private static void registerGif(MinecraftClient client, String url, DecodedGif decoded, Entry entry) {
        List<LoadedImage> outFrames = new ArrayList<>(decoded.frames.size());

        for (int i = 0; i < decoded.frames.size(); i++) {
            NativeImage nativeImage = bufferedToNative(decoded.frames.get(i));
            NativeImageBackedTexture texture = new NativeImageBackedTexture(nativeImage);
            Identifier id = client.getTextureManager().registerDynamicTexture(
                    "odd_social_" + Integer.toHexString(url.hashCode()) + "_f_" + i,
                    texture
            );
            outFrames.add(new LoadedImage(id, nativeImage.getWidth(), nativeImage.getHeight()));
        }

        int[] delays = new int[decoded.delaysMs.size()];
        for (int i = 0; i < decoded.delaysMs.size(); i++) {
            delays[i] = decoded.delaysMs.get(i);
        }

        entry.animated = outFrames.size() > 1;
        entry.frames = outFrames;
        entry.frameDelaysMs = delays;
        entry.image = outFrames.isEmpty() ? null : outFrames.get(0);
        entry.width = decoded.width;
        entry.height = decoded.height;
    }

    private static boolean isGif(byte[] bytes, String url) {
        if (url != null && url.toLowerCase().endsWith(".gif")) {
            return true;
        }

        if (bytes.length >= 6) {
            String header = new String(bytes, 0, 6);
            return "GIF87a".equals(header) || "GIF89a".equals(header);
        }

        return false;
    }

    private static DecodedGif decodeGif(byte[] bytes) throws Exception {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            throw new IllegalStateException("No GIF reader available");
        }

        ImageReader reader = readers.next();

        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(stream, false);

            int frameCount = reader.getNumImages(true);
            if (frameCount <= 0) {
                throw new IllegalStateException("GIF has no frames");
            }

            int screenW = 0;
            int screenH = 0;

            IIOMetadata streamMeta = reader.getStreamMetadata();
            if (streamMeta != null) {
                try {
                    Node root = streamMeta.getAsTree("javax_imageio_gif_stream_1.0");
                    Node lsd = child(root, "LogicalScreenDescriptor");
                    if (lsd != null) {
                        screenW = intAttr(lsd, "logicalScreenWidth", 0);
                        screenH = intAttr(lsd, "logicalScreenHeight", 0);
                    }
                } catch (Exception ignored) {
                }
            }

            if (screenW <= 0 || screenH <= 0) {
                for (int i = 0; i < frameCount; i++) {
                    FrameInfo info = frameInfo(reader.getImageMetadata(i));
                    screenW = Math.max(screenW, info.left + info.width);
                    screenH = Math.max(screenH, info.top + info.height);
                }
            }

            if (screenW <= 0 || screenH <= 0) {
                throw new IllegalStateException("Invalid GIF dimensions");
            }

            BufferedImage master = new BufferedImage(screenW, screenH, BufferedImage.TYPE_INT_ARGB);
            BufferedImage previousComposite = null;
            String previousDisposal = "none";
            int previousLeft = 0;
            int previousTop = 0;
            int previousW = 0;
            int previousH = 0;

            DecodedGif out = new DecodedGif();
            out.width = screenW;
            out.height = screenH;

            for (int i = 0; i < frameCount; i++) {
                if ("restoreToPrevious".equals(previousDisposal) && previousComposite != null) {
                    master = copyImage(previousComposite);
                } else if ("restoreToBackgroundColor".equals(previousDisposal)) {
                    Graphics2D g = master.createGraphics();
                    g.setComposite(AlphaComposite.Clear);
                    g.fillRect(previousLeft, previousTop, previousW, previousH);
                    g.dispose();
                }

                BufferedImage frame = reader.read(i);
                FrameInfo info = frameInfo(reader.getImageMetadata(i));

                if ("restoreToPrevious".equals(info.disposal)) {
                    previousComposite = copyImage(master);
                } else {
                    previousComposite = null;
                }

                Graphics2D g = master.createGraphics();
                g.drawImage(frame, info.left, info.top, null);
                g.dispose();

                out.frames.add(copyImage(master));
                out.delaysMs.add(Math.max(info.delayMs, MIN_FRAME_DELAY_MS));

                previousDisposal = info.disposal;
                previousLeft = info.left;
                previousTop = info.top;
                previousW = info.width > 0 ? info.width : frame.getWidth();
                previousH = info.height > 0 ? info.height : frame.getHeight();
            }

            return out;
        } finally {
            reader.dispose();
        }
    }

    private static FrameInfo frameInfo(IIOMetadata meta) throws Exception {
        FrameInfo out = new FrameInfo();
        Node root = meta.getAsTree("javax_imageio_gif_image_1.0");

        Node descriptor = child(root, "ImageDescriptor");
        if (descriptor != null) {
            out.left = intAttr(descriptor, "imageLeftPosition", 0);
            out.top = intAttr(descriptor, "imageTopPosition", 0);
            out.width = intAttr(descriptor, "imageWidth", 0);
            out.height = intAttr(descriptor, "imageHeight", 0);
        }

        Node gce = child(root, "GraphicControlExtension");
        if (gce != null) {
            out.delayMs = Math.max(1, intAttr(gce, "delayTime", 10)) * 10;
            out.disposal = stringAttr(gce, "disposalMethod", "none");
        } else {
            out.delayMs = 100;
        }

        return out;
    }

    private static Node child(Node root, String name) {
        if (root == null) return null;

        for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (name.equals(n.getNodeName())) {
                return n;
            }
        }
        return null;
    }

    private static int intAttr(Node node, String name, int def) {
        if (node == null) return def;
        NamedNodeMap attrs = node.getAttributes();
        if (attrs == null) return def;
        Node attr = attrs.getNamedItem(name);
        if (attr == null) return def;

        try {
            return Integer.parseInt(attr.getNodeValue());
        } catch (Exception e) {
            return def;
        }
    }

    private static String stringAttr(Node node, String name, String def) {
        if (node == null) return def;
        NamedNodeMap attrs = node.getAttributes();
        if (attrs == null) return def;
        Node attr = attrs.getNamedItem(name);
        return attr == null ? def : attr.getNodeValue();
    }

    private static BufferedImage copyImage(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    private static NativeImage bufferedToNative(BufferedImage src) {
        NativeImage out = new NativeImage(src.getWidth(), src.getHeight(), true);

        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int argb = src.getRGB(x, y);
                out.setColor(x, y, argbToAbgr(argb));
            }
        }

        return out;
    }

    private static int argbToAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static byte[] download(String urlText) throws Exception {
        URI uri = URI.create(urlText.trim());
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "Oddities Social Image Loader");
        conn.setRequestProperty("Accept", "image/png,image/jpeg,image/jpg,image/gif,image/*;q=0.9,*/*;q=0.5");

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code);
        }

        String contentType = conn.getContentType();
        if (contentType != null && !contentType.toLowerCase().startsWith("image/")) {
            throw new IllegalStateException("URL did not return an image. Content-Type=" + contentType);
        }

        try (InputStream in = conn.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int total = 0;
            int read;

            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BYTES) {
                    throw new IllegalStateException("Image too large");
                }
                out.write(buffer, 0, read);
            }

            return out.toByteArray();
        }
    }

    public static void clear(String url) {
        if (url != null) {
            CACHE.remove(url);
        }
    }

    public static void clearAll() {
        CACHE.clear();
    }
}