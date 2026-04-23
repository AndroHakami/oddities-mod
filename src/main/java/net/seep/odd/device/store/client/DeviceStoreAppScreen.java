package net.seep.odd.device.store.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.Oddities;
import net.seep.odd.client.device.DeviceHomeScreen;
import net.seep.odd.device.store.DabloonStoreEntry;
import net.seep.odd.device.store.DabloonStoreNetworking;
import net.seep.odd.device.store.DabloonStoreSale;
import net.seep.odd.device.store.DabloonStoreSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public final class DeviceStoreAppScreen extends Screen {
    private enum View {
        LANDING,
        MY_STORES,
        FIND_STORES,
        MY_STORE_DETAIL,
        FIND_STORE_DETAIL,
        SALES
    }

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }

        int centerX() {
            return x + (w / 2);
        }

        int centerY() {
            return y + (h / 2);
        }
    }

    private record SaleRow(DabloonStoreSnapshot store, DabloonStoreSale sale) {}

    private static final Identifier HOME_BG = new Identifier(Oddities.MOD_ID, "textures/gui/device/home_bg.png");
    private static final Identifier HOME_OVERLAY = new Identifier(Oddities.MOD_ID, "textures/gui/device/home_overlay.png");
    private static final Identifier ICON_HOME = new Identifier(Oddities.MOD_ID, "textures/gui/device/social/top/home.png");

    // Optional user textures. If absent, the screen falls back to drawn placeholder buttons.
    private static final Identifier BTN_MY_STORES = new Identifier(Oddities.MOD_ID, "textures/gui/device/store/app/my_stores_button.png");
    private static final Identifier BTN_FIND_STORES = new Identifier(Oddities.MOD_ID, "textures/gui/device/store/app/find_stores_button.png");

    private static final int TOP_ICON_SIZE = 22;
    private static final int GOLD = 0xFFF7E6A4;
    private static final int TEXT = 0xFFF4F7FF;
    private static final int SUBTEXT = 0xFF9FB0CC;
    private static final int ACCENT = 0xFF8AD4FF;
    private static final int MUTED = 0xFF6F82A2;
    private static final int PANEL = 0xA8141C28;
    private static final int PANEL_DARK = 0xD8182130;
    private static final int PANEL_HOVER = 0xE3273448;
    private static final int PANEL_ACTIVE = 0xEE30455F;
    private static final int CARD_TOP = 0x55DCE7FF;
    private static final int PAGE_SIZE = 9;
    private static final int ITEM_PAGE_SIZE = 6;
    private static final int SALES_PAGE_SIZE = 6;

    private final Map<String, Float> hoverProgress = new HashMap<>();

    private View view = View.LANDING;
    private DabloonStoreSnapshot selectedOwnedStore = null;
    private DabloonStoreSnapshot selectedFoundStore = null;
    private int myStoresPage = 0;
    private int findStoresPage = 0;
    private int myStoreItemPage = 0;
    private int findStoreItemPage = 0;
    private int salesPage = 0;

    public DeviceStoreAppScreen() {
        super(Text.literal("Store"));
    }

    @Override
    protected void init() {
        ClientPlayNetworking.send(DabloonStoreNetworking.C2S_REQUEST_APP_SYNC, PacketByteBufs.create());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (view == View.LANDING) {
            if (this.client != null) {
                this.client.setScreen(new DeviceHomeScreen());
            }
            return;
        }
        goBack();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        int left = (this.width - DeviceHomeScreen.GUI_W) / 2;
        int top = (this.height - DeviceHomeScreen.GUI_H) / 2;

        context.fill(left - 5, top - 5, left + DeviceHomeScreen.GUI_W + 5, top + DeviceHomeScreen.GUI_H + 5, 0x66000000);
        context.drawTexture(HOME_BG, left, top, 0, 0, DeviceHomeScreen.GUI_W, DeviceHomeScreen.GUI_H, DeviceHomeScreen.GUI_W, DeviceHomeScreen.GUI_H);

        renderTopBar(context, left, top, mouseX, mouseY);

        switch (view) {
            case LANDING -> renderLanding(context, left, top, mouseX, mouseY);
            case MY_STORES -> renderStoreGrid(context, left, top, mouseX, mouseY, true);
            case FIND_STORES -> renderStoreGrid(context, left, top, mouseX, mouseY, false);
            case MY_STORE_DETAIL -> renderStoreDetail(context, left, top, mouseX, mouseY, selectedOwnedStore, true);
            case FIND_STORE_DETAIL -> renderStoreDetail(context, left, top, mouseX, mouseY, selectedFoundStore, false);
            case SALES -> renderSales(context, left, top, mouseX, mouseY);
        }

        context.drawTexture(HOME_OVERLAY, left, top, 0, 0, DeviceHomeScreen.GUI_W, DeviceHomeScreen.GUI_H, DeviceHomeScreen.GUI_W, DeviceHomeScreen.GUI_H);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderTopBar(DrawContext context, int left, int top, int mouseX, int mouseY) {
        Rect home = homeRect(left, top);
        renderIconButton(context, "home", home, ICON_HOME, mouseX, mouseY);

        if (view != View.LANDING) {
            Rect back = backRect(left, top);
            renderTextButton(context, "back", back, "Back", mouseX, mouseY, false);
        }

        if (view == View.MY_STORES) {
            Rect sales = actionRect(left, top);
            renderTextButton(context, "sales", sales, "Sales", mouseX, mouseY, false);
        }
    }

    private void renderLanding(DrawContext context, int left, int top, int mouseX, int mouseY) {
        Rect myStores = landingButtonRect(left, top, 0);
        Rect findStores = landingButtonRect(left, top, 1);

        ItemStack myPreview = previewStackForLanding(true);
        ItemStack findPreview = previewStackForLanding(false);

        renderLandingButton(context, "landing_my", myStores, BTN_MY_STORES, "My Stores", "Manage what you own.", myPreview, mouseX, mouseY);
        renderLandingButton(context, "landing_find", findStores, BTN_FIND_STORES, "Find Stores", "Browse shops near you.", findPreview, mouseX, mouseY);

        context.drawCenteredTextWithShadow(this.textRenderer, "Choose a store view", left + DeviceHomeScreen.GUI_W / 2, top + 58, TEXT);
        context.drawCenteredTextWithShadow(this.textRenderer, "Same device feel, cleaner layout.", left + DeviceHomeScreen.GUI_W / 2, top + 72, SUBTEXT);
    }

    private void renderStoreGrid(DrawContext context, int left, int top, int mouseX, int mouseY, boolean owned) {
        List<DabloonStoreSnapshot> stores = owned ? sortedOwnedStores() : sortedDiscoverableStores();
        int maxPage = maxPage(stores.size(), PAGE_SIZE);
        if (owned) {
            myStoresPage = MathHelper.clamp(myStoresPage, 0, maxPage);
        } else {
            findStoresPage = MathHelper.clamp(findStoresPage, 0, maxPage);
        }

        int page = owned ? myStoresPage : findStoresPage;
        List<DabloonStoreSnapshot> visible = pageSlice(stores, page, PAGE_SIZE);

        Rect header = contentHeaderRect(left, top);
        context.fill(header.x, header.y, header.x + header.w, header.y + header.h, 0x7A101622);
        context.fill(header.x, header.y, header.x + header.w, header.y + 1, CARD_TOP);
        context.drawBorder(header.x, header.y, header.w, header.h, 0xAA000000);
        context.drawTextWithShadow(this.textRenderer, owned ? "Your stores" : "Find stores", header.x + 10, header.y + 8, GOLD);
        context.drawText(this.textRenderer, owned ? "Nearest first, 9 per page." : "Discoverable shops closest to you.", header.x + 10, header.y + 20, SUBTEXT, false);

        if (stores.isEmpty()) {
            Rect empty = emptyPanelRect(left, top);
            drawPanel(context, empty);
            context.drawCenteredTextWithShadow(this.textRenderer, owned ? "You do not own any stores yet." : "No discoverable stores right now.", empty.centerX(), empty.y + 72, TEXT);
            context.drawCenteredTextWithShadow(this.textRenderer, owned ? "Open a store block in-world and it will appear here." : "Shops with discovery enabled will appear here.", empty.centerX(), empty.y + 86, SUBTEXT);
            return;
        }

        for (int i = 0; i < visible.size(); i++) {
            DabloonStoreSnapshot store = visible.get(i);
            Rect card = gridCardRect(left, top, i);
            boolean hovered = card.contains(mouseX, mouseY);
            float hover = hover("grid_" + owned + "_" + i, hovered);
            drawStoreCard(context, card, store, owned, hover);
        }

        Rect leftArrow = leftArrowRect(left, top);
        Rect rightArrow = rightArrowRect(left, top);
        if (page > 0) {
            renderArrowButton(context, "page_left_" + owned, leftArrow, "<", mouseX, mouseY);
        }
        if (page < maxPage) {
            renderArrowButton(context, "page_right_" + owned, rightArrow, ">", mouseX, mouseY);
        }

        context.drawCenteredTextWithShadow(this.textRenderer, pageLabel(page, maxPage), left + DeviceHomeScreen.GUI_W / 2, top + 288, SUBTEXT);
    }

    private void renderStoreDetail(DrawContext context, int left, int top, int mouseX, int mouseY, DabloonStoreSnapshot store, boolean owned) {
        if (store == null) {
            view = owned ? View.MY_STORES : View.FIND_STORES;
            return;
        }

        Rect hero = detailHeroRect(left, top);
        drawPanel(context, hero);

        renderLargePreview(context, store.hologramStack, hero.x + 18, hero.y + 18, 1.55f);
        context.drawTextWithShadow(this.textRenderer, trim(store.title, 18), hero.x + 64, hero.y + 14, GOLD);
        context.drawText(this.textRenderer, "Owner: " + trim(store.ownerName, 14), hero.x + 64, hero.y + 28, TEXT, false);
        context.drawText(this.textRenderer, prettyDimension(store.dimensionId), hero.x + 64, hero.y + 40, SUBTEXT, false);
        context.drawText(this.textRenderer, coordsLabel(store), hero.x + 64, hero.y + 52, SUBTEXT, false);
        context.drawText(this.textRenderer, distanceLabel(store), hero.x + hero.w - 72, hero.y + 14, ACCENT, false);

        Rect inventory = detailInventoryRect(left, top);
        drawPanel(context, inventory);
        context.drawTextWithShadow(this.textRenderer, owned ? "Current inventory" : "Shop inventory", inventory.x + 10, inventory.y + 8, GOLD);

        List<DabloonStoreEntry> entries = store.entries;
        int maxPage = maxPage(entries.size(), ITEM_PAGE_SIZE);
        if (owned) {
            myStoreItemPage = MathHelper.clamp(myStoreItemPage, 0, maxPage);
        } else {
            findStoreItemPage = MathHelper.clamp(findStoreItemPage, 0, maxPage);
        }
        int page = owned ? myStoreItemPage : findStoreItemPage;

        if (entries.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, "This store has no items.", inventory.centerX(), inventory.y + 74, TEXT);
            context.drawCenteredTextWithShadow(this.textRenderer, owned ? "Add stock in-world and it will show here." : "Check back later for new stock.", inventory.centerX(), inventory.y + 88, SUBTEXT);
        } else {
            List<DabloonStoreEntry> visibleEntries = pageSlice(entries, page, ITEM_PAGE_SIZE);
            for (int i = 0; i < visibleEntries.size(); i++) {
                Rect row = entryRowRect(inventory, i);
                DabloonStoreEntry entry = visibleEntries.get(i);
                boolean hovered = row.contains(mouseX, mouseY);
                drawEntryRow(context, row, entry, hovered, owned);
            }

            Rect leftArrow = detailLeftArrowRect(left, top);
            Rect rightArrow = detailRightArrowRect(left, top);
            if (page > 0) {
                renderArrowButton(context, "detail_left_" + owned, leftArrow, "<", mouseX, mouseY);
            }
            if (page < maxPage) {
                renderArrowButton(context, "detail_right_" + owned, rightArrow, ">", mouseX, mouseY);
            }
            context.drawCenteredTextWithShadow(this.textRenderer, pageLabel(page, maxPage), inventory.centerX(), inventory.y + inventory.h - 16, SUBTEXT);
        }
    }

    private void renderSales(DrawContext context, int left, int top, int mouseX, int mouseY) {
        List<SaleRow> rows = flattenedSales();
        Rect panel = salesPanelRect(left, top);
        drawPanel(context, panel);
        context.drawTextWithShadow(this.textRenderer, "Store sales", panel.x + 10, panel.y + 8, GOLD);
        context.drawText(this.textRenderer, "All owned-store purchases in one feed.", panel.x + 10, panel.y + 20, SUBTEXT, false);

        int maxPage = maxPage(rows.size(), SALES_PAGE_SIZE);
        salesPage = MathHelper.clamp(salesPage, 0, maxPage);

        if (rows.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, "No sales yet.", panel.centerX(), panel.y + 74, TEXT);
            context.drawCenteredTextWithShadow(this.textRenderer, "Completed purchases will appear here.", panel.centerX(), panel.y + 88, SUBTEXT);
            return;
        }

        List<SaleRow> visible = pageSlice(rows, salesPage, SALES_PAGE_SIZE);
        SimpleDateFormat format = new SimpleDateFormat("dd MMM  HH:mm", Locale.ROOT);
        for (int i = 0; i < visible.size(); i++) {
            SaleRow row = visible.get(i);
            Rect saleRect = saleRowRect(panel, i);
            boolean hovered = saleRect.contains(mouseX, mouseY);
            drawSaleRow(context, saleRect, row, format, hovered);
        }

        Rect leftArrow = salesLeftArrowRect(left, top);
        Rect rightArrow = salesRightArrowRect(left, top);
        if (salesPage > 0) {
            renderArrowButton(context, "sales_left", leftArrow, "<", mouseX, mouseY);
        }
        if (salesPage < maxPage) {
            renderArrowButton(context, "sales_right", rightArrow, ">", mouseX, mouseY);
        }
        context.drawCenteredTextWithShadow(this.textRenderer, pageLabel(salesPage, maxPage), panel.centerX(), panel.y + panel.h - 16, SUBTEXT);
    }

    private void drawStoreCard(DrawContext context, Rect card, DabloonStoreSnapshot store, boolean owned, float hover) {
        int base = hover > 0.05f ? blend(PANEL_DARK, PANEL_HOVER, hover) : PANEL_DARK;
        context.fill(card.x, card.y, card.x + card.w, card.y + card.h, base);
        context.fill(card.x, card.y, card.x + card.w, card.y + 1, CARD_TOP);
        context.drawBorder(card.x, card.y, card.w, card.h, hover > 0.1f ? ACCENT : 0xB2000000);

        if (hover > 0.05f) {
            int glow = ((int) (hover * 38.0f) & 0xFF) << 24;
            context.fill(card.x - 2, card.y - 2, card.x + card.w + 2, card.y + card.h + 2, glow | 0x00C6DCFF);
        }

        renderLargePreview(context, store.hologramStack, card.x + (card.w / 2) - 8, card.y + 10, 1.30f + (hover * 0.18f));
        context.drawCenteredTextWithShadow(this.textRenderer, trim(store.title, 10), card.centerX(), card.y + 42, TEXT);

        if (owned) {
            context.drawCenteredTextWithShadow(this.textRenderer, store.entries.size() + " items", card.centerX(), card.y + 54, SUBTEXT);
            context.drawCenteredTextWithShadow(this.textRenderer, distanceLabel(store), card.centerX(), card.y + 64, ACCENT);
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer, trim("By " + store.ownerName, 11), card.centerX(), card.y + 52, SUBTEXT);
            context.drawCenteredTextWithShadow(this.textRenderer, trim(shortCoords(store), 12), card.centerX(), card.y + 62, ACCENT);
        }
    }

    private void drawEntryRow(DrawContext context, Rect row, DabloonStoreEntry entry, boolean hovered, boolean owned) {
        context.fill(row.x, row.y, row.x + row.w, row.y + row.h, hovered ? 0xD1222E40 : 0xB8182230);
        context.drawBorder(row.x, row.y, row.w, row.h, hovered ? ACCENT : 0x90000000);
        context.drawItem(entry.stock(), row.x + 6, row.y + 3);
        context.drawItemInSlot(this.textRenderer, entry.stock(), row.x + 6, row.y + 3);
        context.drawTextWithShadow(this.textRenderer, trim(entry.title(), 18), row.x + 28, row.y + 5, TEXT);
        context.drawText(this.textRenderer, owned ? "Stock: " + entry.stockCount() : "Price: " + entry.pricePerItem(), row.x + 28, row.y + 16, owned ? SUBTEXT : GOLD, false);
        if (owned) {
            context.drawText(this.textRenderer, "Price " + entry.pricePerItem(), row.x + row.w - 52, row.y + 16, GOLD, false);
        } else {
            context.drawText(this.textRenderer, "x" + entry.stockCount(), row.x + row.w - 22, row.y + 16, SUBTEXT, false);
        }
    }

    private void drawSaleRow(DrawContext context, Rect row, SaleRow saleRow, SimpleDateFormat format, boolean hovered) {
        context.fill(row.x, row.y, row.x + row.w, row.y + row.h, hovered ? 0xD1222E40 : 0xB8182230);
        context.drawBorder(row.x, row.y, row.w, row.h, hovered ? ACCENT : 0x90000000);

        ItemStack itemPreview = saleItemStack(saleRow.sale.itemId);
        context.drawItem(itemPreview, row.x + 8, row.y + 8);
        context.drawItemInSlot(this.textRenderer, itemPreview, row.x + 8, row.y + 8);

        renderLargePreview(context, saleRow.store.hologramStack, row.x + 34, row.y + 8, 0.95f);

        context.drawTextWithShadow(this.textRenderer, trim(saleRow.sale.itemName, 16), row.x + 58, row.y + 7, TEXT);
        context.drawText(this.textRenderer, trim(saleRow.store.title, 15), row.x + 58, row.y + 18, SUBTEXT, false);
        context.drawText(this.textRenderer, "+" + saleRow.sale.totalPrice, row.x + row.w - 34, row.y + 7, GOLD, false);
        context.drawText(this.textRenderer, format.format(new Date(saleRow.sale.soldAt)), row.x + row.w - 68, row.y + 18, MUTED, false);
    }

    private void renderLandingButton(DrawContext context,
                                     String key,
                                     Rect rect,
                                     Identifier texture,
                                     String title,
                                     String subtitle,
                                     ItemStack preview,
                                     int mouseX,
                                     int mouseY) {
        boolean hovered = rect.contains(mouseX, mouseY);
        float hover = hover(key, hovered);
        float time = (System.currentTimeMillis() / 85.0f);
        float bob = (float) Math.sin(time * 0.12f) * 0.015f;
        float pulse = (float) Math.sin(time * 0.08f) * 0.012f;
        float scale = 1.0f + bob + pulse + (hover * 0.12f);

        int cx = rect.centerX();
        int cy = rect.centerY();

        if (hover > 0.05f) {
            int glow = ((int) (hover * 85.0f) & 0xFF) << 24;
            context.fill(rect.x - 6, rect.y - 6, rect.x + rect.w + 6, rect.y + rect.h + 6, glow | 0x00C6DCFF);
        }

        context.getMatrices().push();
        context.getMatrices().translate(cx, cy, 0.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.getMatrices().translate(-cx, -cy, 0.0f);

        if (hasTexture(texture)) {
            context.drawTexture(texture, rect.x, rect.y, 0, 0, rect.w, rect.h, rect.w, rect.h);
        } else {
            context.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, hover > 0.08f ? PANEL_ACTIVE : PANEL_DARK);
            context.fill(rect.x, rect.y, rect.x + rect.w, rect.y + 1, CARD_TOP);
            context.drawBorder(rect.x, rect.y, rect.w, rect.h, hover > 0.08f ? ACCENT : 0xA0000000);
            renderLargePreview(context, preview, rect.x + (rect.w / 2) - 8, rect.y + 18, 1.65f);
        }

        context.getMatrices().pop();

        context.drawCenteredTextWithShadow(this.textRenderer, title, rect.centerX(), rect.y + rect.h + 10, TEXT);
        context.drawCenteredTextWithShadow(this.textRenderer, subtitle, rect.centerX(), rect.y + rect.h + 22, SUBTEXT);
    }

    private void renderIconButton(DrawContext context, String key, Rect rect, Identifier texture, int mouseX, int mouseY) {
        boolean hovered = rect.contains(mouseX, mouseY);
        float hover = hover(key, hovered);
        float time = (System.currentTimeMillis() / 90.0f);
        float bob = (float) Math.sin(time * 0.12f) * 0.020f;
        float pulse = (float) Math.sin(time * 0.07f) * 0.012f;
        float scale = 1.0f + bob + pulse + (hover * 0.18f);

        if (hover > 0.01f) {
            int glowAlpha = (int) (hover * 95.0f) & 0xFF;
            context.fill(rect.centerX() - 14, rect.centerY() - 14, rect.centerX() + 14, rect.centerY() + 14, (glowAlpha << 24) | 0x00C7DAFF);
        }

        context.getMatrices().push();
        context.getMatrices().translate(rect.centerX(), rect.centerY(), 0.0f);
        context.getMatrices().scale((rect.w / 24.0f) * scale, (rect.h / 24.0f) * scale, 1.0f);
        context.getMatrices().translate(-12.0f, -12.0f, 0.0f);
        context.drawTexture(texture, 0, 0, 0, 0, 24, 24, 24, 24);
        context.getMatrices().pop();
    }

    private void renderTextButton(DrawContext context, String key, Rect rect, String label, int mouseX, int mouseY, boolean accent) {
        boolean hovered = rect.contains(mouseX, mouseY);
        float hover = hover(key, hovered);
        int fill = accent ? blend(0xCC20324A, 0xEE31557A, hover) : blend(0xB71A2433, 0xDA28374C, hover);
        context.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, fill);
        context.fill(rect.x, rect.y, rect.x + rect.w, rect.y + 1, CARD_TOP);
        context.drawBorder(rect.x, rect.y, rect.w, rect.h, hovered ? ACCENT : 0xA0000000);
        context.drawCenteredTextWithShadow(this.textRenderer, label, rect.centerX(), rect.y + 5, accent ? GOLD : TEXT);
    }

    private void renderArrowButton(DrawContext context, String key, Rect rect, String label, int mouseX, int mouseY) {
        boolean hovered = rect.contains(mouseX, mouseY);
        float hover = hover(key, hovered);
        context.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, blend(0xB71A2433, 0xE0304660, hover));
        context.fill(rect.x, rect.y, rect.x + rect.w, rect.y + 1, CARD_TOP);
        context.drawBorder(rect.x, rect.y, rect.w, rect.h, hovered ? ACCENT : 0xA0000000);
        context.drawCenteredTextWithShadow(this.textRenderer, label, rect.centerX(), rect.y + 4, TEXT);
    }

    private void drawPanel(DrawContext context, Rect rect) {
        context.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, PANEL);
        context.fill(rect.x, rect.y, rect.x + rect.w, rect.y + 1, CARD_TOP);
        context.drawBorder(rect.x, rect.y, rect.w, rect.h, 0xAA000000);
    }

    private void renderLargePreview(DrawContext context, ItemStack stack, int x, int y, float scale) {
        ItemStack preview = stack.isEmpty() ? new ItemStack(Items.ENDER_CHEST) : stack;
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.drawItem(preview, 0, 0);
        context.drawItemInSlot(this.textRenderer, preview, 0, 0);
        context.getMatrices().pop();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int left = (this.width - DeviceHomeScreen.GUI_W) / 2;
        int top = (this.height - DeviceHomeScreen.GUI_H) / 2;

        if (homeRect(left, top).contains(mouseX, mouseY)) {
            if (this.client != null) {
                this.client.setScreen(new DeviceHomeScreen());
            }
            return true;
        }

        if (view != View.LANDING && backRect(left, top).contains(mouseX, mouseY)) {
            goBack();
            return true;
        }

        switch (view) {
            case LANDING -> {
                if (landingButtonRect(left, top, 0).contains(mouseX, mouseY)) {
                    view = View.MY_STORES;
                    myStoresPage = 0;
                    return true;
                }
                if (landingButtonRect(left, top, 1).contains(mouseX, mouseY)) {
                    view = View.FIND_STORES;
                    findStoresPage = 0;
                    return true;
                }
            }
            case MY_STORES -> {
                if (actionRect(left, top).contains(mouseX, mouseY)) {
                    salesPage = 0;
                    view = View.SALES;
                    return true;
                }

                List<DabloonStoreSnapshot> stores = sortedOwnedStores();
                int maxPage = maxPage(stores.size(), PAGE_SIZE);
                myStoresPage = MathHelper.clamp(myStoresPage, 0, maxPage);
                List<DabloonStoreSnapshot> visible = pageSlice(stores, myStoresPage, PAGE_SIZE);
                for (int i = 0; i < visible.size(); i++) {
                    if (gridCardRect(left, top, i).contains(mouseX, mouseY)) {
                        selectedOwnedStore = visible.get(i);
                        myStoreItemPage = 0;
                        view = View.MY_STORE_DETAIL;
                        return true;
                    }
                }
                if (myStoresPage > 0 && leftArrowRect(left, top).contains(mouseX, mouseY)) {
                    myStoresPage--;
                    return true;
                }
                if (myStoresPage < maxPage && rightArrowRect(left, top).contains(mouseX, mouseY)) {
                    myStoresPage++;
                    return true;
                }
            }
            case FIND_STORES -> {
                List<DabloonStoreSnapshot> stores = sortedDiscoverableStores();
                int maxPage = maxPage(stores.size(), PAGE_SIZE);
                findStoresPage = MathHelper.clamp(findStoresPage, 0, maxPage);
                List<DabloonStoreSnapshot> visible = pageSlice(stores, findStoresPage, PAGE_SIZE);
                for (int i = 0; i < visible.size(); i++) {
                    if (gridCardRect(left, top, i).contains(mouseX, mouseY)) {
                        selectedFoundStore = visible.get(i);
                        findStoreItemPage = 0;
                        view = View.FIND_STORE_DETAIL;
                        return true;
                    }
                }
                if (findStoresPage > 0 && leftArrowRect(left, top).contains(mouseX, mouseY)) {
                    findStoresPage--;
                    return true;
                }
                if (findStoresPage < maxPage && rightArrowRect(left, top).contains(mouseX, mouseY)) {
                    findStoresPage++;
                    return true;
                }
            }
            case MY_STORE_DETAIL -> {
                int maxPage = maxPage(selectedOwnedStore == null ? 0 : selectedOwnedStore.entries.size(), ITEM_PAGE_SIZE);
                if (myStoreItemPage > 0 && detailLeftArrowRect(left, top).contains(mouseX, mouseY)) {
                    myStoreItemPage--;
                    return true;
                }
                if (myStoreItemPage < maxPage && detailRightArrowRect(left, top).contains(mouseX, mouseY)) {
                    myStoreItemPage++;
                    return true;
                }
            }
            case FIND_STORE_DETAIL -> {
                int maxPage = maxPage(selectedFoundStore == null ? 0 : selectedFoundStore.entries.size(), ITEM_PAGE_SIZE);
                if (findStoreItemPage > 0 && detailLeftArrowRect(left, top).contains(mouseX, mouseY)) {
                    findStoreItemPage--;
                    return true;
                }
                if (findStoreItemPage < maxPage && detailRightArrowRect(left, top).contains(mouseX, mouseY)) {
                    findStoreItemPage++;
                    return true;
                }
            }
            case SALES -> {
                int maxPage = maxPage(flattenedSales().size(), SALES_PAGE_SIZE);
                if (salesPage > 0 && salesLeftArrowRect(left, top).contains(mouseX, mouseY)) {
                    salesPage--;
                    return true;
                }
                if (salesPage < maxPage && salesRightArrowRect(left, top).contains(mouseX, mouseY)) {
                    salesPage++;
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void goBack() {
        switch (view) {
            case MY_STORES, FIND_STORES -> view = View.LANDING;
            case MY_STORE_DETAIL -> view = View.MY_STORES;
            case FIND_STORE_DETAIL -> view = View.FIND_STORES;
            case SALES -> view = View.MY_STORES;
            default -> view = View.LANDING;
        }
    }

    private float hover(String key, boolean hovered) {
        float current = hoverProgress.getOrDefault(key, 0.0f);
        float next = MathHelper.lerp(0.24f, current, hovered ? 1.0f : 0.0f);
        hoverProgress.put(key, next);
        return next;
    }

    private boolean hasTexture(Identifier id) {
        if (this.client == null) {
            return false;
        }
        Optional<Resource> resource = this.client.getResourceManager().getResource(id);
        return resource.isPresent();
    }

    private List<DabloonStoreSnapshot> sortedOwnedStores() {
        return sortedStores(DabloonStoreClientState.ownedStores());
    }

    private List<DabloonStoreSnapshot> sortedDiscoverableStores() {
        return sortedStores(DabloonStoreClientState.discoverableStores());
    }

    private List<DabloonStoreSnapshot> sortedStores(List<DabloonStoreSnapshot> input) {
        List<DabloonStoreSnapshot> stores = new ArrayList<>(input);
        stores.sort(Comparator
                .comparingDouble(this::distanceSortValue)
                .thenComparing(snapshot -> snapshot.title, String.CASE_INSENSITIVE_ORDER));
        return stores;
    }

    private double distanceSortValue(DabloonStoreSnapshot snapshot) {
        if (this.client == null || this.client.player == null || this.client.player.getWorld() == null) {
            return 0.0;
        }

        String playerDim = this.client.player.getWorld().getRegistryKey().getValue().toString();
        if (!playerDim.equals(snapshot.dimensionId)) {
            return 1_000_000_000_000.0 + snapshot.pos.getSquaredDistance(this.client.player.getBlockPos());
        }
        return snapshot.pos.getSquaredDistance(this.client.player.getX(), this.client.player.getY(), this.client.player.getZ());
    }

    private String distanceLabel(DabloonStoreSnapshot snapshot) {
        if (this.client == null || this.client.player == null || this.client.player.getWorld() == null) {
            return prettyDimension(snapshot.dimensionId);
        }
        String playerDim = this.client.player.getWorld().getRegistryKey().getValue().toString();
        if (!playerDim.equals(snapshot.dimensionId)) {
            return prettyDimension(snapshot.dimensionId);
        }
        double distance = Math.sqrt(snapshot.pos.getSquaredDistance(this.client.player.getX(), this.client.player.getY(), this.client.player.getZ()));
        return Math.max(1, Math.round(distance)) + "m";
    }

    private String shortCoords(DabloonStoreSnapshot snapshot) {
        return "X" + snapshot.pos.getX() + " Z" + snapshot.pos.getZ();
    }

    private String coordsLabel(DabloonStoreSnapshot snapshot) {
        return "X " + snapshot.pos.getX() + "  Y " + snapshot.pos.getY() + "  Z " + snapshot.pos.getZ();
    }

    private String prettyDimension(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return "Unknown";
        }
        String path = dimensionId.contains(":") ? dimensionId.substring(dimensionId.indexOf(':') + 1) : dimensionId;
        String[] parts = path.split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.isEmpty() ? dimensionId : out.toString();
    }

    private ItemStack previewStackForLanding(boolean owned) {
        List<DabloonStoreSnapshot> stores = owned ? DabloonStoreClientState.ownedStores() : DabloonStoreClientState.discoverableStores();
        if (!stores.isEmpty() && !stores.get(0).hologramStack.isEmpty()) {
            return stores.get(0).hologramStack.copy();
        }
        return new ItemStack(owned ? Items.ENDER_CHEST : Items.COMPASS);
    }

    private ItemStack saleItemStack(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) {
            return new ItemStack(Items.PAPER);
        }
        Optional<Item> item = Registries.ITEM.getOrEmpty(id);
        return item.map(ItemStack::new).orElseGet(() -> new ItemStack(Items.PAPER));
    }

    private List<SaleRow> flattenedSales() {
        List<SaleRow> rows = new ArrayList<>();
        for (DabloonStoreSnapshot snapshot : sortedOwnedStores()) {
            for (DabloonStoreSale sale : snapshot.sales) {
                rows.add(new SaleRow(snapshot, sale));
            }
        }
        rows.sort((a, b) -> Long.compare(b.sale.soldAt, a.sale.soldAt));
        return rows;
    }

    private int maxPage(int totalItems, int pageSize) {
        if (totalItems <= 0) {
            return 0;
        }
        return (totalItems - 1) / pageSize;
    }

    private <T> List<T> pageSlice(List<T> input, int page, int pageSize) {
        int from = Math.min(input.size(), Math.max(0, page) * pageSize);
        int to = Math.min(input.size(), from + pageSize);
        return input.subList(from, to);
    }

    private int blend(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF;
        int ar = (a >>> 16) & 0xFF;
        int ag = (a >>> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF;
        int br = (b >>> 16) & 0xFF;
        int bg = (b >>> 8) & 0xFF;
        int bb = b & 0xFF;
        int oa = (int) MathHelper.lerp(t, aa, ba);
        int or = (int) MathHelper.lerp(t, ar, br);
        int og = (int) MathHelper.lerp(t, ag, bg);
        int ob = (int) MathHelper.lerp(t, ab, bb);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }

    private String trim(String text, int chars) {
        return this.textRenderer.trimToWidth(text == null ? "" : text, chars * 6);
    }

    private String pageLabel(int page, int maxPage) {
        return (page + 1) + " / " + (maxPage + 1);
    }

    private Rect homeRect(int left, int top) {
        return new Rect(left + 18, top + 14, TOP_ICON_SIZE, TOP_ICON_SIZE);
    }

    private Rect backRect(int left, int top) {
        return new Rect(left + 48, top + 12, 40, 18);
    }

    private Rect actionRect(int left, int top) {
        return new Rect(left + DeviceHomeScreen.GUI_W - 58, top + 12, 40, 18);
    }

    private Rect landingButtonRect(int left, int top, int index) {
        int w = 78;
        int h = 78;
        int gap = 20;
        int total = (w * 2) + gap;
        int startX = left + (DeviceHomeScreen.GUI_W - total) / 2;
        return new Rect(startX + index * (w + gap), top + 108, w, h);
    }

    private Rect contentHeaderRect(int left, int top) {
        return new Rect(left + 18, top + 44, DeviceHomeScreen.GUI_W - 36, 30);
    }

    private Rect emptyPanelRect(int left, int top) {
        return new Rect(left + 18, top + 82, DeviceHomeScreen.GUI_W - 36, 176);
    }

    private Rect gridCardRect(int left, int top, int index) {
        int col = index % 3;
        int row = index / 3;
        int startX = left + 18;
        int startY = top + 82;
        int gapX = 6;
        int gapY = 4;
        int w = 64;
        int h = 70;
        return new Rect(startX + col * (w + gapX), startY + row * (h + gapY), w, h);
    }

    private Rect leftArrowRect(int left, int top) {
        return new Rect(left + 18, top + 282, 22, 18);
    }

    private Rect rightArrowRect(int left, int top) {
        return new Rect(left + DeviceHomeScreen.GUI_W - 40, top + 282, 22, 18);
    }

    private Rect detailHeroRect(int left, int top) {
        return new Rect(left + 18, top + 48, DeviceHomeScreen.GUI_W - 36, 82);
    }

    private Rect detailInventoryRect(int left, int top) {
        return new Rect(left + 18, top + 138, DeviceHomeScreen.GUI_W - 36, 150);
    }

    private Rect entryRowRect(Rect panel, int index) {
        return new Rect(panel.x + 8, panel.y + 28 + index * 18, panel.w - 16, 24);
    }

    private Rect detailLeftArrowRect(int left, int top) {
        Rect panel = detailInventoryRect(left, top);
        return new Rect(panel.x + 8, panel.y + panel.h - 22, 22, 16);
    }

    private Rect detailRightArrowRect(int left, int top) {
        Rect panel = detailInventoryRect(left, top);
        return new Rect(panel.x + panel.w - 30, panel.y + panel.h - 22, 22, 16);
    }

    private Rect salesPanelRect(int left, int top) {
        return new Rect(left + 18, top + 48, DeviceHomeScreen.GUI_W - 36, 240);
    }

    private Rect saleRowRect(Rect panel, int index) {
        return new Rect(panel.x + 8, panel.y + 32 + index * 30, panel.w - 16, 26);
    }

    private Rect salesLeftArrowRect(int left, int top) {
        Rect panel = salesPanelRect(left, top);
        return new Rect(panel.x + 8, panel.y + panel.h - 22, 22, 16);
    }

    private Rect salesRightArrowRect(int left, int top) {
        Rect panel = salesPanelRect(left, top);
        return new Rect(panel.x + panel.w - 30, panel.y + panel.h - 22, 22, 16);
    }
}
