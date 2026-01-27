package net.seep.odd.abilities.fairy.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.block.falseflower.FalseFlowerTracker;

import java.util.List;
import java.util.Objects;

public class ManageFlowersScreen extends Screen {

    private FlowerList list;
    private int tickCounter = 0;
    private int lastHash = 0;

    public ManageFlowersScreen() {
        super(Text.literal("Manage Flowers"));
    }

    @Override
    protected void init() {
        list = new FlowerList(MinecraftClient.getInstance(), width, height, 24, height - 8, 92);

        addSelectableChild(list);

        FalseFlowerTracker.requestSnapshot();
        rebuildIfChanged(true);
    }

    @Override
    public void tick() {
        tickCounter++;

        // keep fresh while open
        if (tickCounter % 10 == 0) FalseFlowerTracker.requestSnapshot();
        rebuildIfChanged(false);
    }

    private void rebuildIfChanged(boolean force) {
        List<FalseFlowerTracker.ClientEntry> snap = FalseFlowerTracker.clientSnapshot();

        int h = 1;
        for (var e : snap) {
            h = 31 * h + e.id();
            h = 31 * h + (e.active() ? 1 : 0);
            h = 31 * h + Float.floatToIntBits(e.mana());
            h = 31 * h + Float.floatToIntBits(e.power());
            h = 31 * h + Objects.hashCode(e.name());
            h = 31 * h + Objects.hashCode(e.spellKey());
            h = 31 * h + e.spellColorRgb();
        }

        if (!force && h == lastHash) return;
        lastHash = h;

        double scroll = list.odd_getScrollAmount();
        list.odd_clearEntries();
        for (var e : snap) list.odd_addEntry(new FlowerEntry(this, e));
        list.odd_setScrollAmount(scroll);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // ✅ NO dirt background. Transparent UI overlay.
        // (Do not call renderBackground)

        // subtle top bar only
        ctx.fill(0, 0, width, 22, 0x44000000);
        ctx.drawTextWithShadow(textRenderer, title, 10, 7, 0xE4E0FF);

        list.render(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /* ---------------- List ---------------- */

    private static final class FlowerList extends AlwaysSelectedEntryListWidget<FlowerEntry> {
        FlowerList(MinecraftClient client, int width, int height, int top, int bottom, int itemHeight) {
            super(client, width, height, top, bottom, itemHeight);

            // ✅ kill the EntryListWidget backdrop + shadows + header
            // (setRenderHeader is protected, so it must be called inside the subclass)
            this.setRenderBackground(false);
            this.setRenderHorizontalShadows(false);
            this.setRenderHeader(false, 0);
        }

        @Override
        public int getRowWidth() {
            return this.width - 22;
        }

        @Override
        protected int getScrollbarPositionX() {
            return this.width - 8;
        }

        // wrappers for protected methods
        public double odd_getScrollAmount() { return super.getScrollAmount(); }
        public void odd_setScrollAmount(double v) { super.setScrollAmount(v); }
        public void odd_clearEntries() { super.clearEntries(); }
        public void odd_addEntry(FlowerEntry e) { super.addEntry(e); }
    }

    /* ---------------- Entry ---------------- */

    private static final class FlowerEntry extends AlwaysSelectedEntryListWidget.Entry<FlowerEntry> {
        private final ManageFlowersScreen screen;
        private final FalseFlowerTracker.ClientEntry entry;

        private int lastX, lastY, lastW, lastH;

        private static final int PAD = 8;
        private static final int BTN_W = 118;
        private static final int BTN_H = 16;
        private static final int GAP = 2;

        FlowerEntry(ManageFlowersScreen screen, FalseFlowerTracker.ClientEntry e) {
            this.screen = screen;
            this.entry = e;
        }

        @Override
        public void render(DrawContext ctx, int index, int y, int x, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovered, float tickDelta) {

            lastX = x; lastY = y; lastW = rowWidth; lastH = rowHeight;

            int bg = hovered ? 0x2AFFFFFF : 0x1A000000;
            ctx.fill(x, y, x + rowWidth, y + rowHeight - 2, bg);

            // ✅ boxed 3D icon on the left
            ItemStack flower = new ItemStack(ModBlocks.FALSE_FLOWER_ITEM);

            int boxX = x + PAD;
            int boxY = y + 10;
            int boxS = 22;

            int border = hovered ? 0x66FFFFFF : 0x3AFFFFFF;
            int inner  = 0x22000000;

            ctx.fill(boxX - 1, boxY - 1, boxX + boxS + 1, boxY + boxS + 1, border);
            ctx.fill(boxX, boxY, boxX + boxS, boxY + boxS, inner);

            // item itself (16x16) centered inside the box
            ctx.drawItem(flower, boxX + 3, boxY + 3);

            String name = (entry.name() == null || entry.name().isBlank()) ? "False Flower" : entry.name();
            String type = pretty(entry.spellKey());

            Text nameLine = Text.literal(name + " ")
                    .append(Text.literal("[" + type + "]").styled(s -> s.withColor(0xBFA8FF)));

            int textX = boxX + boxS + 10;
            ctx.drawTextWithShadow(screen.textRenderer, nameLine, textX, y + 10, 0xFFFFFF);

            // mana bar (0..200)
            int barX = textX;
            int barY = y + 32;
            int barH = 8;

            int rightBlock = BTN_W + PAD + 6;
            int barW = Math.max(60, rowWidth - rightBlock - (barX - x));

            float frac = MathHelper.clamp(entry.mana() / 200f, 0f, 1f);
            int rgb = entry.spellColorRgb() & 0x00FFFFFF;

            ctx.fill(barX, barY, barX + barW, barY + barH, 0x55000000);
            ctx.fill(barX, barY, barX + (int)(barW * frac), barY + barH, 0xAA000000 | rgb);

            // right-side button stack
            int bx = x + rowWidth - BTN_W - PAD;
            int by = y + 8;

            drawBtn(ctx, bx, by, BTN_W, BTN_H, entry.active() ? "Deactivate" : "Activate", mouseX, mouseY);
            by += BTN_H + GAP;

            int half = (BTN_W - GAP) / 2;
            drawBtn(ctx, bx, by, half, BTN_H, "-", mouseX, mouseY);
            drawBtn(ctx, bx + half + GAP, by, half, BTN_H, "+", mouseX, mouseY);
            by += BTN_H + GAP;

            drawBtn(ctx, bx, by, BTN_W, BTN_H, "Cleanse", mouseX, mouseY);
            by += BTN_H + GAP;

            drawBtn(ctx, bx, by, BTN_W, BTN_H, "Rename", mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int bx = lastX + lastW - BTN_W - PAD;
            int by = lastY + 8;

            if (hit(mouseX, mouseY, bx, by, BTN_W, BTN_H)) {
                FalseFlowerTracker.sendToggle(entry.id(), !entry.active());
                return true;
            }
            by += BTN_H + GAP;

            int half = (BTN_W - GAP) / 2;
            if (hit(mouseX, mouseY, bx, by, half, BTN_H)) {
                FalseFlowerTracker.sendPower(entry.id(), entry.power() - 1f);
                return true;
            }
            if (hit(mouseX, mouseY, bx + half + GAP, by, half, BTN_H)) {
                FalseFlowerTracker.sendPower(entry.id(), entry.power() + 1f);
                return true;
            }
            by += BTN_H + GAP;

            if (hit(mouseX, mouseY, bx, by, BTN_W, BTN_H)) {
                FalseFlowerTracker.sendCleanse(entry.id());
                return true;
            }
            by += BTN_H + GAP;

            if (hit(mouseX, mouseY, bx, by, BTN_W, BTN_H)) {
                MinecraftClient.getInstance().setScreen(new RenameFlowerScreen(screen, entry.id(), entry.name()));
                return true;
            }

            return false;
        }

        private static boolean hit(double mx, double my, int x, int y, int w, int h) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        private static void drawBtn(DrawContext ctx, int x, int y, int w, int h, String label, int mx, int my) {
            boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
            int bg = hov ? 0x44FFFFFF : 0x2A000000;
            ctx.fill(x, y, x + w, y + h, bg);
            ctx.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(label), x + 7, y + 4, 0xFFFFFF);
        }

        private static String pretty(String key) {
            if (key == null || key.isBlank() || key.equals("none")) return "None";
            return key.substring(0, 1).toUpperCase() + key.substring(1).toLowerCase();
        }

        @Override
        public Text getNarration() {
            return Text.empty();
        }
    }
}
