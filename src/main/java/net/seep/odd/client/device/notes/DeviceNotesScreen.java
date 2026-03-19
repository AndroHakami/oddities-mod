package net.seep.odd.client.device.notes;

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
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.Oddities;
import net.seep.odd.client.device.DeviceHomeScreen;
import net.seep.odd.device.notes.NoteEntry;
import net.seep.odd.device.notes.NotesNetworking;

@Environment(EnvType.CLIENT)
public final class DeviceNotesScreen extends Screen {
    private static final Identifier HOME_BG = new Identifier(Oddities.MOD_ID, "textures/gui/device/home_bg.png");
    private static final Identifier HOME_OVERLAY = new Identifier(Oddities.MOD_ID, "textures/gui/device/home_overlay.png");

    private static final Identifier ICON_HOME = new Identifier(Oddities.MOD_ID, "textures/gui/device/social/top/home.png");
    private static final Identifier ICON_CREATE_NOTE = new Identifier(Oddities.MOD_ID, "textures/gui/device/notes/top/create_note.png");

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private static final int TOP_ICON_SIZE = 22;

    private static final int GRID_COLS = 3;
    private static final int CARD_W = 64;
    private static final int CARD_H = 68;
    private static final int CARD_GAP = 6;

    private static final int PREVIEW_SOURCE_LIMIT = 140;
    private static final int PREVIEW_MAX_LINES = 4;

    private final List<ClickZone> noteZones = new ArrayList<>();
    private final List<ClickZone> deleteZones = new ArrayList<>();
    private final List<ClickZone> topZones = new ArrayList<>();

    private final Map<String, Float> hoverProgress = new HashMap<>();
    private final Set<String> hoveredLastFrame = new HashSet<>();
    private final Set<String> hoveredThisFrame = new HashSet<>();
    private final Map<UUID, PreviewCacheEntry> previewCache = new HashMap<>();

    private double scroll = 0.0;
    private int maxScroll = 0;

    public DeviceNotesScreen() {
        super(Text.literal("Notes"));
    }

    @Override
    protected void init() {
        requestSync();
    }

    private void requestSync() {
        ClientPlayNetworking.send(
                NotesNetworking.C2S_REQUEST_SYNC,
                net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create()
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        int left = (this.width - DeviceHomeScreen.GUI_W) / 2;
        int top = (this.height - DeviceHomeScreen.GUI_H) / 2;

        hoveredThisFrame.clear();
        noteZones.clear();
        deleteZones.clear();
        topZones.clear();

        context.fill(left - 5, top - 5, left + DeviceHomeScreen.GUI_W + 5, top + DeviceHomeScreen.GUI_H + 5, 0x66000000);
        context.drawTexture(HOME_BG, left, top, 0, 0, DeviceHomeScreen.GUI_W, DeviceHomeScreen.GUI_H, DeviceHomeScreen.GUI_W, DeviceHomeScreen.GUI_H);

        renderTopButtons(context, left, top, mouseX, mouseY);

        int feedX = left + 14;
        int feedY = top + 42;
        int feedW = DeviceHomeScreen.GUI_W - 28;
        int feedH = DeviceHomeScreen.GUI_H - 56;

        context.fill(feedX, feedY, feedX + feedW, feedY + feedH, 0x8C101622);

        List<NoteEntry> notes = NotesClientCache.notes();
        previewCache.keySet().removeIf(id -> notes.stream().noneMatch(n -> n.id.equals(id)));

        int contentStartX = feedX + 4;
        int contentStartY = feedY + 8 - (int) scroll;

        int rows = (int) Math.ceil(notes.size() / (double) GRID_COLS);
        int totalContentH = rows <= 0 ? 0 : rows * (CARD_H + CARD_GAP);

        context.enableScissor(feedX, feedY, feedX + feedW, feedY + feedH);

        if (notes.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No notes yet."), this.width / 2, feedY + 24, 0xFFC8D5F0);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Create one."), this.width / 2, feedY + 38, 0xFF9DB0D0);
        } else {
            for (int i = 0; i < notes.size(); i++) {
                NoteEntry note = notes.get(i);

                int col = i % GRID_COLS;
                int row = i / GRID_COLS;

                int cardX = contentStartX + col * (CARD_W + CARD_GAP);
                int cardY = contentStartY + row * (CARD_H + CARD_GAP);

                if (cardY + CARD_H >= feedY && cardY <= feedY + feedH) {
                    renderNoteCard(context, note, cardX, cardY, CARD_W, CARD_H, mouseX, mouseY);
                }
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

        int createX = left + DeviceHomeScreen.GUI_W - 18 - TOP_ICON_SIZE;
        int createY = top + 14;

        renderIconButton(context, "notes_home", ICON_HOME, homeX, homeY, TOP_ICON_SIZE, mouseX, mouseY);
        renderIconButton(context, "notes_create", ICON_CREATE_NOTE, createX, createY, TOP_ICON_SIZE, mouseX, mouseY);

        topZones.add(new ClickZone(homeX, homeY, homeX + TOP_ICON_SIZE, homeY + TOP_ICON_SIZE, () -> {
            if (this.client != null) this.client.setScreen(new DeviceHomeScreen());
        }));

        topZones.add(new ClickZone(createX, createY, createX + TOP_ICON_SIZE, createY + TOP_ICON_SIZE, () -> {
            if (this.client != null) this.client.setScreen(new DeviceNoteEditorScreen(this, null, ""));
        }));
    }

    private void renderNoteCard(DrawContext context, NoteEntry note, int x, int y, int width, int height, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;

        context.fill(x, y, x + width, y + height, hovered ? 0xE0222D40 : 0xD81A2230);
        context.fill(x, y, x + width, y + 1, 0x55E8F0FF);

        String date = DATE_FMT.format(Instant.ofEpochMilli(note.updatedAt).atZone(ZoneId.systemDefault()));
        String time = TIME_FMT.format(Instant.ofEpochMilli(note.updatedAt).atZone(ZoneId.systemDefault()));

        context.drawTextWithShadow(this.textRenderer, Text.literal(date), x + 5, y + 5, 0xFFBFD0EC);
        context.drawTextWithShadow(this.textRenderer, Text.literal(time), x + 5, y + 14, 0xFF93A7C8);

        drawPreview(context, note, x + 5, y + 26, width - 10, PREVIEW_MAX_LINES, 0xFFE4ECFF);

        noteZones.add(new ClickZone(x, y, x + width, y + height, () -> {
            if (this.client != null) {
                this.client.setScreen(new DeviceNoteEditorScreen(this, note.id, note.content));
            }
        }));

        if (hovered) {
            int dx1 = x + width - 14;
            int dy1 = y + 4;
            int dx2 = x + width - 4;
            int dy2 = y + 14;

            context.fill(dx1, dy1, dx2, dy2, 0x90A02020);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("×"), dx1 + 5, dy1 + 1, 0xFFFFD0D0);

            deleteZones.add(new ClickZone(dx1, dy1, dx2, dy2, () -> deleteNote(note.id)));
        }
    }

    private void drawPreview(DrawContext context, NoteEntry note, int x, int y, int width, int maxLines, int color) {
        PreviewCacheEntry cached = previewCache.get(note.id);

        if (cached == null || cached.updatedAt() != note.updatedAt) {
            String raw = note.content == null || note.content.isBlank() ? "(Empty)" : note.content;
            raw = raw.replace('\n', ' ').replace('\r', ' ');

            boolean truncated = raw.length() > PREVIEW_SOURCE_LIMIT;
            String source = truncated ? raw.substring(0, PREVIEW_SOURCE_LIMIT) : raw;

            List<OrderedText> wrapped = this.textRenderer.wrapLines(Text.literal(source), width);

            if (wrapped.size() > maxLines) {
                wrapped = new ArrayList<>(wrapped.subList(0, maxLines));
                truncated = true;
            } else {
                wrapped = new ArrayList<>(wrapped);
            }

            cached = new PreviewCacheEntry(note.updatedAt, wrapped, truncated);
            previewCache.put(note.id, cached);
        }

        List<OrderedText> lines = cached.lines();
        for (int i = 0; i < lines.size(); i++) {
            context.drawTextWithShadow(this.textRenderer, lines.get(i), x, y, color);
            y += 9;
        }

        if (cached.truncated() && !lines.isEmpty()) {
            int ellipsisY = y - 9;
            context.drawTextWithShadow(this.textRenderer, Text.literal("..."), x, ellipsisY, 0xFF9FB0CC);
        }
    }

    private void deleteNote(UUID noteId) {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeUuid(noteId);
        ClientPlayNetworking.send(NotesNetworking.C2S_DELETE_NOTE, buf);
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
            for (ClickZone zone : deleteZones) {
                if (zone.contains(mouseX, mouseY)) {
                    playClickSound();
                    zone.action.run();
                    return true;
                }
            }

            for (ClickZone zone : topZones) {
                if (zone.contains(mouseX, mouseY)) {
                    playClickSound();
                    zone.action.run();
                    return true;
                }
            }

            for (ClickZone zone : noteZones) {
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

    private record PreviewCacheEntry(long updatedAt, List<OrderedText> lines, boolean truncated) {}
}