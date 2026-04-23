package net.seep.odd.client.device.info;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.Oddities;
import net.seep.odd.client.device.DeviceHomeScreen;
import net.seep.odd.client.device.social.SocialUrlTextureCache;
import net.seep.odd.device.info.InfoNetworking;
import net.seep.odd.device.info.InfoPost;

@Environment(EnvType.CLIENT)
public final class DeviceInfoScreen extends Screen {
    private static final Identifier HOME_BG = new Identifier(Oddities.MOD_ID, "textures/gui/device/home_bg.png");
    private static final Identifier HOME_OVERLAY = new Identifier(Oddities.MOD_ID, "textures/gui/device/home_overlay.png");

    private static final Identifier ICON_HOME = new Identifier(Oddities.MOD_ID, "textures/gui/device/social/top/home.png");
    private static final Identifier ICON_CREATE = new Identifier(Oddities.MOD_ID, "textures/gui/device/social/top/create.png");
    private static final Identifier ICON_REFRESH = new Identifier(Oddities.MOD_ID, "textures/gui/device/social/top/refresh.png");
    private static final Identifier ICON_DELETE = new Identifier(Oddities.MOD_ID, "textures/gui/device/social/actions/delete.png");

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy  HH:mm");
    private static final float TITLE_SCALE = 1.35f;

    private static final int TOP_ICON_SIZE = 22;
    private static final int ACTION_ICON_SIZE = 14;

    private final List<ClickZone> zones = new ArrayList<>();
    private final List<ImageZone> imageZones = new ArrayList<>();

    private final Map<String, Float> hoverProgress = new HashMap<>();
    private final Set<String> hoveredLastFrame = new HashSet<>();
    private final Set<String> hoveredThisFrame = new HashSet<>();

    private double scroll = 0.0;
    private int maxScroll = 0;

    public DeviceInfoScreen() {
        super(Text.literal("Info"));
    }

    @Override
    protected void init() {
        requestSync();
    }

    private void requestSync() {
        ClientPlayNetworking.send(
                InfoNetworking.C2S_REQUEST_SYNC,
                net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create()
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        int left = (this.width - DeviceHomeScreen.GUI_W) / 2;
        int top = (this.height - DeviceHomeScreen.GUI_H) / 2;

        hoveredThisFrame.clear();
        zones.clear();
        imageZones.clear();

        context.fill(left - 5, top - 5, left + DeviceHomeScreen.GUI_W + 5, top + DeviceHomeScreen.GUI_H + 5, 0x66000000);
        context.drawTexture(HOME_BG, left, top, 0, 0, DeviceHomeScreen.GUI_W, DeviceHomeScreen.GUI_H, DeviceHomeScreen.GUI_W, DeviceHomeScreen.GUI_H);

        renderTopButtons(context, left, top, mouseX, mouseY);

        int feedX = left + 14;
        int feedY = top + 40;
        int feedW = DeviceHomeScreen.GUI_W - 28;
        int feedH = DeviceHomeScreen.GUI_H - 54;

        context.fill(feedX, feedY, feedX + feedW, feedY + feedH, 0x8C171310);

        List<InfoPost> posts = InfoClientCache.posts();

        int contentY = feedY + 8 - (int) scroll;
        int totalContentH = 0;

        context.enableScissor(feedX, feedY, feedX + feedW, feedY + feedH);

        if (posts.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No info posts yet."), this.width / 2, feedY + 18, 0xFFF7EAD2);
            if (InfoClientCache.canPost()) {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Make the first article."), this.width / 2, feedY + 32, 0xFFD5C6AD);
            }
        } else {
            for (InfoPost post : posts) {
                int cardH = measurePostHeight(post, feedW - 16);
                if (contentY + cardH >= feedY && contentY <= feedY + feedH) {
                    renderPost(context, post, feedX + 8, contentY, feedW - 16, mouseX, mouseY);
                }
                contentY += cardH + 10;
                totalContentH += cardH + 10;
            }
        }

        context.disableScissor();

        maxScroll = Math.max(0, totalContentH - feedH + 8);
        scroll = MathHelper.clamp(scroll, 0.0, maxScroll);

        context.drawTexture(HOME_OVERLAY, left, top, 0, 0, DeviceHomeScreen.GUI_W, DeviceHomeScreen.GUI_H, DeviceHomeScreen.GUI_W, DeviceHomeScreen.GUI_H);

        hoveredLastFrame.clear();
        hoveredLastFrame.addAll(hoveredThisFrame);

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderTopButtons(DrawContext context, int left, int top, int mouseX, int mouseY) {
        int homeX = left + 18;
        int homeY = top + 14;

        renderIconButton(context, "info_home", ICON_HOME, homeX, homeY, TOP_ICON_SIZE, mouseX, mouseY);
        zones.add(new ClickZone(homeX, homeY, homeX + TOP_ICON_SIZE, homeY + TOP_ICON_SIZE, () -> {
            if (this.client != null) this.client.setScreen(new DeviceHomeScreen());
        }));

        if (InfoClientCache.canPost()) {
            int createX = left + (DeviceHomeScreen.GUI_W / 2) - (TOP_ICON_SIZE / 2);
            int createY = top + 14;

            renderIconButton(context, "info_create", ICON_CREATE, createX, createY, TOP_ICON_SIZE, mouseX, mouseY);
            zones.add(new ClickZone(createX, createY, createX + TOP_ICON_SIZE, createY + TOP_ICON_SIZE, () -> {
                if (this.client != null) this.client.setScreen(new DeviceInfoComposeScreen(this));
            }));
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Info"), left + (DeviceHomeScreen.GUI_W / 2), top + 20, 0xFFF5E8CF);
        }

        int refreshX = left + DeviceHomeScreen.GUI_W - 18 - TOP_ICON_SIZE;
        int refreshY = top + 14;

        renderIconButton(context, "info_refresh", ICON_REFRESH, refreshX, refreshY, TOP_ICON_SIZE, mouseX, mouseY);
        zones.add(new ClickZone(refreshX, refreshY, refreshX + TOP_ICON_SIZE, refreshY + TOP_ICON_SIZE, this::requestSync));
    }

    private int measurePostHeight(InfoPost post, int width) {
        int inner = width - 20;
        int h = 18;
        h += wrappedHeightScaled(Text.literal(post.title).formatted(Formatting.BOLD), inner, TITLE_SCALE);
        h += 6;
        h += wrappedHeight(Text.literal(byline(post)), inner);
        h += 8;
        h += wrappedHeight(Text.literal(post.body), inner);

        if (post.hasImages()) {
            int imageRows = (int) Math.ceil(post.imageUrls.size() / 2.0);
            h += 10 + (imageRows * 68);
        }

        return h + 14;
    }

    private void renderPost(DrawContext context, InfoPost post, int x, int y, int width, int mouseX, int mouseY) {
        int cardBottom = y + measurePostHeight(post, width);

        context.fill(x, y, x + width, cardBottom, 0xE5F0E1CB);
        context.fill(x + 1, y + 1, x + width - 1, cardBottom - 1, 0xFCE9D8BF);
        context.fill(x, y, x + width, y + 1, 0xAA8E7254);

        int cursorY = y + 10;
        cursorY = drawWrappedScaled(context, Text.literal(post.title).formatted(Formatting.BOLD), x + 10, cursorY, width - 20, TITLE_SCALE, 0xFF2A2018);
        cursorY += 2;

        cursorY = drawWrapped(context, Text.literal(byline(post)), x + 10, cursorY, width - 20, 0xFF6A5240);
        cursorY += 6;

        cursorY = drawWrapped(context, Text.literal(post.body), x + 10, cursorY, width - 20, 0xFF362A20);

        if (post.hasImages()) {
            cursorY += 10;
            int innerW = width - 20;
            int columnGap = 6;
            int imageW = (innerW - columnGap) / 2;
            int imageH = 62;

            for (int i = 0; i < post.imageUrls.size(); i++) {
                int col = i % 2;
                int row = i / 2;
                int drawX = x + 10 + col * (imageW + columnGap);
                int drawY = cursorY + row * (imageH + 6);

                context.fill(drawX - 1, drawY - 1, drawX + imageW + 1, drawY + imageH + 1, 0xAA8E7254);
                SocialUrlTextureCache.drawContained(context, post.imageUrls.get(i), drawX, drawY, imageW, imageH);

                SocialUrlTextureCache.Fit fit = SocialUrlTextureCache.measureFit(post.imageUrls.get(i), imageW, imageH);
                if (fit.drawW() > 0 && fit.drawH() > 0) {
                    int zoneX = drawX + ((imageW - fit.drawW()) / 2);
                    imageZones.add(new ImageZone(zoneX, drawY, zoneX + fit.drawW(), drawY + fit.drawH(), post.imageUrls.get(i)));
                }
            }
        }

        boolean canDelete = this.client != null && this.client.player != null && this.client.player.hasPermissionLevel(2);
        if (canDelete) {
            int deleteX = x + width - 10 - ACTION_ICON_SIZE;
            int deleteY = y + 8;
            renderIconButton(context, "info_delete_" + post.id, ICON_DELETE, deleteX, deleteY, ACTION_ICON_SIZE, mouseX, mouseY);
            zones.add(new ClickZone(deleteX, deleteY, deleteX + ACTION_ICON_SIZE, deleteY + ACTION_ICON_SIZE, () -> {
                var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                buf.writeUuid(post.id);
                ClientPlayNetworking.send(InfoNetworking.C2S_DELETE_POST, buf);
            }));
        }
    }

    private String byline(InfoPost post) {
        String time = TIME_FMT.format(Instant.ofEpochMilli(post.createdAt).atZone(ZoneId.systemDefault()));
        if (post.source == null || post.source.isBlank()) {
            return time;
        }
        return post.source + "  •  " + time;
    }

    private int wrappedHeight(Text text, int width) {
        List<OrderedText> lines = this.textRenderer.wrapLines(text, width);
        return Math.max(1, lines.size()) * 9;
    }

    private int wrappedHeightScaled(Text text, int width, float scale) {
        int scaledWidth = Math.max(1, Math.round(width / scale));
        List<OrderedText> lines = this.textRenderer.wrapLines(text, scaledWidth);
        int lineHeight = Math.max(9, Math.round(9.0f * scale));
        return Math.max(1, lines.size()) * lineHeight;
    }

    private int drawWrapped(DrawContext context, Text text, int x, int y, int width, int color) {
        List<OrderedText> lines = this.textRenderer.wrapLines(text, width);
        for (OrderedText line : lines) {
            context.drawText(this.textRenderer, line, x, y, color, false);
            y += 9;
        }
        return y;
    }

    private int drawWrappedScaled(DrawContext context, Text text, int x, int y, int width, float scale, int color) {
        int scaledWidth = Math.max(1, Math.round(width / scale));
        List<OrderedText> lines = this.textRenderer.wrapLines(text, scaledWidth);
        int scaledLineHeight = Math.max(9, Math.round(9.0f * scale));

        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        int drawY = 0;
        for (OrderedText line : lines) {
            context.drawText(this.textRenderer, line, 0, drawY, color, false);
            drawY += 9;
        }
        context.getMatrices().pop();

        return y + (Math.max(1, lines.size()) * scaledLineHeight);
    }

    private void renderIconButton(DrawContext context, String key, Identifier texture, int x, int y, int size, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size;

        if (hovered) {
            hoveredThisFrame.add(key);
            if (!hoveredLastFrame.contains(key)) {
                playHoverSound();
            }
        }

        float current = hoverProgress.getOrDefault(key, 0.0f);
        float target = hovered ? 1.0f : 0.0f;
        float hover = MathHelper.lerp(0.24f, current, target);
        hoverProgress.put(key, hover);

        float time = (System.currentTimeMillis() / 90.0f) + (Math.abs(key.hashCode()) % 997);
        float idleBob = (float) Math.sin(time * 0.12f) * 0.020f;
        float pulse = (float) Math.sin(time * 0.07f) * 0.012f;
        float scale = 1.0f + idleBob + pulse + (hover * 0.18f);

        int cx = x + (size / 2);
        int cy = y + (size / 2);

        if (hover > 0.01f) {
            int glowAlpha = (int) (hover * 64.0f) & 0xFF;
            context.fill(cx - (size / 2) - 2, cy - (size / 2) - 2, cx + (size / 2) + 2, cy + (size / 2) + 2, (glowAlpha << 24) | 0xFFF4E4C5);
        }

        context.getMatrices().push();
        context.getMatrices().translate(cx, cy, 0.0f);
        context.getMatrices().scale((size / 24.0f) * scale, (size / 24.0f) * scale, 1.0f);
        context.getMatrices().translate(-12.0f, -12.0f, 0.0f);
        context.drawTexture(texture, 0, 0, 0, 0, 24, 24, 24, 24);
        context.getMatrices().pop();
    }

    private void playHoverSound() {
        if (this.client == null || this.client.player == null) return;
        this.client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.24f, 1.65f);
    }

    private void playClickSound() {
        if (this.client == null || this.client.player == null) return;
        this.client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.40f, 1.00f);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (ImageZone zone : imageZones) {
                if (zone.contains(mouseX, mouseY)) {
                    playClickSound();
                    if (this.client != null) {
                        this.client.setScreen(new DeviceInfoImageViewScreen(this, zone.url()));
                    }
                    return true;
                }
            }

            for (ClickZone zone : zones) {
                if (zone.contains(mouseX, mouseY)) {
                    playClickSound();
                    zone.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scroll = MathHelper.clamp(scroll - amount * 18.0, 0.0, maxScroll);
        return true;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(new DeviceHomeScreen());
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private record ClickZone(int x1, int y1, int x2, int y2, Runnable action) {
        boolean contains(double x, double y) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2;
        }
    }

    private record ImageZone(int x1, int y1, int x2, int y2, String url) {
        boolean contains(double x, double y) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2;
        }
    }
}
