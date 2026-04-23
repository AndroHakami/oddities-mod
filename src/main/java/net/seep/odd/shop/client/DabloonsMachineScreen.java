package net.seep.odd.shop.client;

import com.google.common.collect.Multimap;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.Oddities;
import net.seep.odd.shop.ShopNetworking;
import net.seep.odd.shop.catalog.ShopEntry;
import org.joml.Quaternionf;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DabloonsMachineScreen extends HandledScreen<ScreenHandler> {

    private enum ViewMode {
        ROOT,
        CATEGORY,
        CONFIRM
    }

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < (x + w) && my >= y && my < (y + h);
        }
    }

    private record WeaponStats(double attackDamage, double attackSpeed, boolean hasStats) {
    }

    private static final Identifier DABLOON_ICON = new Identifier(Oddities.MOD_ID, "textures/item/dabloon.png");
    private static final int DABLOON_TEX_W = 32;
    private static final int DABLOON_TEX_H = 32;

    private static final int ROOT_CARD_W = 320;
    private static final int ROOT_CARD_H = 50;
    private static final int ROOT_CARD_GAP = 4;
    private static final int ROOT_CARD_PADDING = 0;

    private static final float ROOT_BANNER_SCALE = 0.80f;
    private static final float ROOT_BANNER_HOVER_POP = 0.03f;

    private static final int GRID_COLUMNS = 4;
    private static final int GRID_ROWS = 3;
    private static final int ITEMS_PER_PAGE = GRID_COLUMNS * GRID_ROWS;
    private static final int SLOT_W = 76;
    private static final int SLOT_H = 68;
    private static final int SLOT_GAP_X = 13;
    private static final int SLOT_GAP_Y = 12;

    private static final int BOTTOM_BAR_H = 26;
    private static final int ROOT_BUTTON_TEX_W = 1000;
    private static final int ROOT_BUTTON_TEX_H = 168;
    private static final int CATEGORY_BG_TEX_W = 1504;
    private static final int CATEGORY_BG_TEX_H = 856;

    private static final Map<ShopEntry.Category, Identifier> CATEGORY_BUTTONS = new EnumMap<>(ShopEntry.Category.class);
    private static final Map<ShopEntry.Category, Identifier> CATEGORY_BACKGROUNDS = new EnumMap<>(ShopEntry.Category.class);
    private static final Map<ShopEntry.Category, Identifier> CATEGORY_GIFS = new EnumMap<>(ShopEntry.Category.class);

    static {
        CATEGORY_BUTTONS.put(ShopEntry.Category.WEAPONS, new Identifier(Oddities.MOD_ID, "textures/gui/shop/root/weapons_button.png"));
        CATEGORY_BUTTONS.put(ShopEntry.Category.PETS, new Identifier(Oddities.MOD_ID, "textures/gui/shop/root/pets_button.png"));
        CATEGORY_BUTTONS.put(ShopEntry.Category.STYLES, new Identifier(Oddities.MOD_ID, "textures/gui/shop/root/styles_button.png"));
        CATEGORY_BUTTONS.put(ShopEntry.Category.MISC, new Identifier(Oddities.MOD_ID, "textures/gui/shop/root/misc_button.png"));

        CATEGORY_BACKGROUNDS.put(ShopEntry.Category.WEAPONS, new Identifier(Oddities.MOD_ID, "textures/gui/shop/backgrounds/weapons.png"));
        CATEGORY_BACKGROUNDS.put(ShopEntry.Category.PETS, new Identifier(Oddities.MOD_ID, "textures/gui/shop/backgrounds/pets.png"));
        CATEGORY_BACKGROUNDS.put(ShopEntry.Category.STYLES, new Identifier(Oddities.MOD_ID, "textures/gui/shop/backgrounds/styles.png"));
        CATEGORY_BACKGROUNDS.put(ShopEntry.Category.MISC, new Identifier(Oddities.MOD_ID, "textures/gui/shop/backgrounds/misc.png"));

        CATEGORY_GIFS.put(ShopEntry.Category.WEAPONS, new Identifier(Oddities.MOD_ID, "shop/weapons_bg"));
        CATEGORY_GIFS.put(ShopEntry.Category.PETS, new Identifier(Oddities.MOD_ID, "shop/pets_bg"));
        CATEGORY_GIFS.put(ShopEntry.Category.STYLES, new Identifier(Oddities.MOD_ID, "shop/styles_bg"));
        CATEGORY_GIFS.put(ShopEntry.Category.MISC, new Identifier(Oddities.MOD_ID, "shop/misc_bg"));
    }

    private static Method gifRenderMethod;
    private static boolean gifLookupAttempted = false;

    private final Map<String, Entity> entityPreviewCache = new HashMap<>();
    private final Map<String, Float> hoverLerps = new HashMap<>();
    private final Map<ShopEntry.Category, Float> rootHoverLerps = new EnumMap<>(ShopEntry.Category.class);

    private ShopLoopingSoundInstance music;

    private ViewMode viewMode = ViewMode.ROOT;
    private ShopEntry.Category currentCategory = ShopEntry.Category.WEAPONS;
    private ShopEntry confirmEntry;
    private int page = 0;

    private TextFieldWidget petNameField;
    private String pendingPetName = "";

    private float confirmAnim = 0.0f;

    private float stylePreviewYaw = 16.0f;
    private float stylePreviewPitch = -6.0f;
    private boolean stylePreviewDragging = false;
    private double lastStyleDragX = 0.0;
    private double lastStyleDragY = 0.0;

    public DabloonsMachineScreen(ScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 392;
        this.backgroundHeight = 278;
    }

    @Override
    protected void init() {
        super.init();
        this.titleX = 0;
        this.titleY = 0;

        this.petNameField = new TextFieldWidget(this.textRenderer, 0, 0, 86, 18, Text.literal("Pet name"));
        this.petNameField.setMaxLength(32);
        this.petNameField.setVisible(false);
        this.addDrawableChild(this.petNameField);

        var soundId = new Identifier(Oddities.MOD_ID, "shop_music");
        if (Registries.SOUND_EVENT.containsId(soundId)) {
            SoundEvent evt = Registries.SOUND_EVENT.get(soundId);
            music = new ShopLoopingSoundInstance(evt);
            SoundManager sm = MinecraftClient.getInstance().getSoundManager();
            sm.play(music);
        }
    }

    @Override
    public void close() {
        super.close();
        if (music != null) {
            music.stopNow();
            MinecraftClient.getInstance().getSoundManager().stop(music);
            music = null;
        }
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();

        if (petNameField != null && petNameField.isVisible()) {
            petNameField.tick();
        }

        ClientShopState.PurchaseResult purchaseResult = ClientShopState.consumePurchaseResult();
        if (purchaseResult != null) {
            this.viewMode = ViewMode.CATEGORY;
            this.confirmEntry = null;
            this.confirmAnim = 0.0f;
            this.pendingPetName = "";
            this.stylePreviewYaw = 16.0f;
            this.stylePreviewPitch = -6.0f;
            this.stylePreviewDragging = false;
            hidePetNameField();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (viewMode != ViewMode.CATEGORY) return false;

        List<ShopEntry> entries = ClientShopState.entriesFor(currentCategory);
        int pageCount = Math.max(1, (int) Math.ceil(entries.size() / (double) ITEMS_PER_PAGE));
        if (pageCount <= 1) return false;

        if (amount < 0 && this.page < pageCount - 1) {
            this.page++;
            return true;
        }

        if (amount > 0 && this.page > 0) {
            this.page--;
            return true;
        }

        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        tickAnimations();
        syncPetNameField();

        renderBackground(context);
        drawBackdrop(context, delta);
        drawHeader(context);

        if (viewMode == ViewMode.ROOT) {
            drawRootMenu(context, mouseX, mouseY, delta);
        } else if (viewMode == ViewMode.CATEGORY) {
            drawCategoryView(context, mouseX, mouseY, delta);
        } else if (viewMode == ViewMode.CONFIRM) {
            drawConfirmScreen(context, mouseX, mouseY, delta);
        }

        drawBottomBalanceBar(context, mouseX, mouseY);
        drawToast(context);
        drawTooltips(context, mouseX, mouseY);

        if (isPetConfirmOpen() && petNameField != null) {
            petNameField.render(context, mouseX, mouseY, delta);
        }
    }

    private void tickAnimations() {
        this.confirmAnim = MathHelper.lerp(0.16f, this.confirmAnim, this.viewMode == ViewMode.CONFIRM ? 1.0f : 0.0f);
    }

    private void syncPetNameField() {
        if (petNameField == null) return;

        if (isPetConfirmOpen()) {
            Rect field = petNameFieldRect();
            petNameField.setX(field.x);
            petNameField.setY(field.y);
            petNameField.setVisible(true);
            petNameField.setEditable(true);
        } else {
            hidePetNameField();
        }
    }

    private void hidePetNameField() {
        if (petNameField == null) return;
        petNameField.setVisible(false);
        petNameField.setFocused(false);
    }

    private boolean isPetConfirmOpen() {
        return viewMode == ViewMode.CONFIRM
                && confirmEntry != null
                && currentCategory == ShopEntry.Category.PETS;
    }

    private void drawBackdrop(DrawContext context, float delta) {
        int left = this.x;
        int top = this.y;
        int right = left + this.backgroundWidth;
        int bottom = top + this.backgroundHeight;

        context.fill(left, top, right, bottom, 0xE5121212);
        context.fill(left + 1, top + 1, right - 1, bottom - 1, 0xEE1A1A1A);
        context.drawBorder(left, top, this.backgroundWidth, this.backgroundHeight, 0xFF000000);

        int contentX = left + 8;
        int contentY = top + 30;
        int contentW = this.backgroundWidth - 16;
        int contentH = this.backgroundHeight - 30 - BOTTOM_BAR_H - 8;

        if (viewMode != ViewMode.ROOT) {
            if (!tryRenderGifBackground(context, CATEGORY_GIFS.get(currentCategory), contentX, contentY, contentW, contentH, delta)) {
                drawOptionalTexture(context, CATEGORY_BACKGROUNDS.get(currentCategory), contentX, contentY, contentW, contentH);
            }
            context.fill(contentX, contentY, contentX + contentW, contentY + contentH, 0x5A0A0A0A);
        } else {
            context.fill(contentX, contentY, contentX + contentW, contentY + contentH, 0x14080808);
        }
    }

    private void drawHeader(DrawContext context) {
        int headerX = this.x + 10;
        int headerY = this.y + 8;
        context.drawText(this.textRenderer, Text.literal("DABLOON SHOP"), headerX, headerY, 0xFFF7E6A4, true);

        if (viewMode != ViewMode.ROOT) {
            String cat = switch (currentCategory) {
                case WEAPONS -> "WEAPONS";
                case PETS -> "PETS";
                case STYLES -> "STYLES";
                case MISC -> "MISC";
            };
            int labelWidth = this.textRenderer.getWidth(cat);
            context.drawText(this.textRenderer, cat, this.x + this.backgroundWidth - 12 - labelWidth, headerY, 0xFFFFFFFF, true);
        }
    }

    private void drawRootMenu(DrawContext context, int mouseX, int mouseY, float delta) {
        ShopEntry.Category[] categories = {
                ShopEntry.Category.WEAPONS,
                ShopEntry.Category.PETS,
                ShopEntry.Category.STYLES,
                ShopEntry.Category.MISC
        };

        float time = getTimeTicks(delta);

        for (int i = 0; i < categories.length; i++) {
            ShopEntry.Category category = categories[i];
            Rect rect = rootButtonRect(i);
            boolean hovered = rect.contains(mouseX, mouseY);

            float hover = rootHoverLerps.getOrDefault(category, 0f);
            hover = MathHelper.lerp(0.18f, hover, hovered ? 1f : 0f);
            rootHoverLerps.put(category, hover);

            drawCategoryButton(context, rect, category, hover, time);
        }
    }

    private void drawCategoryButton(DrawContext context, Rect rect, ShopEntry.Category category, float hover, float time) {
        Identifier tex = CATEGORY_BUTTONS.get(category);
        if (drawRootBannerTexture(context, tex, rect, hover, time)) {
            return;
        }

        String label = switch (category) {
            case WEAPONS -> "WEAPONS";
            case PETS -> "PETS";
            case STYLES -> "STYLES";
            case MISC -> "OTHER";
        };
        int color = hover > 0.5f ? 0xFFFFFFFF : 0xFFECECEC;
        context.drawCenteredTextWithShadow(this.textRenderer, label, rect.x + rect.w / 2, rect.y + rect.h / 2 - 4, color);
    }

    private void drawCategoryView(DrawContext context, int mouseX, int mouseY, float delta) {
        Rect home = homeButtonRect();
        Rect prev = prevPageRect();
        Rect next = nextPageRect();

        drawSmallButton(context, home, "HOME", home.contains(mouseX, mouseY), 1.0f);

        List<ShopEntry> entries = ClientShopState.entriesFor(currentCategory);
        int pageCount = Math.max(1, (int) Math.ceil(entries.size() / (double) ITEMS_PER_PAGE));
        this.page = MathHelper.clamp(this.page, 0, pageCount - 1);

        if (pageCount > 1) {
            drawSmallButton(context, prev, "<", prev.contains(mouseX, mouseY), 1.0f);
            drawSmallButton(context, next, ">", next.contains(mouseX, mouseY), 1.0f);
            String pageText = "PAGE " + (page + 1) + "/" + pageCount;
            context.drawCenteredTextWithShadow(this.textRenderer, pageText, this.x + this.backgroundWidth / 2, this.y + 36, 0xFFFFFFFF);
        }

        if (entries.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, "No items in this store yet.", this.x + this.backgroundWidth / 2, this.y + 124, 0xFFFFFFFF);
            return;
        }

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(entries.size(), start + ITEMS_PER_PAGE);
        float time = getTimeTicks(delta);

        for (int i = start; i < end; i++) {
            int local = i - start;
            Rect rect = slotRect(local);
            ShopEntry entry = entries.get(i);
            boolean hovered = rect.contains(mouseX, mouseY);
            drawShopEntry(context, rect, entry, hovered, time);
        }
    }

    private void drawShopEntry(DrawContext context, Rect rect, ShopEntry entry, boolean hovered, float time) {
        float hover = hoverLerps.getOrDefault(entry.id, 0f);
        hover = MathHelper.lerp(0.18f, hover, hovered ? 1f : 0f);
        hoverLerps.put(entry.id, hover);

        int lift = Math.round(hover * 2.0f);
        int fill = hovered ? 0xD7383838 : 0xBF1D1D1D;
        int drawY = rect.y - lift;
        context.fill(rect.x, drawY, rect.x + rect.w, drawY + rect.h, fill);
        context.drawBorder(rect.x, drawY, rect.w, rect.h, hovered ? 0xFFF7E6A4 : 0xFF000000);

        float previewScale = 0.85f + hover * 0.12f;
        Rect previewRect = slotPreviewRect(rect, lift, entry);
        int previewCenterX = previewRect.x + previewRect.w / 2;
        int previewCenterY = previewRect.y + previewRect.h / 2;

        context.enableScissor(previewRect.x, previewRect.y, previewRect.x + previewRect.w, previewRect.y + previewRect.h);
        if (entry.previewType == ShopEntry.PreviewType.ENTITY && entry.previewEntityType != null && !entry.previewEntityType.isBlank()) {
            drawCardEntityPreview(context, entry.previewEntityType, previewRect, time, hover, previewScale);
        } else if (entry.previewType == ShopEntry.PreviewType.ARMOUR) {
            drawArmourPreview(
                    context,
                    entry,
                    createPreviewStack(entry),
                    previewCenterX,
                    previewRect.y + previewRect.h + 4,
                    Math.max(15, Math.round(18.0f * previewScale)),
                    time,
                    hover * 0.25f
            );
        } else {
            drawSpinningItem(context, getPreviewItemId(entry), previewCenterX, previewCenterY + 1, previewScale * 0.92f, time, hover * 0.25f);
        }
        context.disableScissor();

        drawCenteredTrimmedText(context, this.textRenderer, entry.displayName, rect.x + rect.w / 2, drawY + rect.h - 21, rect.w - 8, 0xFFFFFFFF);
        drawPrice(context, rect.x + 8, drawY + rect.h - 11, entry.price, false, 1.0f);
    }

    private Rect slotPreviewRect(Rect rect, int lift, ShopEntry entry) {
        int drawY = rect.y - lift;

        if (entry.previewType == ShopEntry.PreviewType.ENTITY) {
            return new Rect(rect.x + 4, drawY + 4, rect.w - 8, 38);
        }
        if (entry.previewType == ShopEntry.PreviewType.ARMOUR) {
            return new Rect(rect.x + 7, drawY + 4, rect.w - 14, 33);
        }
        return new Rect(rect.x + 10, drawY + 7, rect.w - 20, 26);
    }

    private void drawCardEntityPreview(DrawContext context, String entityTypeId, Rect previewRect, float time, float hover, float previewScale) {
        Entity ent = getOrCreatePreviewEntity(entityTypeId);
        if (!(ent instanceof LivingEntity living)) {
            return;
        }

        float entityHeight = Math.max(0.35f, living.getHeight());
        float entityWidth = Math.max(0.35f, living.getWidth());

        float heightFit = (previewRect.h - 4) / entityHeight;
        float widthFit = (previewRect.w - 6) / Math.max(entityWidth * 1.55f, entityWidth + 0.15f);
        int renderSize = MathHelper.clamp(
                Math.round(Math.min(heightFit, widthFit) * (0.96f + hover * 0.04f) * previewScale),
                16,
                44
        );

        int renderY = previewRect.y + previewRect.h - 2;
        if (entityHeight < 0.8f) {
            renderY -= 1;
        }
        if (entityWidth > entityHeight * 1.25f) {
            renderY -= 2;
        }

        float fakeMouseX = (float) (Math.sin(time * 0.020f) * 9.0f) + hover * 1.8f;
        float fakeMouseY = (float) (Math.cos(time * 0.017f) * 1.2f);
        InventoryScreen.drawEntity(context, previewRect.x + previewRect.w / 2, renderY, renderSize, fakeMouseX, fakeMouseY, living);
    }

    private void drawConfirmScreen(DrawContext context, int mouseX, int mouseY, float delta) {
        int contentX = this.x + 8;
        int contentY = this.y + 30;
        int contentW = this.backgroundWidth - 16;
        int contentH = this.backgroundHeight - 30 - BOTTOM_BAR_H - 8;

        float anim = easeOutCubic(this.confirmAnim);
        context.fill(contentX, contentY, contentX + contentW, contentY + contentH, applyAlpha(0xD8000000, anim));

        if (confirmEntry != null) {
            drawConfirmModal(context, mouseX, mouseY, delta, confirmEntry);
        }
    }

    private Rect confirmModalRect() {
        return new Rect(this.x + (this.backgroundWidth - 308) / 2, this.y + 38, 308, 182);
    }

    private Rect petNameFieldRect() {
        Rect modal = confirmModalRect();
        return new Rect(modal.x + 178, modal.y + 86, 108, 18);
    }

    private Rect stylePreviewBoxRect() {
        return stylePreviewBoxRect(confirmModalRect());
    }

    private Rect stylePreviewBoxRect(Rect modal) {
        return new Rect(modal.x + (modal.w - 132) / 2, modal.y + 28, 132, 84);
    }

    private boolean isStylePreviewActive() {
        return viewMode == ViewMode.CONFIRM
                && confirmEntry != null
                && currentCategory == ShopEntry.Category.STYLES
                && confirmEntry.previewType == ShopEntry.PreviewType.ARMOUR;
    }

    private void drawConfirmModal(DrawContext context, int mouseX, int mouseY, float delta, ShopEntry entry) {
        Rect modal = confirmModalRect();
        float anim = easeOutCubic(this.confirmAnim);
        float scale = 0.95f + 0.05f * anim;
        float offsetY = (1.0f - anim) * 7.0f;

        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(modal.x + modal.w / 2f, modal.y + modal.h / 2f + offsetY, 0.0f);
        matrices.scale(scale, scale, 1.0f);
        matrices.translate(-(modal.x + modal.w / 2f), -(modal.y + modal.h / 2f), 0.0f);

        int left = modal.x;
        int top = modal.y;
        int right = left + modal.w;
        int bottom = top + modal.h;

        context.fill(left - 4, top - 4, right + 4, bottom + 4, applyAlpha(0xCC000000, anim));
        context.fill(left, top, right, bottom, applyAlpha(0xF1141414, anim));
        context.drawBorder(left, top, modal.w, modal.h, applyAlpha(0xFFF7E6A4, anim));

        String title = this.textRenderer.trimToWidth(entry.displayName, modal.w - 20);
        context.drawCenteredTextWithShadow(this.textRenderer, title, left + modal.w / 2, top + 10, applyAlpha(0xFFFFFFFF, anim));

        switch (currentCategory) {
            case WEAPONS -> drawWeaponConfirmContent(context, modal, entry, delta, anim);
            case PETS -> drawPetConfirmContent(context, modal, entry, delta, anim);
            case STYLES -> drawStyleConfirmContent(context, modal, entry, delta, anim);
            case MISC -> drawMiscConfirmContent(context, modal, entry, delta, anim);
        }

        drawSmallButton(context, confirmBuyRect(), "BUY", confirmBuyRect().contains(mouseX, mouseY), anim);
        drawSmallButton(context, confirmCancelRect(), "CANCEL", confirmCancelRect().contains(mouseX, mouseY), anim);

        matrices.pop();
    }

    private void drawWeaponConfirmContent(DrawContext context, Rect modal, ShopEntry entry, float delta, float alphaFactor) {
        int left = modal.x;
        int top = modal.y;
        float time = getTimeTicks(delta);

        drawSpinningItem(context, getPreviewItemId(entry), left + 84, top + 64, 2.95f, time, 0.12f);

        context.fill(left + 20, top + 28, left + 150, top + 88, applyAlpha(0x22000000, alphaFactor));
        context.drawBorder(left + 20, top + 28, 130, 60, applyAlpha(0x332A2A2A, alphaFactor));

        List<String> descLines = wrapText(entry.description, 134, 4);
        if (descLines.isEmpty()) descLines = List.of("No description set.");
        int descY = top + 96;
        for (String line : descLines) {
            int lineW = this.textRenderer.getWidth(line);
            int drawX = left + 18 + (136 - lineW) / 2;
            context.drawText(this.textRenderer, line, drawX, descY, applyAlpha(0xFFD8D8D8, alphaFactor), false);
            descY += 10;
        }

        WeaponStats stats = readWeaponStats(createPreviewStack(entry));
        int statsX = left + 172;
        int statsY = top + 34;
        int statsW = 112;
        int statsH = 72;

        context.fill(statsX, statsY, statsX + statsW, statsY + statsH, applyAlpha(0x36000000, alphaFactor));
        context.drawBorder(statsX, statsY, statsW, statsH, applyAlpha(0x55303030, alphaFactor));
        context.drawCenteredTextWithShadow(this.textRenderer, "STATS", statsX + statsW / 2, statsY + 6, applyAlpha(0xFFF7E6A4, alphaFactor));

        drawStatRow(context, statsX + 8, statsY + 22, new ItemStack(Items.IRON_SWORD), "Damage", stats.attackDamage(), 0.0, 16.0, alphaFactor);
        drawStatRow(context, statsX + 8, statsY + 46, new ItemStack(Items.CLOCK), "Speed", stats.attackSpeed(), 0.0, 4.0, alphaFactor);

        context.drawCenteredTextWithShadow(this.textRenderer, "Confirm purchase?", left + modal.w / 2, top + 136, applyAlpha(0xFFECECEC, alphaFactor));
        drawPrice(context, left + modal.w / 2, top + 149, entry.price, true, alphaFactor);
    }

    private void drawPetConfirmContent(DrawContext context, Rect modal, ShopEntry entry, float delta, float alphaFactor) {
        int left = modal.x;
        int top = modal.y;
        float time = getTimeTicks(delta);

        if (entry.previewType == ShopEntry.PreviewType.ENTITY && entry.previewEntityType != null && !entry.previewEntityType.isBlank()) {
            drawSpinningEntity(context, entry.previewEntityType, left + 84, top + 88, 48, time, 0.08f);
        } else {
            drawSpinningItem(context, getPreviewItemId(entry), left + 84, top + 66, 2.25f, time, 0.12f);
        }

        List<String> descLines = wrapText(entry.description, 136, 4);
        if (descLines.isEmpty()) descLines = List.of("No description set.");
        int descY = top + 104;
        for (String line : descLines) {
            int lineW = this.textRenderer.getWidth(line);
            int drawX = left + 18 + (136 - lineW) / 2;
            context.drawText(this.textRenderer, line, drawX, descY, applyAlpha(0xFFD8D8D8, alphaFactor), false);
            descY += 10;
        }

        int boxX = left + 176;
        int boxY = top + 42;
        int boxW = 108;
        int boxH = 70;
        context.fill(boxX, boxY, boxX + boxW, boxY + boxH, applyAlpha(0x36000000, alphaFactor));
        context.drawBorder(boxX, boxY, boxW, boxH, applyAlpha(0x55303030, alphaFactor));

        context.drawCenteredTextWithShadow(this.textRenderer, "PET NAME", boxX + boxW / 2, boxY + 8, applyAlpha(0xFFF7E6A4, alphaFactor));
        context.drawCenteredTextWithShadow(this.textRenderer, "Free first name", boxX + boxW / 2, boxY + 24, applyAlpha(0xFFD8D8D8, alphaFactor));
        context.drawCenteredTextWithShadow(this.textRenderer, "Optional", boxX + boxW / 2, boxY + 34, applyAlpha(0xFFD8D8D8, alphaFactor));

        context.drawCenteredTextWithShadow(this.textRenderer, "Confirm purchase?", left + modal.w / 2, top + 136, applyAlpha(0xFFECECEC, alphaFactor));
        drawPrice(context, left + modal.w / 2, top + 149, entry.price, true, alphaFactor);
    }

    private void drawStyleConfirmContent(DrawContext context, Rect modal, ShopEntry entry, float delta, float alphaFactor) {
        int left = modal.x;
        int top = modal.y;
        float time = getTimeTicks(delta);

        Rect box = stylePreviewBoxRect(modal);
        context.fill(box.x, box.y, box.x + box.w, box.y + box.h, applyAlpha(0x30000000, alphaFactor));
        context.drawBorder(box.x, box.y, box.w, box.h, applyAlpha(0x66505050, alphaFactor));

        context.enableScissor(box.x + 1, box.y + 1, box.x + box.w - 1, box.y + box.h - 1);
        if (entry.previewType == ShopEntry.PreviewType.ARMOUR) {
            drawArmourPreview(context, entry, createPreviewStack(entry), box.x + box.w / 2, box.y + box.h + 20, 46, time, 0.08f);
        } else {
            drawSpinningItem(context, getPreviewItemId(entry), box.x + box.w / 2, box.y + box.h / 2 + 2, 2.0f, time, 0.12f);
        }
        context.disableScissor();

        List<String> descLines = wrapText(entry.description, modal.w - 40, 2);
        if (descLines.isEmpty()) descLines = List.of("No description set.");

        int descY = box.y + box.h + 8;
        for (String line : descLines) {
            context.drawCenteredTextWithShadow(this.textRenderer, line, left + modal.w / 2, descY, applyAlpha(0xFFD8D8D8, alphaFactor));
            descY += 10;
        }

        if (entry.previewType == ShopEntry.PreviewType.ARMOUR) {
            context.drawCenteredTextWithShadow(this.textRenderer, "Drag to rotate", left + modal.w / 2, top + 126, applyAlpha(0xFFC7C7C7, alphaFactor));
        }

        context.drawCenteredTextWithShadow(this.textRenderer, "Confirm purchase?", left + modal.w / 2, top + 138, applyAlpha(0xFFECECEC, alphaFactor));
        drawPrice(context, left + modal.w / 2, top + 151, entry.price, true, alphaFactor);
    }

    private void drawMiscConfirmContent(DrawContext context, Rect modal, ShopEntry entry, float delta, float alphaFactor) {
        int left = modal.x;
        int top = modal.y;
        float time = getTimeTicks(delta);

        drawSpinningItem(context, getPreviewItemId(entry), left + modal.w / 2, top + 60, 2.35f, time, 0.12f);

        List<String> descLines = wrapText(entry.description, modal.w - 40, 4);
        if (descLines.isEmpty()) descLines = List.of("No description set.");
        int descY = top + 96;
        for (String line : descLines) {
            context.drawCenteredTextWithShadow(this.textRenderer, line, left + modal.w / 2, descY, applyAlpha(0xFFD8D8D8, alphaFactor));
            descY += 10;
        }

        context.drawCenteredTextWithShadow(this.textRenderer, "Confirm purchase?", left + modal.w / 2, top + 136, applyAlpha(0xFFECECEC, alphaFactor));
        drawPrice(context, left + modal.w / 2, top + 149, entry.price, true, alphaFactor);
    }

    private void drawBottomBalanceBar(DrawContext context, int mouseX, int mouseY) {
        int barX = this.x + 8;
        int barY = this.y + this.backgroundHeight - BOTTOM_BAR_H - 6;
        int barW = this.backgroundWidth - 16;
        int barH = BOTTOM_BAR_H;

        context.fill(barX, barY, barX + barW, barY + barH, 0xE41A1A1A);
        context.drawBorder(barX, barY, barW, barH, 0xFF000000);

        int iconSize = 16;
        int iconX = barX + 10;
        int iconY = barY + (barH - iconSize) / 2;
        drawScaledIcon(context, DABLOON_ICON, iconX, iconY, iconSize, DABLOON_TEX_W, DABLOON_TEX_H);

        String total = Integer.toString(ClientShopState.balance());
        context.drawText(this.textRenderer, total, iconX + iconSize + 6, barY + 8, 0xFFFFFFFF, true);

        if (bottomBarRect().contains(mouseX, mouseY)) {
            String breakdown = "Inventory: " + ClientShopState.inventoryBalance() + " | Bank: " + ClientShopState.bankBalance();
            int width = this.textRenderer.getWidth(breakdown) + 10;
            int tipX = Math.min(mouseX + 10, this.width - width - 4);
            int tipY = mouseY - 14;
            context.fill(tipX, tipY, tipX + width, tipY + 12, 0xE0101010);
            context.drawBorder(tipX, tipY, width, 12, 0xFF000000);
            context.drawText(this.textRenderer, breakdown, tipX + 5, tipY + 2, 0xFFFFFFFF, false);
        }
    }

    private void drawToast(DrawContext context) {
        String toast = ClientShopState.toastText();
        if (toast == null || toast.isBlank()) return;

        int width = this.textRenderer.getWidth(toast) + 16;
        int tx = this.x + (this.backgroundWidth - width) / 2;
        int ty = this.y + 6;
        context.fill(tx, ty, tx + width, ty + 16, 0xD8111111);
        context.drawBorder(tx, ty, width, 16, 0xFF000000);
        context.drawCenteredTextWithShadow(this.textRenderer, toast, tx + width / 2, ty + 4, 0xFFFFFFFF);
    }

    private void drawTooltips(DrawContext context, int mouseX, int mouseY) {
        if (viewMode != ViewMode.CATEGORY) return;

        List<ShopEntry> entries = ClientShopState.entriesFor(currentCategory);
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(entries.size(), start + ITEMS_PER_PAGE);
        for (int i = start; i < end; i++) {
            int local = i - start;
            Rect rect = slotRect(local);
            if (rect.contains(mouseX, mouseY)) {
                ShopEntry entry = entries.get(i);
                context.drawTooltip(this.textRenderer, Text.literal(entry.displayName + " - " + entry.price), mouseX, mouseY);
                return;
            }
        }
    }

    private void drawSmallButton(DrawContext context, Rect rect, String label, boolean hovered, float alphaFactor) {
        int fill = hovered ? 0xCE373737 : 0xB91F1F1F;
        int border = hovered ? 0xFFF7E6A4 : 0xFF000000;
        int textColor = 0xFFFFFFFF;

        context.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, applyAlpha(fill, alphaFactor));
        context.drawBorder(rect.x, rect.y, rect.w, rect.h, applyAlpha(border, alphaFactor));
        context.drawCenteredTextWithShadow(this.textRenderer, label, rect.x + rect.w / 2, rect.y + rect.h / 2 - 4, applyAlpha(textColor, alphaFactor));
    }

    private void drawPrice(DrawContext context, int x, int y, int price, boolean centered, float alphaFactor) {
        String text = Integer.toString(Math.max(0, price));
        int iconSize = 12;
        int textW = this.textRenderer.getWidth(text);
        int drawX = centered ? x - (textW + iconSize + 4) / 2 : x;
        context.drawText(this.textRenderer, text, drawX, y, applyAlpha(0xFFF7E6A4, alphaFactor), false);
        drawScaledIcon(context, DABLOON_ICON, drawX + textW + 4, y - 1, iconSize, DABLOON_TEX_W, DABLOON_TEX_H);
    }

    private void drawCenteredTrimmedText(DrawContext context, TextRenderer renderer, String text, int centerX, int y, int maxWidth, int color) {
        String trimmed = renderer.trimToWidth(text, maxWidth);
        context.drawCenteredTextWithShadow(renderer, trimmed, centerX, y, color);
    }

    private void drawStatRow(DrawContext context, int x, int y, ItemStack icon, String label, double value, double min, double max, float alphaFactor) {
        context.drawItem(icon, x, y - 2);

        String valueText = formatNumber(value);
        context.drawText(this.textRenderer, label, x + 20, y, applyAlpha(0xFFFFFFFF, alphaFactor), false);
        context.drawText(this.textRenderer, valueText, x + 70, y, applyAlpha(0xFFF7E6A4, alphaFactor), false);

        int barX = x + 20;
        int barY = y + 10;
        int barW = 72;
        int barH = 5;

        float t = max > min ? (float) ((value - min) / (max - min)) : 0.0f;
        t = MathHelper.clamp(t, 0.0f, 1.0f);

        context.fill(barX, barY, barX + barW, barY + barH, applyAlpha(0xFF101010, alphaFactor));
        context.fill(barX + 1, barY + 1, barX + 1 + Math.round((barW - 2) * t), barY + barH - 1, applyAlpha(0xFFF7E6A4, alphaFactor));
        context.drawBorder(barX, barY, barW, barH, applyAlpha(0xFF000000, alphaFactor));
    }

    private void openCategory(ShopEntry.Category category) {
        this.currentCategory = category;
        this.page = 0;
        this.viewMode = ViewMode.CATEGORY;
        this.confirmEntry = null;
        this.confirmAnim = 0.0f;
        this.pendingPetName = "";
        this.stylePreviewYaw = 16.0f;
        this.stylePreviewPitch = -6.0f;
        this.stylePreviewDragging = false;
        if (petNameField != null) {
            petNameField.setText("");
            petNameField.setFocused(false);
        }
        hidePetNameField();
    }

    private void sendPetRenameNow(String token, String name) {
        var buf = PacketByteBufs.create();
        buf.writeString(token, 128);
        buf.writeString(name == null ? "" : name.trim(), 64);
        ClientPlayNetworking.send(ShopNetworking.C2S_PET_NAME, buf);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        if (viewMode == ViewMode.ROOT) {
            ShopEntry.Category[] categories = {
                    ShopEntry.Category.WEAPONS,
                    ShopEntry.Category.PETS,
                    ShopEntry.Category.STYLES,
                    ShopEntry.Category.MISC
            };

            for (int i = 0; i < categories.length; i++) {
                if (rootButtonRect(i).contains(mouseX, mouseY)) {
                    openCategory(categories[i]);
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (viewMode == ViewMode.CONFIRM) {
            if (isStylePreviewActive() && stylePreviewBoxRect().contains(mouseX, mouseY)) {
                this.stylePreviewDragging = true;
                this.lastStyleDragX = mouseX;
                this.lastStyleDragY = mouseY;
                return true;
            }

            if (isPetConfirmOpen() && petNameField != null) {
                boolean clickedField = petNameField.mouseClicked(mouseX, mouseY, button);
                petNameField.setFocused(clickedField || petNameFieldRect().contains(mouseX, mouseY));
                if (clickedField) {
                    return true;
                }
            }

            if (confirmEntry != null) {
                if (confirmBuyRect().contains(mouseX, mouseY)) {
                    if (currentCategory == ShopEntry.Category.PETS && petNameField != null) {
                        this.pendingPetName = petNameField.getText().trim();
                    } else {
                        this.pendingPetName = "";
                    }

                    var buf = PacketByteBufs.create();
                    buf.writeString(confirmEntry.id, 128);
                    buf.writeString(this.pendingPetName, 64);
                    ClientPlayNetworking.send(ShopNetworking.C2S_BUY, buf);
                    return true;
                }
                if (confirmCancelRect().contains(mouseX, mouseY)) {
                    this.viewMode = ViewMode.CATEGORY;
                    this.confirmEntry = null;
                    this.confirmAnim = 0.0f;
                    this.pendingPetName = "";
                    this.stylePreviewYaw = 16.0f;
                    this.stylePreviewPitch = -6.0f;
                    this.stylePreviewDragging = false;
                    if (petNameField != null) {
                        petNameField.setText("");
                        petNameField.setFocused(false);
                    }
                    hidePetNameField();
                    return true;
                }
            }

            return true;
        }

        if (homeButtonRect().contains(mouseX, mouseY)) {
            this.viewMode = ViewMode.ROOT;
            this.confirmEntry = null;
            this.confirmAnim = 0.0f;
            this.pendingPetName = "";
            this.stylePreviewYaw = 16.0f;
            this.stylePreviewPitch = -6.0f;
            this.stylePreviewDragging = false;
            if (petNameField != null) {
                petNameField.setText("");
                petNameField.setFocused(false);
            }
            hidePetNameField();
            return true;
        }

        List<ShopEntry> entries = ClientShopState.entriesFor(currentCategory);
        int pageCount = Math.max(1, (int) Math.ceil(entries.size() / (double) ITEMS_PER_PAGE));
        if (pageCount > 1) {
            if (prevPageRect().contains(mouseX, mouseY) && this.page > 0) {
                this.page--;
                return true;
            }
            if (nextPageRect().contains(mouseX, mouseY) && this.page < pageCount - 1) {
                this.page++;
                return true;
            }
        }

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(entries.size(), start + ITEMS_PER_PAGE);
        for (int i = start; i < end; i++) {
            int local = i - start;
            Rect rect = slotRect(local);
            if (rect.contains(mouseX, mouseY)) {
                this.confirmEntry = entries.get(i);
                this.viewMode = ViewMode.CONFIRM;
                this.confirmAnim = 0.0f;
                this.pendingPetName = "";
                this.stylePreviewDragging = false;

                if (petNameField != null) {
                    petNameField.setText("");
                    petNameField.setFocused(currentCategory == ShopEntry.Category.PETS);
                }

                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (isPetConfirmOpen() && petNameField != null && petNameField.isVisible() && petNameField.isFocused()) {
            if (petNameField.charTyped(chr, modifiers)) {
                return true;
            }
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isPetConfirmOpen() && petNameField != null && petNameField.isVisible()) {
            if (this.client != null && this.client.options.inventoryKey.matchesKey(keyCode, scanCode) && petNameField.isFocused()) {
                return true;
            }

            if (petNameField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        if (keyCode == 256) {
            if (viewMode == ViewMode.CONFIRM) {
                viewMode = ViewMode.CATEGORY;
                confirmEntry = null;
                confirmAnim = 0.0f;
                pendingPetName = "";
                stylePreviewYaw = 16.0f;
                stylePreviewPitch = -6.0f;
                stylePreviewDragging = false;
                if (petNameField != null) {
                    petNameField.setText("");
                    petNameField.setFocused(false);
                }
                hidePetNameField();
                return true;
            }
            if (viewMode == ViewMode.CATEGORY) {
                viewMode = ViewMode.ROOT;
                return true;
            }
        }

        if (viewMode == ViewMode.CONFIRM && (keyCode == 257 || keyCode == 335) && confirmEntry != null) {
            if (currentCategory == ShopEntry.Category.PETS && petNameField != null) {
                this.pendingPetName = petNameField.getText().trim();
            } else {
                this.pendingPetName = "";
            }

            var buf = PacketByteBufs.create();
            buf.writeString(confirmEntry.id, 128);
            buf.writeString(this.pendingPetName, 64);
            ClientPlayNetworking.send(ShopNetworking.C2S_BUY, buf);
            return true;
        }

        if (viewMode == ViewMode.CATEGORY) {
            List<ShopEntry> entries = ClientShopState.entriesFor(currentCategory);
            int pageCount = Math.max(1, (int) Math.ceil(entries.size() / (double) ITEMS_PER_PAGE));

            if (keyCode == 263 && page > 0) {
                page--;
                return true;
            }
            if (keyCode == 262 && page < pageCount - 1) {
                page++;
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && this.stylePreviewDragging && isStylePreviewActive()) {
            this.stylePreviewYaw += (float) ((mouseX - this.lastStyleDragX) * 2.0);
            this.stylePreviewPitch = MathHelper.clamp(this.stylePreviewPitch + (float) ((mouseY - this.lastStyleDragY) * 1.4), -20.0f, 25.0f);
            this.lastStyleDragX = mouseX;
            this.lastStyleDragY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.stylePreviewDragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private Rect slotRect(int localIndex) {
        int col = localIndex % GRID_COLUMNS;
        int row = localIndex / GRID_COLUMNS;
        int gridW = GRID_COLUMNS * SLOT_W + (GRID_COLUMNS - 1) * SLOT_GAP_X;
        int startX = this.x + (this.backgroundWidth - gridW) / 2;
        int startY = this.y + 52;
        return new Rect(startX + col * (SLOT_W + SLOT_GAP_X), startY + row * (SLOT_H + SLOT_GAP_Y), SLOT_W, SLOT_H);
    }

    private Rect rootButtonRect(int index) {
        int contentX = this.x + 8;
        int contentY = this.y + 30;
        int contentW = this.backgroundWidth - 16;
        int contentH = this.backgroundHeight - 30 - BOTTOM_BAR_H - 8;

        int totalH = ROOT_CARD_H * 4 + ROOT_CARD_GAP * 3;
        int startX = contentX + (contentW - ROOT_CARD_W) / 2;
        int startY = contentY + Math.max(0, (contentH - totalH) / 2);

        return new Rect(startX, startY + index * (ROOT_CARD_H + ROOT_CARD_GAP), ROOT_CARD_W, ROOT_CARD_H);
    }

    private Rect homeButtonRect() {
        return new Rect(this.x + 12, this.y + 34, 54, 18);
    }

    private Rect prevPageRect() {
        return new Rect(this.x + this.backgroundWidth - 68, this.y + 34, 24, 18);
    }

    private Rect nextPageRect() {
        return new Rect(this.x + this.backgroundWidth - 36, this.y + 34, 24, 18);
    }

    private Rect confirmBuyRect() {
        Rect modal = confirmModalRect();
        return new Rect(modal.x + 38, modal.y + modal.h - 24, 90, 18);
    }

    private Rect confirmCancelRect() {
        Rect modal = confirmModalRect();
        return new Rect(modal.x + modal.w - 128, modal.y + modal.h - 24, 90, 18);
    }

    private Rect bottomBarRect() {
        return new Rect(this.x + 8, this.y + this.backgroundHeight - BOTTOM_BAR_H - 6, this.backgroundWidth - 16, BOTTOM_BAR_H);
    }

    private boolean hasTexture(Identifier texture) {
        if (texture == null || client == null) return false;
        try {
            return client.getResourceManager().getResource(texture).isPresent();
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean drawOptionalTexture(DrawContext context, Identifier texture, int x, int y, int w, int h) {
        if (!hasTexture(texture)) return false;

        try {
            int texW = texture.getPath().contains("/root/") ? ROOT_BUTTON_TEX_W : CATEGORY_BG_TEX_W;
            int texH = texture.getPath().contains("/root/") ? ROOT_BUTTON_TEX_H : CATEGORY_BG_TEX_H;

            drawTextureScaled(context, texture, x, y, w, h, texW, texH);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void drawTextureScaled(DrawContext context, Identifier texture, int x, int y, int drawW, int drawH, int texW, int texH) {
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0);
        matrices.scale(drawW / (float) texW, drawH / (float) texH, 1f);

        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, texture);
        context.drawTexture(texture, 0, 0, 0, 0, texW, texH, texW, texH);

        matrices.pop();
    }

    private boolean drawRootBannerTexture(DrawContext context, Identifier texture, Rect rect, float hover, float time) {
        if (!hasTexture(texture)) return false;

        int shadowPad = 1 + Math.round(hover * 2f);
        int shadowAlpha = MathHelper.clamp((int) (16 + hover * 14f), 0, 255);

        context.fill(
                rect.x - shadowPad,
                rect.y - shadowPad,
                rect.x + rect.w + shadowPad,
                rect.y + rect.h + shadowPad,
                (shadowAlpha << 24)
        );

        int innerX = rect.x + ROOT_CARD_PADDING;
        int innerY = rect.y + ROOT_CARD_PADDING;
        int innerW = rect.w - ROOT_CARD_PADDING * 2;
        int innerH = rect.h - ROOT_CARD_PADDING * 2;

        float fitScale = Math.min(innerW / (float) ROOT_BUTTON_TEX_W, innerH / (float) ROOT_BUTTON_TEX_H);
        float scale = fitScale * ROOT_BANNER_SCALE * (1.0f + ROOT_BANNER_HOVER_POP * hover);

        int drawW = Math.max(1, Math.round(ROOT_BUTTON_TEX_W * scale));
        int drawH = Math.max(1, Math.round(ROOT_BUTTON_TEX_H * scale));
        int drawX = innerX + (innerW - drawW) / 2;
        int drawY = innerY + (innerH - drawH) / 2 - Math.round(hover);

        drawTextureScaled(context, texture, drawX, drawY, drawW, drawH, ROOT_BUTTON_TEX_W, ROOT_BUTTON_TEX_H);

        int softHighlightAlpha = MathHelper.clamp((int) (hover * 10f), 0, 255);
        if (softHighlightAlpha > 0) {
            context.fill(drawX, drawY, drawX + drawW, drawY + drawH, (softHighlightAlpha << 24) | 0x00FFFFFF);
        }

        return true;
    }

    private boolean tryRenderGifBackground(DrawContext context, Identifier gifId, int x, int y, int w, int h, float delta) {
        if (gifId == null) return false;

        if (!gifLookupAttempted) {
            gifLookupAttempted = true;
            try {
                Class<?> clazz = Class.forName("net.seep.odd.client.gif.GifTexturePlayer");
                gifRenderMethod = clazz.getMethod("render", DrawContext.class, Identifier.class, int.class, int.class, int.class, int.class, float.class);
            } catch (Throwable ignored) {
                gifRenderMethod = null;
            }
        }

        if (gifRenderMethod == null) return false;

        try {
            gifRenderMethod.invoke(null, context, gifId, x, y, w, h, delta);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void drawScaledIcon(DrawContext context, Identifier tex, int x, int y, int size, int texW, int texH) {
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0);
        matrices.scale(size / (float) texW, size / (float) texH, 1f);
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, tex);
        context.drawTexture(tex, 0, 0, 0, 0, texW, texH, texW, texH);
        matrices.pop();
    }

    private void drawSpinningItem(DrawContext context, String itemId, int centerX, int centerY, float scale, float time, float tilt) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !Registries.ITEM.containsId(id)) return;

        ItemStack stack = new ItemStack(Registries.ITEM.get(id), 1);
        var matrices = context.getMatrices();
        matrices.push();

        float bob = (float) Math.sin(time * 0.08f) * 0.8f;
        matrices.translate(centerX, centerY + bob, 200);

        float s = 20.0f * scale;
        matrices.scale(s, s, s);

        float spin = time * 0.032f;
        Quaternionf rotation = new Quaternionf()
                .rotateY(spin + tilt * 0.08f)
                .rotateX(0.18f)
                .rotateZ(-0.04f);

        matrices.multiply(rotation);

        var itemRenderer = MinecraftClient.getInstance().getItemRenderer();
        var immediate = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        itemRenderer.renderItem(
                stack,
                net.minecraft.client.render.model.json.ModelTransformationMode.GUI,
                0xF000F0,
                OverlayTexture.DEFAULT_UV,
                matrices,
                immediate,
                MinecraftClient.getInstance().world,
                0
        );
        immediate.draw();
        matrices.pop();
    }

    private void drawSpinningEntity(DrawContext context, String entityTypeId, int x, int y, int size, float time, float tilt) {
        Entity ent = getOrCreatePreviewEntity(entityTypeId);
        if (!(ent instanceof LivingEntity living)) return;

        float fakeMouseX = (float) (Math.sin(time * 0.020f) * 18.0f) + tilt * 4.0f;
        float fakeMouseY = (float) (Math.cos(time * 0.017f) * 3.0f);
        InventoryScreen.drawEntity(context, x, y, size, fakeMouseX, fakeMouseY, living);
    }

    private Entity getOrCreatePreviewEntity(String entityTypeId) {
        if (client == null || client.world == null) return null;

        Entity ent = entityPreviewCache.get(entityTypeId);
        if (ent != null) {
            return ent;
        }

        Identifier id = Identifier.tryParse(entityTypeId);
        if (id == null || !Registries.ENTITY_TYPE.containsId(id)) return null;

        EntityType<?> type = Registries.ENTITY_TYPE.get(id);
        ent = type.create(client.world);
        if (ent == null) return null;

        entityPreviewCache.put(entityTypeId, ent);
        return ent;
    }

    private void drawArmourPreview(DrawContext context, ShopEntry entry, ItemStack armourStack, int x, int y, int size, float time, float tilt) {
        if (client == null || client.player == null || armourStack == null || armourStack.isEmpty()) {
            return;
        }

        EquipmentSlot targetSlot = getArmourSlot(entry, armourStack);
        if (targetSlot == null) {
            Identifier id = Registries.ITEM.getId(armourStack.getItem());
            drawSpinningItem(context, id.toString(), x, y - 10, 2.0f, time, tilt);
            return;
        }

        ShopEntry.PreviewArmourSlot focus = entry != null && entry.previewArmourSlot != null
                ? entry.previewArmourSlot
                : ShopEntry.PreviewArmourSlot.AUTO;

        int focusedSize = size;
        int focusedY = y;

        boolean interactive = viewMode == ViewMode.CONFIRM
                && currentCategory == ShopEntry.Category.STYLES
                && confirmEntry == entry;

        if (interactive) {
            switch (focus) {
                case HEAD -> {
                    focusedSize = Math.round(size * 1.18f);
                    focusedY = y + 6;
                }
                case CHEST -> {
                    focusedSize = Math.round(size * 1.08f);
                    focusedY = y + 4;
                }
                case LEGS -> {
                    focusedSize = Math.round(size * 1.00f);
                    focusedY = y + 2;
                }
                case FEET -> {
                    focusedSize = Math.round(size * 0.92f);
                    focusedY = y - 6;
                }
                default -> focusedY = y + 2;
            }
        } else {
            switch (focus) {
                case HEAD -> {
                    focusedSize = Math.round(size * 1.08f);
                    focusedY = y + 3;
                }
                case CHEST -> focusedY = y + 2;
                case LEGS -> focusedY = y + 1;
                case FEET -> focusedY = y - 3;
                default -> { }
            }
        }

        Map<EquipmentSlot, ItemStack> saved = new EnumMap<>(EquipmentSlot.class);
        EquipmentSlot[] slots = new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET,
                EquipmentSlot.MAINHAND,
                EquipmentSlot.OFFHAND
        };

        try {
            for (EquipmentSlot slot : slots) {
                saved.put(slot, client.player.getEquippedStack(slot).copy());
                client.player.equipStack(slot, ItemStack.EMPTY);
            }

            client.player.equipStack(targetSlot, armourStack.copy());

            float lookX;
            float lookY;
            if (interactive) {
                lookX = this.stylePreviewYaw + (this.stylePreviewDragging ? 0.0f : (float) Math.sin(time * 0.035f) * 8.0f);
                lookY = this.stylePreviewPitch;
            } else {
                lookX = (float) (Math.sin(time * 0.018f) * 14.0f) + tilt * 3.0f;
                lookY = (float) (Math.cos(time * 0.014f) * 2.5f);
            }

            InventoryScreen.drawEntity(context, x, focusedY, focusedSize, lookX, lookY, client.player);
        } finally {
            for (EquipmentSlot slot : slots) {
                ItemStack restore = saved.get(slot);
                if (restore != null) {
                    client.player.equipStack(slot, restore);
                }
            }
        }
    }

    private EquipmentSlot getArmourSlot(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armorItem) {
            return armorItem.getSlotType();
        }
        return null;
    }

    private EquipmentSlot getArmourSlot(ShopEntry entry, ItemStack stack) {
        if (entry != null && entry.previewArmourSlot != null && entry.previewArmourSlot != ShopEntry.PreviewArmourSlot.AUTO) {
            return switch (entry.previewArmourSlot) {
                case HEAD -> EquipmentSlot.HEAD;
                case CHEST -> EquipmentSlot.CHEST;
                case LEGS -> EquipmentSlot.LEGS;
                case FEET -> EquipmentSlot.FEET;
                default -> getArmourSlot(stack);
            };
        }
        return getArmourSlot(stack);
    }

    private String getPreviewItemId(ShopEntry entry) {
        if (entry.previewItemId != null && !entry.previewItemId.isBlank()) {
            return entry.previewItemId;
        }
        return entry.giveItemId;
    }

    private ItemStack createPreviewStack(ShopEntry entry) {
        String itemId = getPreviewItemId(entry);
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !Registries.ITEM.containsId(id)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(Registries.ITEM.get(id));
    }

    private WeaponStats readWeaponStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new WeaponStats(0.0, 0.0, false);
        }

        try {
            Multimap<EntityAttribute, EntityAttributeModifier> modifiers = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);

            double attackDamage = 1.0;
            double attackSpeed = 4.0;
            boolean foundDamage = false;
            boolean foundSpeed = false;

            for (Map.Entry<EntityAttribute, EntityAttributeModifier> entry : modifiers.entries()) {
                EntityAttribute attribute = entry.getKey();
                EntityAttributeModifier modifier = entry.getValue();
                double amount = modifier.getValue();

                if (attribute.equals(EntityAttributes.GENERIC_ATTACK_DAMAGE)) {
                    attackDamage += amount;
                    foundDamage = true;
                } else if (attribute.equals(EntityAttributes.GENERIC_ATTACK_SPEED)) {
                    attackSpeed += amount;
                    foundSpeed = true;
                }
            }

            return new WeaponStats(
                    round1(Math.max(0.0, attackDamage)),
                    round1(Math.max(0.0, attackSpeed)),
                    foundDamage || foundSpeed
            );
        } catch (Throwable ignored) {
            return new WeaponStats(0.0, 0.0, false);
        }
    }

    private List<String> wrapText(String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text == null) return lines;

        String cleaned = text.replace("\r", " ").trim();
        if (cleaned.isBlank()) return lines;

        String[] words = cleaned.split("\\s+");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            String test = current.length() == 0 ? word : current + " " + word;
            if (this.textRenderer.getWidth(test) <= maxWidth) {
                current.setLength(0);
                current.append(test);
            } else {
                if (current.length() > 0) {
                    lines.add(current.toString());
                    if (lines.size() >= maxLines) {
                        return lines;
                    }
                }
                current.setLength(0);
                current.append(word);
            }
        }

        if (current.length() > 0 && lines.size() < maxLines) {
            lines.add(current.toString());
        }

        return lines;
    }

    private float getTimeTicks(float delta) {
        return (client == null || client.world == null) ? 0f : (client.world.getTime() + delta);
    }

    private int applyAlpha(int color, float alphaFactor) {
        alphaFactor = MathHelper.clamp(alphaFactor, 0.0f, 1.0f);
        int a = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;
        int newA = MathHelper.clamp(Math.round(a * alphaFactor), 0, 255);
        return (newA << 24) | rgb;
    }

    private float easeOutCubic(float t) {
        t = MathHelper.clamp(t, 0.0f, 1.0f);
        return 1.0f - (float) Math.pow(1.0f - t, 3.0);
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.001) {
            return Integer.toString((int) Math.rint(value));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        // fully handled in render()
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // intentionally empty
    }
}