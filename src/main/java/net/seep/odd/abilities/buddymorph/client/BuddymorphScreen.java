package net.seep.odd.abilities.buddymorph.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.buddymorph.BuddymorphNet;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class BuddymorphScreen extends Screen {

    /* ---------- model ---------- */
    private static final class Entry {
        final boolean self;
        final Identifier typeId; // null if self
        final String label;
        Entry(boolean self, Identifier id, String label) { this.self = self; this.typeId = id; this.label = label; }
        static Entry self() { return new Entry(true, null, "You"); }
        static Entry of(Identifier id) {
            String name = id.getPath().replace('_', ' ');
            return new Entry(false, id, name);
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    /* ---------- layout ---------- */
    private static final int LIST_W = 260;
    private static final int ROW_H  = 24;
    private static final int VISIBLE_ROWS = 10;

    private int listLeft, listTop, listRight, listBottom;
    private int scroll = 0; // first visible row index

    public BuddymorphScreen(List<String> ids) {
        super(Text.literal("Buddymorph"));
        setEntriesFromIds(ids);
    }

    /** Live update from S2C_UPDATE while the screen is open. */
    public void updateIds(List<String> ids) {
        setEntriesFromIds(ids);
        clampScroll();
    }

    private void setEntriesFromIds(List<String> ids) {
        entries.clear();
        entries.add(Entry.self());
        // only add living entity types to keep the picker clean
        for (String s : ids) {
            try {
                Identifier id = new Identifier(s);
                var t = Registries.ENTITY_TYPE.get(id);
                if (t != null && LivingEntity.class.isAssignableFrom(t.getBaseClass())) {
                    entries.add(Entry.of(id));
                }
            } catch (Throwable ignored) {}
        }
    }

    private void clampScroll() {
        int max = Math.max(0, entries.size() - VISIBLE_ROWS);
        if (scroll < 0) scroll = 0;
        if (scroll > max) scroll = max;
    }

    /* ---------- Screen ---------- */

    @Override
    protected void init() {
        int panelH = VISIBLE_ROWS * ROW_H + 2;
        listLeft   = (width - LIST_W) / 2;
        listRight  = listLeft + LIST_W;
        listTop    = (height - panelH) / 2;
        listBottom = listTop + panelH;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);

        // title
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, listTop - 18, 0xFFFFFFFF);

        // panel bg + border
        ctx.fill(listLeft, listTop, listRight, listBottom, 0x88000000);
        ctx.drawBorder(listLeft, listTop, LIST_W, VISIBLE_ROWS * ROW_H + 2, 0xFFFFFFFF);

        // rows
        int hovered = -1;
        int start = scroll;
        int end   = Math.min(entries.size(), scroll + VISIBLE_ROWS);

        for (int i = start, row = 0; i < end; i++, row++) {
            int y0 = listTop + 1 + row * ROW_H;
            int y1 = y0 + ROW_H - 1;

            // row bg
            ctx.fill(listLeft + 1, y0, listRight - 1, y1, 0x22000000);

            // hover detect
            boolean hot = mouseX >= listLeft && mouseX <= listRight && mouseY >= y0 && mouseY <= y1;
            if (hot) { hovered = i; ctx.fill(listLeft + 1, y0, listRight - 1, y1, 0x22FFFFFF); }

            // label
            Entry e = entries.get(i);
            int color = e.self ? 0xFFEAA73C : 0xFFE0E0E0;
            String label = e.label;
            ctx.drawTextWithShadow(textRenderer, label, listLeft + 10, y0 + 6, color);
            if (!e.self) {
                // dimmed id on the right for debugging clarity
                String idTxt = e.typeId.toString();
                int tw = textRenderer.getWidth(idTxt);
                ctx.drawText(textRenderer, idTxt, listRight - 10 - tw, y0 + 7, 0x66FFFFFF, false);
            }
        }

        // scrollbar hint
        if (entries.size() > VISIBLE_ROWS) {
            String hint = String.format("%d / %d", Math.min(end - 1, entries.size() - 1), entries.size() - 1);
            int tw = textRenderer.getWidth(hint);
            ctx.drawText(textRenderer, hint, listRight - tw, listBottom + 4, 0x66FFFFFF, false);
        }

        // tiny debug, bottom-left
        ctx.drawText(textRenderer, "Debug size: " + Math.max(0, entries.size() - 1), 6, height - 12, 0x88FFFFFF, false);

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        int start = scroll;
        int end   = Math.min(entries.size(), scroll + VISIBLE_ROWS);

        for (int i = start, row = 0; i < end; i++, row++) {
            int y0 = listTop + 1 + row * ROW_H;
            int y1 = y0 + ROW_H - 1;
            if (mx >= listLeft && mx <= listRight && my >= y0 && my <= y1) {
                Entry e = entries.get(i);

                // send choice to server
                if (e.self) {
                    var out = PacketByteBufs.create();
                    out.writeBoolean(true);
                    ClientPlayNetworking.send(BuddymorphNet.C2S_PICK, out);
                } else {
                    var out = PacketByteBufs.create();
                    out.writeBoolean(false);
                    out.writeString(e.typeId.toString());
                    ClientPlayNetworking.send(BuddymorphNet.C2S_PICK, out);
                }

                // close now
                MinecraftClient.getInstance().setScreen(null);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (entries.size() > VISIBLE_ROWS &&
                mx >= listLeft && mx <= listRight && my >= listTop && my <= listBottom) {
            scroll -= (int) Math.signum(amount); // natural feel
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mx, my, amount);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public boolean shouldCloseOnEsc() { return true; }
}
