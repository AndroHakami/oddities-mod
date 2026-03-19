package net.seep.odd.client.device.social;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.Oddities;
import net.seep.odd.client.device.DeviceHomeScreen;
import net.seep.odd.device.social.SocialNetworking;
import net.seep.odd.device.social.SocialPost;
import net.seep.odd.device.social.SocialReply;

@Environment(EnvType.CLIENT)
public final class DeviceSocialScreen extends Screen {
    private static final Identifier HOME_BG = new Identifier(Oddities.MOD_ID, "textures/gui/device/home_bg.png");
    private static final Identifier HOME_OVERLAY = new Identifier(Oddities.MOD_ID, "textures/gui/device/home_overlay.png");

    private static final Identifier ICON_HOME = new Identifier(Oddities.MOD_ID, "textures/gui/device/social/top/home.png");
    private static final Identifier ICON_CREATE = new Identifier(Oddities.MOD_ID, "textures/gui/device/social/top/create.png");
    private static final Identifier ICON_REFRESH = new Identifier(Oddities.MOD_ID, "textures/gui/device/social/top/refresh.png");

    private static final Identifier ICON_LIKE = new Identifier(Oddities.MOD_ID, "textures/gui/device/social/actions/like.png");
    private static final Identifier ICON_DISLIKE = new Identifier(Oddities.MOD_ID, "textures/gui/device/social/actions/dislike.png");
    private static final Identifier ICON_REPLY = new Identifier(Oddities.MOD_ID, "textures/gui/device/social/actions/reply.png");
    private static final Identifier ICON_DELETE = new Identifier(Oddities.MOD_ID, "textures/gui/device/social/actions/delete.png");

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd MMM HH:mm");

    private static final int TOP_ICON_SIZE = 22;
    private static final int ACTION_ICON_SIZE = 14;
    private static final int FACE_SIZE = 20;
    private static final int REPLY_FACE_SIZE = 14;

    private final List<ClickZone> zones = new ArrayList<>();
    private final List<ImageZone> imageZones = new ArrayList<>();
    private final Set<UUID> expandedReplies = new HashSet<>();

    private final Map<String, Float> hoverProgress = new HashMap<>();
    private final Set<String> hoveredLastFrame = new HashSet<>();
    private final Set<String> hoveredThisFrame = new HashSet<>();

    private double scroll = 0.0;
    private int maxScroll = 0;

    public DeviceSocialScreen() {
        super(Text.literal("Social"));
    }

    @Override
    protected void init() {
        requestSync();
    }

    private void requestSync() {
        ClientPlayNetworking.send(
                SocialNetworking.C2S_REQUEST_SYNC,
                net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create()
        );
    }

    private int getReplyBoxHeight(SocialReply reply, int width) {
        int replyTextW = width - 48;
        return 24 + wrappedHeight(reply.body, replyTextW) + 8;
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

        context.fill(feedX, feedY, feedX + feedW, feedY + feedH, 0x8C101622);

        List<SocialPost> posts = SocialClientCache.posts();

        int contentY = feedY + 8 - (int) scroll;
        int totalContentH = 0;

        context.enableScissor(feedX, feedY, feedX + feedW, feedY + feedH);

        if (posts.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No posts yet."), this.width / 2, feedY + 18, 0xFFC8D5F0);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Make the first one."), this.width / 2, feedY + 32, 0xFF9DB0D0);
        } else {
            for (SocialPost post : posts) {
                int cardH = measurePostHeight(post, feedW - 16);
                if (contentY + cardH >= feedY && contentY <= feedY + feedH) {
                    renderPost(context, post, feedX + 8, contentY, feedW - 16, mouseX, mouseY);
                }
                contentY += cardH + 8;
                totalContentH += cardH + 8;
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

        int createX = left + (DeviceHomeScreen.GUI_W / 2) - (TOP_ICON_SIZE / 2);
        int createY = top + 14;

        int refreshX = left + DeviceHomeScreen.GUI_W - 18 - TOP_ICON_SIZE;
        int refreshY = top + 14;

        renderIconButton(context, "top_home", ICON_HOME, homeX, homeY, TOP_ICON_SIZE, mouseX, mouseY);
        renderIconButton(context, "top_create", ICON_CREATE, createX, createY, TOP_ICON_SIZE, mouseX, mouseY);
        renderIconButton(context, "top_refresh", ICON_REFRESH, refreshX, refreshY, TOP_ICON_SIZE, mouseX, mouseY);

        zones.add(new ClickZone(homeX, homeY, homeX + TOP_ICON_SIZE, homeY + TOP_ICON_SIZE, () -> {
            if (this.client != null) this.client.setScreen(new DeviceHomeScreen());
        }));

        zones.add(new ClickZone(createX, createY, createX + TOP_ICON_SIZE, createY + TOP_ICON_SIZE, () -> {
            if (this.client != null) this.client.setScreen(new DeviceSocialComposeScreen(this));
        }));

        zones.add(new ClickZone(refreshX, refreshY, refreshX + TOP_ICON_SIZE, refreshY + TOP_ICON_SIZE, this::requestSync));
    }

    private int measurePostHeight(SocialPost post, int width) {
        int inner = width - 20;
        int h = 44;

        h += wrappedHeight(post.title, inner);
        h += 4;
        h += wrappedHeight(post.body, inner);

        if (post.hasMainImage()) {
            h += 8 + SocialUrlTextureCache.fittedHeight(post.mainImageUrl, inner, 110);
        }

        h += post.replies.isEmpty() ? 18 : 30;

        if (!post.replies.isEmpty() && expandedReplies.contains(post.id)) {
            h += 6;
            for (SocialReply reply : post.replies) {
                h += getReplyBoxHeight(reply, width) + 4;
            }
        }

        return h + 12;
    }

    private int wrappedHeight(String text, int width) {
        List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal(text), width);
        return Math.max(1, lines.size()) * 9;
    }

    private void renderPost(DrawContext context, SocialPost post, int x, int y, int width, int mouseX, int mouseY) {
        int cardBottom = y + measurePostHeight(post, width);

        context.fill(x, y, x + width, cardBottom, 0xD81A2230);
        context.fill(x, y, x + width, y + 1, 0x55E8F0FF);

        int cursorY = y + 10;

        renderFace(context, post.authorUuid, x + 10, cursorY, FACE_SIZE);
        context.drawTextWithShadow(this.textRenderer, Text.literal(post.authorName), x + 36, cursorY + 1, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal(formatTime(post.createdAt)), x + 36, cursorY + 11, 0xFF9FB0CC);

        cursorY += 30;

        cursorY = drawWrapped(context, post.title, x + 10, cursorY, width - 20, 0xFFF2F7FF);
        cursorY += 2;
        cursorY = drawWrapped(context, post.body, x + 10, cursorY, width - 20, 0xFFD3DDF2);

        if (post.hasMainImage()) {
            cursorY += 8;
            cursorY += renderRemoteImage(context, post.mainImageUrl, x + 10, cursorY, width - 20, 110);
        }

        cursorY += 8;

        UUID me = this.client != null && this.client.player != null ? this.client.player.getUuid() : null;
        byte myVote = me != null ? post.voteOf(me) : 0;
        boolean repliesExpanded = expandedReplies.contains(post.id);

        int actionY = cursorY;
        int actionX = x + 10;

        String likeKey = "like_" + post.id;
        renderIconButton(context, likeKey, ICON_LIKE, actionX, actionY, ACTION_ICON_SIZE, mouseX, mouseY);
        zones.add(new ClickZone(actionX, actionY, actionX + ACTION_ICON_SIZE, actionY + ACTION_ICON_SIZE, () -> {
            sendReact(post.id, myVote > 0 ? (byte) 0 : (byte) 1);
        }));
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal(String.valueOf(post.likes())),
                actionX + ACTION_ICON_SIZE + 2,
                actionY + 3,
                myVote > 0 ? 0xFFAEE1FF : 0xFFE6EDF9
        );
        actionX += ACTION_ICON_SIZE + 2 + this.textRenderer.getWidth(String.valueOf(post.likes())) + 10;

        String dislikeKey = "dislike_" + post.id;
        renderIconButton(context, dislikeKey, ICON_DISLIKE, actionX, actionY, ACTION_ICON_SIZE, mouseX, mouseY);
        zones.add(new ClickZone(actionX, actionY, actionX + ACTION_ICON_SIZE, actionY + ACTION_ICON_SIZE, () -> {
            sendReact(post.id, myVote < 0 ? (byte) 0 : (byte) -1);
        }));
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal(String.valueOf(post.dislikes())),
                actionX + ACTION_ICON_SIZE + 2,
                actionY + 3,
                myVote < 0 ? 0xFFFFB7C8 : 0xFFE6EDF9
        );
        actionX += ACTION_ICON_SIZE + 2 + this.textRenderer.getWidth(String.valueOf(post.dislikes())) + 10;

        String replyKey = "reply_" + post.id;
        renderIconButton(context, replyKey, ICON_REPLY, actionX, actionY, ACTION_ICON_SIZE, mouseX, mouseY);
        zones.add(new ClickZone(actionX, actionY, actionX + ACTION_ICON_SIZE, actionY + ACTION_ICON_SIZE, () -> {
            if (this.client != null) {
                this.client.setScreen(new DeviceSocialReplyScreen(this, post.id));
            }
        }));

        boolean canDelete = this.client != null && this.client.player != null
                && (post.authorUuid.equals(this.client.player.getUuid()) || this.client.player.hasPermissionLevel(2));

        if (canDelete) {
            int deleteX = x + width - 10 - ACTION_ICON_SIZE;
            String deleteKey = "delete_" + post.id;
            renderIconButton(context, deleteKey, ICON_DELETE, deleteX, actionY, ACTION_ICON_SIZE, mouseX, mouseY);
            zones.add(new ClickZone(deleteX, actionY, deleteX + ACTION_ICON_SIZE, actionY + ACTION_ICON_SIZE, () -> {
                var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                buf.writeUuid(post.id);
                ClientPlayNetworking.send(SocialNetworking.C2S_DELETE_POST, buf);
            }));
        }

        cursorY += 18;

        if (!post.replies.isEmpty()) {
            String toggleLabel = repliesExpanded ? "Hide replies ▲" : "Show replies ▼";
            int toggleX = x + 10;
            int toggleY = cursorY;

            context.drawTextWithShadow(this.textRenderer, Text.literal(toggleLabel), toggleX, toggleY, 0xFFBBD1F4);

            int labelW = this.textRenderer.getWidth(toggleLabel);
            zones.add(new ClickZone(toggleX, toggleY, toggleX + labelW, toggleY + 10, () -> toggleReplies(post.id)));

            cursorY += 12;
        }

        if (!post.replies.isEmpty() && repliesExpanded) {
            cursorY += 2;

            for (SocialReply reply : post.replies) {
                int replyTextX = x + 34;
                int replyTextW = width - 48;
                int replyBoxH = getReplyBoxHeight(reply, width);

                context.fill(x + 10, cursorY, x + width - 10, cursorY + replyBoxH, 0x50101825);

                renderFace(context, reply.authorUuid, x + 14, cursorY + 4, REPLY_FACE_SIZE);
                context.drawTextWithShadow(this.textRenderer, Text.literal(reply.authorName), x + 34, cursorY + 3, 0xFFF2F7FF);
                context.drawTextWithShadow(this.textRenderer, Text.literal(formatTime(reply.createdAt)), x + 34, cursorY + 12, 0xFF92A4C5);

                drawWrapped(context, reply.body, replyTextX, cursorY + 24, replyTextW, 0xFFD5E0F5);
                cursorY += replyBoxH + 4;
            }
        }
    }

    private int renderRemoteImage(DrawContext context, String url, int x, int y, int maxW, int maxH) {
        SocialUrlTextureCache.Fit fit = SocialUrlTextureCache.measureFit(url, maxW, maxH);
        int drawX = x + ((maxW - fit.drawW()) / 2);
        int drawY = y;

        int usedH = SocialUrlTextureCache.drawContained(context, url, x, y, maxW, maxH);

        if (fit.drawW() > 0 && fit.drawH() > 0) {
            imageZones.add(new ImageZone(drawX, drawY, drawX + fit.drawW(), drawY + fit.drawH(), url));
        }

        return usedH;
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
            int glowAlpha = (int) (hover * 72.0f) & 0xFF;
            context.fill(cx - (size / 2) - 2, cy - (size / 2) - 2, cx + (size / 2) + 2, cy + (size / 2) + 2, (glowAlpha << 24) | 0xD4E5FF);
        }

        context.getMatrices().push();
        context.getMatrices().translate(cx, cy, 0.0f);
        context.getMatrices().scale((size / 24.0f) * scale, (size / 24.0f) * scale, 1.0f);
        context.getMatrices().translate(-12.0f, -12.0f, 0.0f);
        context.drawTexture(texture, 0, 0, 0, 0, 24, 24, 24, 24);
        context.getMatrices().pop();
    }

    private int drawWrapped(DrawContext context, String text, int x, int y, int width, int color) {
        List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal(text), width);
        for (OrderedText line : lines) {
            context.drawTextWithShadow(this.textRenderer, line, x, y, color);
            y += 9;
        }
        return y;
    }

    private void renderFace(DrawContext context, UUID uuid, int x, int y, int size) {
        Identifier skin = DefaultSkinHelper.getTexture(uuid);

        if (MinecraftClient.getInstance().getNetworkHandler() != null) {
            PlayerListEntry entry = MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(uuid);
            if (entry != null) {
                skin = entry.getSkinTexture();
            }
        }

        context.drawTexture(skin, x, y, size, size, 8.0f, 8.0f, 8, 8, 64, 64);
        context.drawTexture(skin, x, y, size, size, 40.0f, 8.0f, 8, 8, 64, 64);
    }

    private void toggleReplies(UUID postId) {
        if (!expandedReplies.add(postId)) {
            expandedReplies.remove(postId);
        }
    }

    private void sendReact(UUID postId, byte vote) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeUuid(postId);
        buf.writeByte(vote);
        ClientPlayNetworking.send(SocialNetworking.C2S_REACT, buf);
    }

    private String formatTime(long millis) {
        return TIME_FMT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
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
                        this.client.setScreen(new DeviceSocialImageViewScreen(this, zone.url()));
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