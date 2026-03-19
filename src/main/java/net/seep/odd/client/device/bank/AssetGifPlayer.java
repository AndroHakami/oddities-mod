package net.seep.odd.client.device.bank;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.mojang.blaze3d.systems.RenderSystem;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public final class AssetGifPlayer {
    private static final int MIN_FRAME_DELAY_MS = 20;

    private final Identifier gifResource;
    private final String dynamicPrefix;

    private boolean attemptedLoad = false;
    private boolean failed = false;

    private final List<LoadedFrame> frames = new ArrayList<>();
    private int[] delaysMs = new int[0];
    private int width = 0;
    private int height = 0;

    public AssetGifPlayer(Identifier gifResource, String dynamicPrefix) {
        this.gifResource = gifResource;
        this.dynamicPrefix = dynamicPrefix;
    }

    public void ensureLoaded() {
        if (attemptedLoad) return;
        attemptedLoad = true;

        MinecraftClient client = MinecraftClient.getInstance();

        try {
            Resource resource = client.getResourceManager().getResource(gifResource)
                    .orElseThrow(() -> new IllegalStateException("Missing gif resource: " + gifResource));

            byte[] bytes;
            try (InputStream in = resource.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                bytes = out.toByteArray();
            }

            DecodedGif decoded = decodeGif(bytes);
            registerFrames(client, decoded);
        } catch (Exception e) {
            failed = true;
            System.out.println("[Oddities Bank GIF] Failed to load gif: " + gifResource);
            e.printStackTrace();
        }
    }

    public boolean drawCover(DrawContext context, int x, int y, int targetW, int targetH, float alpha) {
        ensureLoaded();
        if (failed || frames.isEmpty() || width <= 0 || height <= 0) return false;

        LoadedFrame frame = frames.get(currentFrameIndex());
        float scale = Math.max((float) targetW / (float) width, (float) targetH / (float) height);

        float srcW = targetW / scale;
        float srcH = targetH / scale;
        float srcX = (width - srcW) * 0.5f;
        float srcY = (height - srcH) * 0.5f;

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);

        context.drawTexture(
                frame.id(),
                x, y,
                targetW, targetH,
                srcX, srcY,
                (int) srcW, (int) srcH,
                width, height
        );

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        return true;
    }

    private int currentFrameIndex() {
        if (delaysMs.length == 0) return 0;

        int total = 0;
        for (int d : delaysMs) total += Math.max(d, MIN_FRAME_DELAY_MS);
        if (total <= 0) return 0;

        int t = (int) (System.currentTimeMillis() % total);
        int acc = 0;

        for (int i = 0; i < delaysMs.length; i++) {
            acc += Math.max(delaysMs[i], MIN_FRAME_DELAY_MS);
            if (t < acc) return i;
        }

        return 0;
    }

    private void registerFrames(MinecraftClient client, DecodedGif decoded) {
        this.width = decoded.width;
        this.height = decoded.height;
        this.delaysMs = new int[decoded.delaysMs.size()];

        for (int i = 0; i < decoded.frames.size(); i++) {
            BufferedImage buffered = decoded.frames.get(i);
            NativeImage nativeImage = bufferedToNative(buffered);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(nativeImage);

            Identifier id = client.getTextureManager().registerDynamicTexture(
                    dynamicPrefix + "_" + i,
                    texture
            );

            frames.add(new LoadedFrame(id));
            delaysMs[i] = decoded.delaysMs.get(i);
        }
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

    private record LoadedFrame(Identifier id) {}

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
}