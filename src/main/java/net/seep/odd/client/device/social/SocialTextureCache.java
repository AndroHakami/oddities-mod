package net.seep.odd.client.device.social;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public final class SocialTextureCache {
    private SocialTextureCache() {}

    public record LoadedImage(Identifier id, int width, int height) {}

    private static final Map<String, LoadedImage> CACHE = new HashMap<>();

    public static LoadedImage get(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;

        String key = Integer.toHexString(java.util.Arrays.hashCode(bytes)) + "_" + bytes.length;
        LoadedImage cached = CACHE.get(key);
        if (cached != null) return cached;

        try {
            NativeImage img = NativeImage.read(new ByteArrayInputStream(bytes));
            NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
            Identifier id = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("odd_social_" + key, tex);
            LoadedImage out = new LoadedImage(id, img.getWidth(), img.getHeight());
            CACHE.put(key, out);
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    public static int drawFit(DrawContext context, byte[] bytes, int x, int y, int maxW, int maxH) {
        LoadedImage img = get(bytes);
        if (img == null) return 0;

        float scale = Math.min((float) maxW / (float) img.width(), (float) maxH / (float) img.height());
        scale = Math.min(scale, 1.0f);

        int w = Math.max(1, Math.round(img.width() * scale));
        int h = Math.max(1, Math.round(img.height() * scale));

        context.drawTexture(img.id(), x, y, 0, 0, w, h, img.width(), img.height());
        return h;
    }

    public static int fittedHeight(byte[] bytes, int maxW, int maxH) {
        LoadedImage img = get(bytes);
        if (img == null) return 0;

        float scale = Math.min((float) maxW / (float) img.width(), (float) maxH / (float) img.height());
        scale = Math.min(scale, 1.0f);

        return Math.max(1, Math.round(img.height() * scale));
    }
}