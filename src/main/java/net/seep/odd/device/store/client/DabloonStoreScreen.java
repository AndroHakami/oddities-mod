package net.seep.odd.device.store.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.Oddities;
import net.seep.odd.device.store.DabloonStoreEntry;
import net.seep.odd.device.store.DabloonStoreNetworking;
import net.seep.odd.device.store.screen.DabloonStoreScreenHandler;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class DabloonStoreScreen extends HandledScreen<DabloonStoreScreenHandler> {
    private enum Mode { SHOP, EDIT }

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my < y + h && my >= y;
        }

        int centerX() {
            return x + (w / 2);
        }

        int centerY() {
            return y + (h / 2);
        }
    }

    private static final Identifier DABLOON_ICON =
            new Identifier(Oddities.MOD_ID, "textures/item/dabloon.png");
    private static final int DABLOON_TEX_SIZE = 32;

    private static final int HEADER_H = 42;
    private static final int FOOTER_H = 22;

    private static final int BG = 0xF010141B;
    private static final int PANEL = 0xD9141B25;
    private static final int PANEL_DARK = 0xE0101822;
    private static final int BORDER = 0xFF000000;
    private static final int LINE = 0x3F5E85B4;
    private static final int GOLD = 0xFFF7E6A4;
    private static final int TEXT = 0xFFF3F5FF;
    private static final int SUBTEXT = 0xFFB7C6DA;
    private static final int ACCENT = 0xFF8ED3FF;
    private static final int SLOT_FILL = 0xD61A2330;
    private static final int SLOT_FILL_HOVER = 0xEA223140;
    private static final int DELETE_FILL = 0xD24A2020;
    private static final int DELETE_FILL_HOVER = 0xEA6A2A2A;

    private Mode mode = Mode.SHOP;

    private boolean inventoryPickerOpen = false;
    private boolean pickerForHologram = false;
    private boolean draggingHue = false;

    private int selectedListing = -1;
    private int syncCooldown = 0;

    private DabloonStoreClientState.BlockStateView data;

    private TextFieldWidget storeTitleField;
    private TextFieldWidget listingTitleField;
    private TextFieldWidget listingDescField;
    private TextFieldWidget listingPriceField;

    private ButtonWidget editDoneButton;
    private ButtonWidget discoveryButton;
    private ButtonWidget saveSettingsButton;
    private ButtonWidget clearHologramButton;
    private ButtonWidget saveListingButton;
    private final List<ButtonWidget> buyButtons = new ArrayList<>();

    private boolean settingsDirty = false;
    private boolean listingDirty = false;
    private boolean localDiscoveryEnabled = false;
    private float hue01 = 0.53f;

    public DabloonStoreScreen(DabloonStoreScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 560;
        this.backgroundHeight = 360;
    }

    @Override
    protected void init() {
        super.init();
        this.titleX = 0;
        this.titleY = 0;

        createWidgets();
        layoutWidgets();

        requestSync();
        applySnapshotToControls(true);
        refreshWidgetState();
    }

    private void createWidgets() {
        storeTitleField = new TextFieldWidget(this.textRenderer, 0, 0, 152, 20, Text.literal("Store title"));
        storeTitleField.setMaxLength(64);
        this.addDrawableChild(storeTitleField);

        listingTitleField = new TextFieldWidget(this.textRenderer, 0, 0, 286, 20, Text.literal("Listing title"));
        listingTitleField.setMaxLength(DabloonStoreEntry.TITLE_MAX_LEN);
        this.addDrawableChild(listingTitleField);

        listingDescField = new TextFieldWidget(this.textRenderer, 0, 0, 286, 20, Text.literal("Description"));
        listingDescField.setMaxLength(DabloonStoreEntry.DESC_MAX_LEN);
        this.addDrawableChild(listingDescField);

        listingPriceField = new TextFieldWidget(this.textRenderer, 0, 0, 70, 20, Text.literal("Price"));
        listingPriceField.setMaxLength(6);
        this.addDrawableChild(listingPriceField);

        editDoneButton = ButtonWidget.builder(Text.literal("EDIT"), b -> {
            if (mode == Mode.EDIT) {
                flushPendingEdits();
                mode = Mode.SHOP;
            } else {
                mode = Mode.EDIT;
                if (selectedListing < 0 && data != null && !data.snapshot.entries.isEmpty()) {
                    selectedListing = 0;
                    listingDirty = false;
                    applySnapshotToControls(true);
                }
            }
            setAllFieldsUnfocused();
            refreshWidgetState();
        }).dimensions(0, 0, 104, 24).build();
        this.addDrawableChild(editDoneButton);

        discoveryButton = ButtonWidget.builder(Text.literal("OFF"), b -> {
            localDiscoveryEnabled = !localDiscoveryEnabled;
            settingsDirty = true;
            requestSettingsSave();
            refreshWidgetState();
        }).dimensions(0, 0, 58, 20).build();
        this.addDrawableChild(discoveryButton);

        saveSettingsButton = ButtonWidget.builder(Text.literal("SAVE"), b -> requestSettingsSave())
                .dimensions(0, 0, 74, 20).build();
        this.addDrawableChild(saveSettingsButton);

        clearHologramButton = ButtonWidget.builder(Text.literal("CLEAR"), b -> {
            var buf = PacketByteBufs.create();
            buf.writeBlockPos(handler.getPos());
            ClientPlayNetworking.send(DabloonStoreNetworking.C2S_CLEAR_HOLOGRAM, buf);
        }).dimensions(0, 0, 62, 20).build();
        this.addDrawableChild(clearHologramButton);

        saveListingButton = ButtonWidget.builder(Text.literal("SAVE ITEM"), b -> requestListingSave())
                .dimensions(0, 0, 98, 22).build();
        this.addDrawableChild(saveListingButton);

        for (int i = 0; i < 9; i++) {
            final int index = i;
            ButtonWidget buy = ButtonWidget.builder(Text.literal("BUY"), b -> {
                var buf = PacketByteBufs.create();
                buf.writeBlockPos(handler.getPos());
                buf.writeVarInt(index);
                ClientPlayNetworking.send(DabloonStoreNetworking.C2S_BUY, buf);
            }).dimensions(0, 0, 46, 18).build();
            buyButtons.add(buy);
            this.addDrawableChild(buy);
        }
    }

    private void layoutWidgets() {
        placeField(storeTitleField, storeTitleFieldRect());
        placeField(listingTitleField, listingTitleFieldRect());
        placeField(listingDescField, listingDescFieldRect());
        placeField(listingPriceField, listingPriceFieldRect());

        editDoneButton.setPosition(gearRect().x, gearRect().y);
        discoveryButton.setPosition(discoveryButtonRect().x, discoveryButtonRect().y);
        saveSettingsButton.setPosition(saveSettingsButtonRect().x, saveSettingsButtonRect().y);
        clearHologramButton.setPosition(clearHologramButtonRect().x, clearHologramButtonRect().y);
        saveListingButton.setPosition(saveListingButtonRect().x, saveListingButtonRect().y);

        for (int i = 0; i < buyButtons.size(); i++) {
            Rect rect = buyButtonRect(i);
            buyButtons.get(i).setPosition(rect.x, rect.y);
        }
    }

    private static void placeField(TextFieldWidget field, Rect rect) {
        field.setX(rect.x);
        field.setY(rect.y);
        field.setWidth(rect.w);
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();

        if (syncCooldown-- <= 0) {
            requestSync();
            syncCooldown = mode == Mode.EDIT ? 30 : 20;
        }

        storeTitleField.tick();
        listingTitleField.tick();
        listingDescField.tick();
        listingPriceField.tick();

        DabloonStoreClientState.BlockStateView latest = DabloonStoreClientState.getBlockState(handler.getPos());
        if (latest != null) {
            this.data = latest;

            if (selectedListing >= latest.snapshot.entries.size()) {
                selectedListing = latest.snapshot.entries.isEmpty() ? -1 : latest.snapshot.entries.size() - 1;
                listingDirty = false;
            }

            applySnapshotToControls(false);
        }

        layoutWidgets();
        refreshWidgetState();
    }

    private void refreshWidgetState() {
        boolean owner = data != null && data.owner;
        boolean edit = owner && mode == Mode.EDIT;
        boolean hasSelectedListing = edit && data != null && selectedListing >= 0 && selectedListing < data.snapshot.entries.size();

        editDoneButton.visible = owner;
        editDoneButton.active = owner;
        editDoneButton.setMessage(Text.literal(mode == Mode.EDIT ? "DONE" : "EDIT"));

        discoveryButton.visible = edit;
        discoveryButton.active = edit;
        discoveryButton.setMessage(Text.literal(localDiscoveryEnabled ? "ON" : "OFF"));

        saveSettingsButton.visible = edit;
        saveSettingsButton.active = edit;

        clearHologramButton.visible = edit;
        clearHologramButton.active = edit;

        saveListingButton.visible = hasSelectedListing;
        saveListingButton.active = hasSelectedListing;

        storeTitleField.setVisible(edit);

        listingTitleField.setVisible(hasSelectedListing);
        listingDescField.setVisible(hasSelectedListing);
        listingPriceField.setVisible(hasSelectedListing);

        if (!edit) {
            setAllFieldsUnfocused();
        }

        for (int i = 0; i < buyButtons.size(); i++) {
            boolean show = mode == Mode.SHOP && data != null && i < data.snapshot.entries.size();
            buyButtons.get(i).visible = show;
            buyButtons.get(i).active = show;
        }
    }

    @Override
    public void close() {
        flushPendingEdits();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        drawShell(context);
        drawHeaderText(context);

        if (mode == Mode.SHOP) {
            drawShopMode(context, mouseX, mouseY);
        } else {
            drawEditMode(context, mouseX, mouseY);
        }

        drawFooter(context);

        super.render(context, mouseX, mouseY, delta);

        if (inventoryPickerOpen) {
            drawInventoryPicker(context, mouseX, mouseY);
        }

        drawTooltips(context, mouseX, mouseY);
    }

    private void drawShell(DrawContext context) {
        context.fill(x - 5, y - 5, x + backgroundWidth + 5, y + backgroundHeight + 5, 0x6A000000);
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, BG);
        context.drawBorder(x, y, backgroundWidth, backgroundHeight, BORDER);

        context.fill(x + 1, y + 1, x + backgroundWidth - 1, y + HEADER_H, 0xE0122030);
        context.fill(x + 1, y + HEADER_H, x + backgroundWidth - 1, y + backgroundHeight - FOOTER_H - 1, 0xD70E141B);
        context.fill(x + 1, y + backgroundHeight - FOOTER_H, x + backgroundWidth - 1, y + backgroundHeight - 1, 0xE0121821);
        context.fill(x + 10, y + HEADER_H + 8, x + backgroundWidth - 10, y + backgroundHeight - FOOTER_H - 8, 0x220C1118);
    }

    private void drawHeaderText(DrawContext context) {
        String storeName = data == null ? "Dabloon Store" : data.snapshot.title;
        context.drawText(this.textRenderer, Text.literal(storeName.toUpperCase()), x + 14, y + 10, GOLD, true);

        if (data != null) {
            context.drawText(this.textRenderer, "Owner: " + data.snapshot.ownerName, x + 14, y + 24, SUBTEXT, false);
        }
    }

    private void drawSection(DrawContext context, Rect rect, String title) {
        context.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, PANEL);
        context.drawBorder(rect.x, rect.y, rect.w, rect.h, BORDER);
        context.fill(rect.x, rect.y, rect.x + rect.w, rect.y + 1, LINE);

        if (title != null && !title.isEmpty()) {
            context.drawText(this.textRenderer, title, rect.x + 10, rect.y + 7, GOLD, false);
        }
    }

    private void drawShopMode(DrawContext context, int mouseX, int mouseY) {
        drawSection(context, browseListingsPanelRect(), "Listings");

        if (data == null || data.snapshot.entries.isEmpty()) {
            Rect panel = browseListingsPanelRect();
            context.drawCenteredTextWithShadow(this.textRenderer, "This store is empty.", panel.centerX(), panel.y + 118, TEXT);
            context.drawCenteredTextWithShadow(this.textRenderer, "Come back later.", panel.centerX(), panel.y + 132, SUBTEXT);
            return;
        }

        for (int i = 0; i < data.snapshot.entries.size(); i++) {
            drawBrowseCard(context, browseCardRect(i), data.snapshot.entries.get(i), mouseX, mouseY);
        }
    }

    private void drawEditMode(DrawContext context, int mouseX, int mouseY) {
        drawSection(context, editListingsPanelRect(), "Listings");
        drawSection(context, settingsPanelRect(), "Store Settings");
        drawSection(context, detailsPanelRect(), "Listing Details");

        drawEditListings(context, mouseX, mouseY);
        drawSettingsPanel(context, mouseX, mouseY);
        drawDetailsPanel(context);
    }

    private void drawEditListings(DrawContext context, int mouseX, int mouseY) {
        if (data == null) return;

        for (int i = 0; i < 9; i++) {
            Rect slot = editListingCardRect(i);
            boolean hovered = slot.contains(mouseX, mouseY);
            boolean selected = i == selectedListing;
            boolean filled = i < data.snapshot.entries.size();

            context.fill(slot.x, slot.y, slot.x + slot.w, slot.y + slot.h, hovered ? SLOT_FILL_HOVER : SLOT_FILL);
            context.drawBorder(slot.x, slot.y, slot.w, slot.h, selected ? ACCENT : BORDER);

            if (!filled) {
                context.drawCenteredTextWithShadow(this.textRenderer, "+", slot.centerX(), slot.y + 8, 0xFF9AB0C7);
                context.drawCenteredTextWithShadow(this.textRenderer, "Add item", slot.centerX(), slot.y + 22, 0xFF9AB0C7);
                continue;
            }

            DabloonStoreEntry entry = data.snapshot.entries.get(i);

            Rect icon = new Rect(slot.x + 7, slot.y + 6, 28, 28);
            context.fill(icon.x, icon.y, icon.x + icon.w, icon.y + icon.h, 0x50111820);
            context.drawBorder(icon.x, icon.y, icon.w, icon.h, BORDER);
            context.drawItem(entry.stock(), icon.x + 6, icon.y + 6);

            context.drawText(this.textRenderer, trimToWidth(entry.title(), 50), slot.x + 41, slot.y + 8, TEXT, false);
            context.drawText(this.textRenderer, "x" + entry.stockCount(), slot.x + 41, slot.y + 19, SUBTEXT, false);

            Rect delete = deleteRect(i);
            boolean deleteHover = delete.contains(mouseX, mouseY);
            context.fill(delete.x, delete.y, delete.x + delete.w, delete.y + delete.h, deleteHover ? DELETE_FILL_HOVER : DELETE_FILL);
            context.drawBorder(delete.x, delete.y, delete.w, delete.h, BORDER);
            context.drawCenteredTextWithShadow(this.textRenderer, "X", delete.centerX(), delete.y + 2, 0xFFFFFFFF);
        }
    }

    private void drawSettingsPanel(DrawContext context, int mouseX, int mouseY) {
        if (data == null) return;

        Rect panel = settingsPanelRect();

        context.drawText(this.textRenderer, "Store title", panel.x + 10, panel.y + 20, SUBTEXT, false);
        context.drawText(this.textRenderer, "Discovery", panel.x + 10, panel.y + 63, TEXT, false);
        context.drawText(this.textRenderer, "Display item", panel.x + 10, panel.y + 86, TEXT, false);


        Rect holo = hologramPreviewRect();
        boolean hovered = holo.contains(mouseX, mouseY);
        context.fill(holo.x, holo.y, holo.x + holo.w, holo.y + holo.h, hovered ? SLOT_FILL_HOVER : SLOT_FILL);
        context.drawBorder(holo.x, holo.y, holo.w, holo.h, hovered ? ACCENT : BORDER);

        if (data.snapshot.hologramStack != null && !data.snapshot.hologramStack.isEmpty()) {
            context.drawItem(data.snapshot.hologramStack, holo.x + 12, holo.y + 10);
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer, "+", holo.centerX(), holo.y + 8, 0xFF9AB0C7);
            context.drawCenteredTextWithShadow(this.textRenderer, "Set", holo.centerX(), holo.y + 20, 0xFF9AB0C7);
        }

        Rect swatch = colorSwatchRect();
        context.fill(swatch.x, swatch.y, swatch.x + swatch.w, swatch.y + swatch.h, 0xFF000000 | hueColor());
        context.drawBorder(swatch.x, swatch.y, swatch.w, swatch.h, BORDER);

        drawHueSlider(context, hueSliderRect(), mouseX, mouseY);
    }

    private void drawDetailsPanel(DrawContext context) {
        Rect panel = detailsPanelRect();

        if (data == null || selectedListing < 0 || selectedListing >= data.snapshot.entries.size()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    "Select a listing to edit it.",
                    panel.centerX(), panel.y + 38, TEXT);
            context.drawCenteredTextWithShadow(this.textRenderer,
                    "Empty slots add new items.",
                    panel.centerX(), panel.y + 54, SUBTEXT);
            return;
        }

        DabloonStoreEntry entry = data.snapshot.entries.get(selectedListing);
        Rect icon = detailItemRect();

        context.fill(icon.x, icon.y, icon.x + icon.w, icon.y + icon.h, 0x50101720);
        context.drawBorder(icon.x, icon.y, icon.w, icon.h, BORDER);
        context.drawItem(entry.stock(), icon.x + 14, icon.y + 14);

        context.drawText(this.textRenderer, "Title", panel.x + 72, panel.y + 10, SUBTEXT, false);
        context.drawText(this.textRenderer, "Price", panel.x + 370, panel.y + 10, SUBTEXT, false);
        context.drawText(this.textRenderer, "Description", panel.x + 72, panel.y + 50, SUBTEXT, false);
        context.drawText(this.textRenderer, "Stock: " + entry.stockCount(), panel.x + 16, panel.y + 80, SUBTEXT, false);
    }

    private void drawBrowseCard(DrawContext context, Rect rect, DabloonStoreEntry entry, int mouseX, int mouseY) {
        boolean hovered = rect.contains(mouseX, mouseY);

        context.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, hovered ? SLOT_FILL_HOVER : SLOT_FILL);
        context.drawBorder(rect.x, rect.y, rect.w, rect.h, BORDER);
        context.fill(rect.x, rect.y, rect.x + rect.w, rect.y + 1, hovered ? ACCENT : LINE);

        Rect icon = new Rect(rect.x + 10, rect.y + 12, 52, 52);
        context.fill(icon.x, icon.y, icon.x + icon.w, icon.y + icon.h, 0x50101720);
        context.drawBorder(icon.x, icon.y, icon.w, icon.h, BORDER);
        context.drawItem(entry.stock(), icon.x + 18, icon.y + 18);
        context.drawItemInSlot(this.textRenderer, entry.stock(), icon.x + 18, icon.y + 18);

        context.drawText(this.textRenderer, trimToWidth(entry.title(), 86), rect.x + 72, rect.y + 14, TEXT, false);

        List<OrderedText> lines = wrap(entry.description(), 86, 2);
        int lineY = rect.y + 28;
        for (OrderedText line : lines) {
            context.drawText(this.textRenderer, line, rect.x + 72, lineY, SUBTEXT, false);
            lineY += 9;
        }

        drawPrice(context, rect.x + 72, rect.y + 57, entry.pricePerItem());
    }

    private void drawHueSlider(DrawContext context, Rect rect, int mouseX, int mouseY) {
        for (int i = 0; i < rect.w; i++) {
            float h = i / (float) Math.max(1, rect.w - 1);
            int c = 0xFF000000 | hsvToRgb(h, 0.78f, 1.0f);
            context.fill(rect.x + i, rect.y, rect.x + i + 1, rect.y + rect.h, c);
        }
        context.drawBorder(rect.x, rect.y, rect.w, rect.h, BORDER);

        int knobX = rect.x + Math.round(hue01 * (rect.w - 1));
        boolean hot = rect.contains(mouseX, mouseY) || draggingHue;
        context.fill(knobX - 2, rect.y - 2, knobX + 3, rect.y + rect.h + 2, hot ? 0xFFF8FCFF : 0xFFD8E2EE);
        context.drawBorder(knobX - 2, rect.y - 2, 5, rect.h + 4, BORDER);
    }

    private void drawInventoryPicker(DrawContext context, int mouseX, int mouseY) {
        if (client == null || client.player == null) return;

        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(0.0f, 0.0f, 400.0f);

        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xDA000000);

        Rect modal = inventoryPickerRect();
        context.fill(modal.x, modal.y, modal.x + modal.w, modal.y + modal.h, 0xFF101724);
        context.drawBorder(modal.x, modal.y, modal.w, modal.h, BORDER);
        context.fill(modal.x, modal.y, modal.x + modal.w, modal.y + 1, LINE);
        context.fill(modal.x + 1, modal.y + 1, modal.x + modal.w - 1, modal.y + 34, 0xFF131E2E);

        context.drawText(this.textRenderer,
                pickerForHologram ? "Choose hologram item" : "Choose listing item",
                modal.x + 12, modal.y + 8, GOLD, false);
        context.drawText(this.textRenderer,
                pickerForHologram ? "Click any stack to use it as the display item." : "Click any stack to create a listing.",
                modal.x + 12, modal.y + 22, SUBTEXT, false);

        Rect close = inventoryCloseRect();
        context.fill(close.x, close.y, close.x + close.w, close.y + close.h,
                close.contains(mouseX, mouseY) ? SLOT_FILL_HOVER : SLOT_FILL);
        context.drawBorder(close.x, close.y, close.w, close.h, BORDER);
        context.drawCenteredTextWithShadow(this.textRenderer, "X", close.centerX(), close.y + 3, TEXT);

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int index = row == 3 ? col : col + row * 9 + 9;
                Rect rect = inventoryRect(row, col);
                boolean hovered = rect.contains(mouseX, mouseY);

                context.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, hovered ? SLOT_FILL_HOVER : SLOT_FILL);
                context.drawBorder(rect.x, rect.y, rect.w, rect.h, BORDER);

                ItemStack stack = client.player.getInventory().getStack(index);
                if (!stack.isEmpty()) {
                    context.drawItem(stack, rect.x + 2, rect.y + 2);
                    context.drawItemInSlot(this.textRenderer, stack, rect.x + 2, rect.y + 2);
                }
            }
        }

        matrices.pop();
    }

    private void drawFooter(DrawContext context) {
        if (data == null) return;

        String status = data.owner ? (mode == Mode.EDIT ? "Owner mode" : "Browse mode") : "Browse mode";
        int width = this.textRenderer.getWidth(status);
        context.drawText(this.textRenderer, status,
                x + backgroundWidth - 12 - width, y + backgroundHeight - 16, SUBTEXT, false);
    }

    private void drawTooltips(DrawContext context, int mouseX, int mouseY) {
        if (data == null) return;

        if (inventoryPickerOpen && client != null && client.player != null) {
            for (int row = 0; row < 4; row++) {
                for (int col = 0; col < 9; col++) {
                    int index = row == 3 ? col : col + row * 9 + 9;
                    Rect rect = inventoryRect(row, col);
                    if (!rect.contains(mouseX, mouseY)) continue;

                    ItemStack stack = client.player.getInventory().getStack(index);
                    if (!stack.isEmpty()) {
                        context.drawItemTooltip(this.textRenderer, stack, mouseX, mouseY);
                        return;
                    }
                }
            }
            return;
        }

        if (mode == Mode.EDIT) {
            for (int i = 0; i < 9; i++) {
                Rect card = editListingCardRect(i);
                if (!card.contains(mouseX, mouseY)) continue;

                if (i >= data.snapshot.entries.size()) {
                    context.drawTooltip(this.textRenderer, List.of(Text.literal("Add a new listing")), mouseX, mouseY);
                    return;
                }

                if (deleteRect(i).contains(mouseX, mouseY)) {
                    context.drawTooltip(this.textRenderer, List.of(Text.literal("Remove listing")), mouseX, mouseY);
                    return;
                }

                ItemStack stack = data.snapshot.entries.get(i).stock();
                if (!stack.isEmpty()) {
                    context.drawItemTooltip(this.textRenderer, stack, mouseX, mouseY);
                    return;
                }
            }

            if (hologramPreviewRect().contains(mouseX, mouseY)) {
                context.drawTooltip(this.textRenderer, List.of(Text.literal("Choose the display item")), mouseX, mouseY);
                return;
            }
        } else {
            for (int i = 0; i < data.snapshot.entries.size(); i++) {
                Rect card = browseCardRect(i);
                if (!card.contains(mouseX, mouseY)) continue;

                DabloonStoreEntry entry = data.snapshot.entries.get(i);
                List<Text> tooltip = new ArrayList<>();
                tooltip.add(Text.literal(entry.title()));
                if (!entry.description().isBlank()) {
                    tooltip.add(Text.literal(entry.description()));
                }
                tooltip.add(Text.literal("Stock: " + entry.stockCount()));
                tooltip.add(Text.literal("Price: " + entry.pricePerItem()));
                context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        if (inventoryPickerOpen) {
            return handleInventoryPickerClick(mouseX, mouseY);
        }

        if (mode == Mode.EDIT && data != null && data.owner) {
            if (hueSliderRect().contains(mouseX, mouseY)) {
                draggingHue = true;
                setHueFromMouse(mouseX);
                return true;
            }

            if (hologramPreviewRect().contains(mouseX, mouseY)) {
                inventoryPickerOpen = true;
                pickerForHologram = true;
                setAllFieldsUnfocused();
                refreshWidgetState();
                return true;
            }

            for (int i = 0; i < 9; i++) {
                Rect card = editListingCardRect(i);
                if (!card.contains(mouseX, mouseY)) continue;

                if (i < data.snapshot.entries.size() && deleteRect(i).contains(mouseX, mouseY)) {
                    var buf = PacketByteBufs.create();
                    buf.writeBlockPos(handler.getPos());
                    buf.writeVarInt(i);
                    ClientPlayNetworking.send(DabloonStoreNetworking.C2S_REMOVE_LISTING, buf);

                    if (selectedListing == i) {
                        selectedListing = -1;
                        listingDirty = false;
                    }
                    return true;
                }

                if (i < data.snapshot.entries.size()) {
                    selectedListing = i;
                    listingDirty = false;
                    applySnapshotToControls(true);
                    setAllFieldsUnfocused();
                    refreshWidgetState();
                    return true;
                }

                inventoryPickerOpen = true;
                pickerForHologram = false;
                setAllFieldsUnfocused();
                refreshWidgetState();
                return true;
            }
        }

        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (handled) {
            return true;
        }

        setAllFieldsUnfocused();

        if (screenBounds().contains(mouseX, mouseY)) {
            return true;
        }
        return false;
    }

    private boolean handleInventoryPickerClick(double mouseX, double mouseY) {
        if (inventoryCloseRect().contains(mouseX, mouseY) || !inventoryPickerRect().contains(mouseX, mouseY)) {
            inventoryPickerOpen = false;
            refreshWidgetState();
            return true;
        }

        if (client != null && client.player != null) {
            for (int row = 0; row < 4; row++) {
                for (int col = 0; col < 9; col++) {
                    int index = row == 3 ? col : col + row * 9 + 9;
                    Rect rect = inventoryRect(row, col);
                    if (!rect.contains(mouseX, mouseY)) continue;

                    ItemStack stack = client.player.getInventory().getStack(index);
                    if (stack.isEmpty()) return true;

                    var buf = PacketByteBufs.create();
                    buf.writeBlockPos(handler.getPos());
                    buf.writeVarInt(index);

                    if (pickerForHologram) {
                        ClientPlayNetworking.send(DabloonStoreNetworking.C2S_SET_HOLOGRAM, buf);
                    } else {
                        ClientPlayNetworking.send(DabloonStoreNetworking.C2S_ADD_LISTING, buf);
                    }

                    inventoryPickerOpen = false;
                    refreshWidgetState();
                    return true;
                }
            }
        }

        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && draggingHue) {
            setHueFromMouse(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingHue) {
            draggingHue = false;
            requestSettingsSave();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (storeTitleField.isVisible() && storeTitleField.charTyped(chr, modifiers)) {
            settingsDirty = true;
            return true;
        }
        if (listingTitleField.isVisible() && listingTitleField.charTyped(chr, modifiers)) {
            listingDirty = true;
            return true;
        }
        if (listingDescField.isVisible() && listingDescField.charTyped(chr, modifiers)) {
            listingDirty = true;
            return true;
        }
        if (listingPriceField.isVisible() && listingPriceField.charTyped(chr, modifiers)) {
            listingDirty = true;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean inventoryKey =
                keyCode == GLFW.GLFW_KEY_E ||
                        (this.client != null && this.client.options.inventoryKey.matchesKey(keyCode, scanCode));

        if (storeTitleField.isVisible() && storeTitleField.keyPressed(keyCode, scanCode, modifiers)) {
            settingsDirty = true;
            return true;
        }
        if (listingTitleField.isVisible() && listingTitleField.keyPressed(keyCode, scanCode, modifiers)) {
            listingDirty = true;
            return true;
        }
        if (listingDescField.isVisible() && listingDescField.keyPressed(keyCode, scanCode, modifiers)) {
            listingDirty = true;
            return true;
        }
        if (listingPriceField.isVisible() && listingPriceField.keyPressed(keyCode, scanCode, modifiers)) {
            listingDirty = true;
            return true;
        }

        if (inventoryKey) {
            if (isAnyFieldFocused()) {
                return true;
            }
            if (inventoryPickerOpen) {
                inventoryPickerOpen = false;
                refreshWidgetState();
                return true;
            }
            if (mode == Mode.EDIT) {
                flushPendingEdits();
                mode = Mode.SHOP;
                setAllFieldsUnfocused();
                refreshWidgetState();
                return true;
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (inventoryPickerOpen) {
                inventoryPickerOpen = false;
                refreshWidgetState();
                return true;
            }
            if (mode == Mode.EDIT) {
                flushPendingEdits();
                mode = Mode.SHOP;
                setAllFieldsUnfocused();
                refreshWidgetState();
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (storeTitleField.isVisible() && storeTitleField.isFocused()) {
                requestSettingsSave();
                return true;
            }
            if (selectedListing >= 0 && listingTitleField.isVisible()) {
                requestListingSave();
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean isAnyFieldFocused() {
        return storeTitleField.isFocused()
                || listingTitleField.isFocused()
                || listingDescField.isFocused()
                || listingPriceField.isFocused();
    }

    private void requestSync() {
        var buf = PacketByteBufs.create();
        buf.writeBlockPos(handler.getPos());
        ClientPlayNetworking.send(DabloonStoreNetworking.C2S_REQUEST_BLOCK_SYNC, buf);
    }

    private void requestSettingsSave() {
        settingsDirty = false;

        var buf = PacketByteBufs.create();
        buf.writeBlockPos(handler.getPos());
        buf.writeString(storeTitleField.getText(), 64);
        buf.writeBoolean(localDiscoveryEnabled);
        buf.writeInt(hueColor());
        ClientPlayNetworking.send(DabloonStoreNetworking.C2S_UPDATE_SETTINGS, buf);
    }

    private void requestListingSave() {
        if (selectedListing < 0 || data == null || selectedListing >= data.snapshot.entries.size()) return;

        listingDirty = false;

        var buf = PacketByteBufs.create();
        buf.writeBlockPos(handler.getPos());
        buf.writeVarInt(selectedListing);
        buf.writeString(listingTitleField.getText(), DabloonStoreEntry.TITLE_MAX_LEN);
        buf.writeString(listingDescField.getText(), DabloonStoreEntry.DESC_MAX_LEN);
        buf.writeVarInt(parsePrice());
        ClientPlayNetworking.send(DabloonStoreNetworking.C2S_UPDATE_LISTING, buf);
    }

    private void flushPendingEdits() {
        if (settingsDirty) {
            requestSettingsSave();
        }
        if (listingDirty) {
            requestListingSave();
        }
    }

    private void applySnapshotToControls(boolean force) {
        DabloonStoreClientState.BlockStateView snapshotView = DabloonStoreClientState.getBlockState(handler.getPos());
        if (snapshotView != null) {
            data = snapshotView;
        }
        if (data == null) return;

        if (force || !settingsDirty) {
            if (!storeTitleField.isFocused() || force) {
                storeTitleField.setText(data.snapshot.title);
            }
            localDiscoveryEnabled = data.snapshot.discoveryEnabled;
            hue01 = rgbToHue(data.snapshot.hologramColor);
        }

        if (selectedListing >= 0 && selectedListing < data.snapshot.entries.size()) {
            DabloonStoreEntry entry = data.snapshot.entries.get(selectedListing);
            if (force || !listingDirty) {
                if (!listingTitleField.isFocused() || force) {
                    listingTitleField.setText(entry.title());
                }
                if (!listingDescField.isFocused() || force) {
                    listingDescField.setText(entry.description());
                }
                if (!listingPriceField.isFocused() || force) {
                    listingPriceField.setText(Integer.toString(entry.pricePerItem()));
                }
            }
        } else if (force || !listingDirty) {
            listingTitleField.setText("");
            listingDescField.setText("");
            listingPriceField.setText("1");
        }
    }

    private void setAllFieldsUnfocused() {
        storeTitleField.setFocused(false);
        listingTitleField.setFocused(false);
        listingDescField.setFocused(false);
        listingPriceField.setFocused(false);
    }

    private void setHueFromMouse(double mouseX) {
        Rect rect = hueSliderRect();
        float value = (float) ((mouseX - rect.x) / Math.max(1.0, rect.w - 1.0));
        hue01 = MathHelper.clamp(value, 0.0f, 1.0f);
        settingsDirty = true;
    }

    private int parsePrice() {
        try {
            return Math.max(1, Integer.parseInt(listingPriceField.getText().trim()));
        } catch (Exception ignored) {
            return 1;
        }
    }

    private int hueColor() {
        return hsvToRgb(hue01, 0.78f, 1.0f);
    }

    private String trimToWidth(String text, int pixelWidth) {
        return this.textRenderer.trimToWidth(text == null ? "" : text, pixelWidth);
    }

    private List<OrderedText> wrap(String text, int maxWidth, int maxLines) {
        List<OrderedText> wrapped = new ArrayList<>();
        if (text == null || text.isBlank()) return wrapped;
        wrapped.addAll(this.textRenderer.wrapLines(Text.literal(text), maxWidth));
        if (wrapped.size() > maxLines) {
            return new ArrayList<>(wrapped.subList(0, maxLines));
        }
        return wrapped;
    }

    private void drawPrice(DrawContext context, int drawX, int drawY, int amount) {
        String text = Integer.toString(amount);
        context.drawText(this.textRenderer, text, drawX, drawY, GOLD, false);
        drawIcon(context, DABLOON_ICON, drawX + this.textRenderer.getWidth(text) + 4, drawY - 2, 12);
    }

    private void drawIcon(DrawContext context, Identifier texture, int drawX, int drawY, int size) {
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(drawX, drawY, 0.0f);
        matrices.scale(size / (float) DABLOON_TEX_SIZE, size / (float) DABLOON_TEX_SIZE, 1.0f);
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, texture);
        context.drawTexture(texture, 0, 0, 0, 0, DABLOON_TEX_SIZE, DABLOON_TEX_SIZE, DABLOON_TEX_SIZE, DABLOON_TEX_SIZE);
        matrices.pop();
    }

    private static float rgbToHue(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;

        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        if (delta <= 0.0001f) return 0.0f;

        float hue;
        if (max == r) {
            hue = ((g - b) / delta) % 6.0f;
        } else if (max == g) {
            hue = ((b - r) / delta) + 2.0f;
        } else {
            hue = ((r - g) / delta) + 4.0f;
        }
        hue /= 6.0f;
        if (hue < 0.0f) hue += 1.0f;
        return hue;
    }

    private static int hsvToRgb(float hue, float saturation, float value) {
        hue = hue - (float) Math.floor(hue);
        saturation = MathHelper.clamp(saturation, 0.0f, 1.0f);
        value = MathHelper.clamp(value, 0.0f, 1.0f);

        float h = hue * 6.0f;
        int sector = (int) Math.floor(h);
        float f = h - sector;
        float p = value * (1.0f - saturation);
        float q = value * (1.0f - f * saturation);
        float t = value * (1.0f - (1.0f - f) * saturation);

        float r;
        float g;
        float b;
        switch (sector % 6) {
            case 0 -> { r = value; g = t; b = p; }
            case 1 -> { r = q; g = value; b = p; }
            case 2 -> { r = p; g = value; b = t; }
            case 3 -> { r = p; g = q; b = value; }
            case 4 -> { r = t; g = p; b = value; }
            default -> { r = value; g = p; b = q; }
        }

        int ri = MathHelper.clamp(Math.round(r * 255.0f), 0, 255);
        int gi = MathHelper.clamp(Math.round(g * 255.0f), 0, 255);
        int bi = MathHelper.clamp(Math.round(b * 255.0f), 0, 255);
        return (ri << 16) | (gi << 8) | bi;
    }

    private Rect screenBounds() {
        return new Rect(x, y, backgroundWidth, backgroundHeight);
    }

    private Rect gearRect() {
        return new Rect(x + backgroundWidth - 120, y + 9, 104, 24);
    }

    private Rect browseListingsPanelRect() {
        return new Rect(x + 16, y + 56, backgroundWidth - 32, 266);
    }

    private Rect browseCardRect(int i) {
        Rect panel = browseListingsPanelRect();
        int col = i % 3;
        int row = i / 3;
        int startX = panel.x + 12;
        int startY = panel.y + 30;
        return new Rect(startX + col * 172, startY + row * 86, 164, 78);
    }

    private Rect buyButtonRect(int i) {
        Rect card = browseCardRect(i);
        return new Rect(card.x + card.w - 54, card.y + 53, 46, 18);
    }

    private Rect editListingsPanelRect() {
        return new Rect(x + 16, y + 56, 344, 166);
    }

    private Rect settingsPanelRect() {
        return new Rect(x + 372, y + 56, 172, 166);
    }

    private Rect detailsPanelRect() {
        return new Rect(x + 16, y + 232, 528, 110);
    }

    private Rect editListingCardRect(int i) {
        Rect panel = editListingsPanelRect();
        int col = i % 3;
        int row = i / 3;
        int startX = panel.x + 10;
        int startY = panel.y + 28;
        return new Rect(startX + col * 110, startY + row * 42, 104, 36);
    }

    private Rect deleteRect(int i) {
        Rect card = editListingCardRect(i);
        return new Rect(card.x + card.w - 14, card.y + 2, 12, 12);
    }

    private Rect storeTitleFieldRect() {
        Rect p = settingsPanelRect();
        return new Rect(p.x + 10, p.y + 34, p.w - 20, 20);
    }

    private Rect discoveryButtonRect() {
        Rect p = settingsPanelRect();
        return new Rect(p.x + p.w - 68, p.y + 58, 58, 20);
    }

    private Rect hologramPreviewRect() {
        Rect p = settingsPanelRect();
        return new Rect(p.x + 10, p.y + 102, 40, 36);
    }

    private Rect colorSwatchRect() {
        Rect p = settingsPanelRect();
        return new Rect(p.x + 58, p.y + 104, p.w - 68, 12);
    }

    private Rect hueSliderRect() {
        Rect p = settingsPanelRect();
        return new Rect(p.x + 58, p.y + 122, p.w - 68, 10);
    }

    private Rect saveSettingsButtonRect() {
        Rect p = settingsPanelRect();
        return new Rect(p.x + 10, p.y + p.h - 26, 74, 20);
    }

    private Rect clearHologramButtonRect() {
        Rect p = settingsPanelRect();
        return new Rect(p.x + p.w - 72, p.y + p.h - 26, 62, 20);
    }

    private Rect detailItemRect() {
        Rect p = detailsPanelRect();
        return new Rect(p.x + 16, p.y + 31, 44, 44);
    }

    private Rect listingTitleFieldRect() {
        Rect p = detailsPanelRect();
        return new Rect(p.x + 72, p.y + 22, 286, 20);
    }

    private Rect listingDescFieldRect() {
        Rect p = detailsPanelRect();
        return new Rect(p.x + 72, p.y + 62, 286, 20);
    }

    private Rect listingPriceFieldRect() {
        Rect p = detailsPanelRect();
        return new Rect(p.x + 370, p.y + 22, 70, 20);
    }

    private Rect saveListingButtonRect() {
        Rect p = detailsPanelRect();
        return new Rect(p.x + p.w - 114, p.y + 60, 98, 22);
    }

    private Rect inventoryPickerRect() {
        return new Rect(x + (backgroundWidth - 320) / 2, y + 76, 320, 184);
    }

    private Rect inventoryCloseRect() {
        Rect modal = inventoryPickerRect();
        return new Rect(modal.x + modal.w - 26, modal.y + 8, 18, 18);
    }

    private Rect inventoryRect(int row, int col) {
        Rect modal = inventoryPickerRect();
        int startX = modal.x + 16;
        int startY = modal.y + 52;
        return new Rect(startX + col * 28, startY + row * 28, 24, 24);
    }
}
