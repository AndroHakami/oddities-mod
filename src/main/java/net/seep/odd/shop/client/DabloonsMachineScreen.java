package net.seep.odd.shop.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DabloonsMachineScreen extends HandledScreen<ScreenHandler> {

    /* ---------------- Texture ---------------- */

    // Your new “frame” texture (the image you posted). MUST be 320x210.
    private static final Identifier FRAME = new Identifier(Oddities.MOD_ID, "textures/gui/shop/dabloons_bg.png");

    // Dabloon icon is 32x32
    private static final Identifier DABLOON_ICON = new Identifier(Oddities.MOD_ID, "textures/item/dabloon.png");
    private static final int DABLOON_TEX_W = 32;
    private static final int DABLOON_TEX_H = 32;

    /* ---------------- Pixel-perfect layout (matches your frame image) ---------------- */

    // Inner black “screen” viewport detected from your provided texture
    private static final int VIEW_X = 74;
    private static final int VIEW_Y = 26;
    private static final int VIEW_W = 175;
    private static final int VIEW_H = 158;

    // Arrows sit in the left/right wells
    private static final int ARROW_W = 22;
    private static final int ARROW_H = 34;
    private static final int ARROW_CX_LEFT = 42;
    private static final int ARROW_CX_RIGHT = 278;
    private static final int ARROW_CY = VIEW_Y + (VIEW_H / 2); // vertically centered to viewport

    // Purchase sits in the bottom well
    private static final int BUY_W = 210;
    private static final int BUY_H = 24;
    private static final int BUY_Y = 168; // tuned to match your frame

    // Item name at top center (matches your mock)
    private static final int NAME_Y = 18;

    /* ---------------- Carousel tuning ---------------- */

    private static final float SPACING = 150f;
    private static final float TILT_MAX = 0.70f;
    private static final float ROLL_FACTOR = 0.22f;

    // Hover enlarge (bigger)
    private static final float HOVER_SCALE_TARGET = 1.35f;
    private static final float HOVER_LERP = 0.25f;

    // Ease-out slide
    private static final float SLIDE_DURATION_TICKS = 9.0f;

    /* ---------------- State ---------------- */

    private float animIndex = 0f;
    private int targetIndex = 0;

    private float animFromIndex = 0f;
    private float animStartTime = 0f;

    private final Map<String, Entity> entityPreviewCache = new HashMap<>();
    private final Map<String, Float> hoverScale = new HashMap<>();

    private ShopLoopingSoundInstance music;

    public DabloonsMachineScreen(ScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);

        // Perfect match to your texture
        this.backgroundWidth = 320;
        this.backgroundHeight = 210;
    }

    @Override
    protected void init() {
        super.init();

        // prevent vanilla labels (we override drawForeground)
        this.titleX = 0;
        this.titleY = 0;

        clampTargetToCatalog();
        animIndex = targetIndex;
        animFromIndex = animIndex;
        animStartTime = getTimeTicks(0);

        // looping shop music: odd:shop_music
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        // Draw your frame 1:1 (no scaling artifacts)
        drawFrame(context);

        List<ShopEntry> entries = ClientShopState.catalog();
        if (entries.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, "No shop items.", x + backgroundWidth / 2, y + backgroundHeight / 2 - 4, 0xFFFFFF);
            return;
        }

        clampTargetToCatalog();
        updateEaseSlide(delta);

        int centerX = x + backgroundWidth / 2;

        // Stationary: balance
        drawBalance(context, x + 12, y + 12);

        // Stationary: selected name
        ShopEntry selected = entries.get(selectedIndex());
        context.drawCenteredTextWithShadow(textRenderer, selected.displayName, centerX, y + NAME_Y, 0xFFFFFF);

        // Arrow positions
        int leftArrowX = x + (ARROW_CX_LEFT - ARROW_W / 2);
        int rightArrowX = x + (ARROW_CX_RIGHT - ARROW_W / 2);
        int arrowY = y + (ARROW_CY - ARROW_H / 2);

        boolean canLeft = selectedIndex() > 0;
        boolean canRight = selectedIndex() < entries.size() - 1;

        boolean hoverLeft = isInRect(mouseX, mouseY, leftArrowX, arrowY, ARROW_W, ARROW_H);
        boolean hoverRight = isInRect(mouseX, mouseY, rightArrowX, arrowY, ARROW_W, ARROW_H);

        drawArrowGlyph(context, leftArrowX, arrowY, true, hoverLeft, canLeft);
        drawArrowGlyph(context, rightArrowX, arrowY, false, hoverRight, canRight);

        // Viewport scissor (pixel-perfect)
        int viewX = x + VIEW_X;
        int viewY = y + VIEW_Y;

        context.enableScissor(viewX, viewY, viewX + VIEW_W, viewY + VIEW_H);

        float time = getTimeTicks(delta);
        int viewCenterY = viewY + VIEW_H / 2;

        // Far first, center last
        renderPreviews(context, entries, mouseX, mouseY, centerX, viewCenterY, time, false);
        renderPreviews(context, entries, mouseX, mouseY, centerX, viewCenterY, time, true);

        context.disableScissor();

        // Purchase button (aligned to bottom well)
        int buyX = x + (backgroundWidth - BUY_W) / 2;
        int buyY = y + BUY_Y;

        boolean hoverBuy = isInRect(mouseX, mouseY, buyX, buyY, BUY_W, BUY_H);
        drawPurchaseButton(context, buyX, buyY, hoverBuy, selected);

        // No super.render(): prevents vanilla title + inventory labels
    }

    /* ---------------- Drawing ---------------- */

    private void drawFrame(DrawContext context) {
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, FRAME);

        // Draw full 320x210 texture
        context.drawTexture(FRAME, x, y, 0, 0, backgroundWidth, backgroundHeight, backgroundWidth, backgroundHeight);
    }

    private void drawBalance(DrawContext context, int bx, int by) {
        int bal = ClientShopState.balance();
        int iconSize = 12;

        drawScaledIcon(context, DABLOON_ICON, bx, by, iconSize, DABLOON_TEX_W, DABLOON_TEX_H);
        context.drawText(textRenderer, String.valueOf(bal), bx + iconSize + 5, by + 2, 0xFFFFFF, true);
    }

    private void drawArrowGlyph(DrawContext context, int ax, int ay, boolean left, boolean hovered, boolean enabled) {
        // Keep it minimal so it matches your frame wells (no extra boxes)
        int color = enabled ? 0xFFFFFF : 0x777777;
        if (enabled && hovered) color = 0xFFE6A6;

        String ch = left ? "<" : ">";
        context.drawCenteredTextWithShadow(textRenderer, ch, ax + ARROW_W / 2, ay + 12, color);
    }

    private void drawPurchaseButton(DrawContext context, int bx, int by, boolean hovered, ShopEntry selected) {
        boolean canAfford = ClientShopState.balance() >= Math.max(0, selected.price);

        // Subtle hover highlight that still respects your texture
        if (hovered) {
            int fill = canAfford ? 0x22000000 : 0x221A0A0A;
            context.fill(bx + 2, by + 2, bx + BUY_W - 2, by + BUY_H - 2, fill);
        }

        // Label
        int labelColor = canAfford ? 0xFFFFFF : 0xBBBBBB;
        context.drawCenteredTextWithShadow(textRenderer, "PURCHASE", bx + BUY_W / 2 - 22, by + 7, labelColor);

        // Price on right (number + icon)
        String priceText = Integer.toString(Math.max(0, selected.price));
        int iconSize = 12;
        int priceTextW = textRenderer.getWidth(priceText);
        int rightPad = 12;

        int iconX = bx + BUY_W - rightPad - iconSize;
        int textX = iconX - 4 - priceTextW;

        context.drawText(textRenderer, priceText, textX, by + 7, 0xFFE6A6, false);
        drawScaledIcon(context, DABLOON_ICON, iconX, by + 5, iconSize, DABLOON_TEX_W, DABLOON_TEX_H);
    }

    /**
     * Fixes icon cropping for 32x32 textures by drawing the full region and scaling down.
     */
    private void drawScaledIcon(DrawContext context, Identifier tex, int x, int y, int size, int texW, int texH) {
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0);

        float sx = size / (float) texW;
        float sy = size / (float) texH;
        matrices.scale(sx, sy, 1f);

        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, tex);

        context.drawTexture(tex, 0, 0, 0, 0, texW, texH, texW, texH);
        matrices.pop();
    }

    /* ---------------- Previews (clipped to viewport) ---------------- */

    private void renderPreviews(DrawContext context, List<ShopEntry> entries, int mouseX, int mouseY,
                                int centerX, int centerY, float time, boolean centerPass) {

        for (int i = 0; i < entries.size(); i++) {
            float rel = i - animIndex;
            float abs = Math.abs(rel);

            if (abs > 1.55f) continue;

            boolean isCenter = abs < 0.35f;
            if (centerPass != isCenter) continue;

            ShopEntry e = entries.get(i);

            float baseScale = 1.0f - 0.25f * Math.min(1.55f, abs);
            baseScale = MathHelper.clamp(baseScale, 0.72f, 1.0f);

            int px = Math.round(centerX + rel * SPACING);
            int py = centerY;

            boolean hovered = isHoveredPreview(e, mouseX, mouseY, px, py, baseScale);

            float cur = hoverScale.getOrDefault(e.id, 1.0f);
            float tgt = hovered ? HOVER_SCALE_TARGET : 1.0f;
            cur = MathHelper.lerp(HOVER_LERP, cur, tgt);
            hoverScale.put(e.id, cur);

            float scale = baseScale * cur;

            float tilt = MathHelper.clamp(rel, -1f, 1f) * TILT_MAX;

            if (e.previewType == ShopEntry.PreviewType.ENTITY && e.previewEntityType != null) {
                int size = Math.round(52f * scale);
                drawSpinningEntity(context, e.previewEntityType, px, py + 30, size, time, tilt);
            } else {
                String itemId = (e.previewItemId != null && !e.previewItemId.isBlank()) ? e.previewItemId : e.giveItemId;
                drawSpinningItem(context, itemId, px, py + 6, scale, time, tilt);
            }
        }
    }

    private boolean isHoveredPreview(ShopEntry e, int mouseX, int mouseY, int px, int py, float baseScale) {
        if (e.previewType == ShopEntry.PreviewType.ENTITY && e.previewEntityType != null) {
            int approxSize = Math.round(52f * baseScale);
            int rectX = px - approxSize;
            int rectY = (py + 30) - approxSize * 2;
            int rectW = approxSize * 2;
            int rectH = approxSize * 2;
            return isInRect(mouseX, mouseY, rectX, rectY, rectW, rectH);
        } else {
            int box = Math.round(90f * baseScale);
            int rectX = px - box / 2;
            int rectY = (py + 6) - box / 2;
            return isInRect(mouseX, mouseY, rectX, rectY, box, box);
        }
    }

    private void drawSpinningItem(DrawContext context, String itemId, int centerX, int centerY, float scale, float time, float tilt) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !Registries.ITEM.containsId(id)) return;

        ItemStack stack = new ItemStack(Registries.ITEM.get(id), 1);

        var matrices = context.getMatrices();
        matrices.push();

        matrices.translate(centerX, centerY, 200);
        float s = 26.0f * scale;
        matrices.scale(s, s, s);

        float spin = time * 0.09f;
        float roll = -tilt * ROLL_FACTOR;
        matrices.multiply(new Quaternionf().rotationXYZ(0.40f, spin + tilt, roll));

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
        if (client.world == null) return;

        Entity ent = entityPreviewCache.get(entityTypeId);
        if (ent == null) {
            Identifier id = Identifier.tryParse(entityTypeId);
            if (id == null || !Registries.ENTITY_TYPE.containsId(id)) return;

            EntityType<?> type = Registries.ENTITY_TYPE.get(id);
            ent = type.create(client.world);
            if (ent == null) return;

            entityPreviewCache.put(entityTypeId, ent);
        }

        if (!(ent instanceof LivingEntity living)) return;

        float yawInfluence = (float) (Math.sin(time * 0.07f) * 12f);
        float fakeMouseX = yawInfluence + (tilt * 55f);
        float fakeMouseY = (float) (Math.cos(time * 0.05f) * 6f);

        InventoryScreen.drawEntity(context, x, y, size, fakeMouseX, fakeMouseY, living);
    }

    /* ---------------- Ease-out slide ---------------- */

    private void updateEaseSlide(float delta) {
        float now = getTimeTicks(delta);

        if (Math.abs(animIndex - targetIndex) < 0.0005f) {
            animIndex = targetIndex;
            return;
        }

        float t = (now - animStartTime) / SLIDE_DURATION_TICKS;
        t = MathHelper.clamp(t, 0f, 1f);

        // easeOutCubic
        float eased = 1f - (float) Math.pow(1f - t, 3);

        animIndex = MathHelper.lerp(eased, animFromIndex, (float) targetIndex);

        if (t >= 1f) {
            animIndex = targetIndex;
        }
    }

    private void setTarget(int idx, float delta) {
        List<ShopEntry> entries = ClientShopState.catalog();
        if (entries.isEmpty()) {
            targetIndex = 0;
            animIndex = 0f;
            return;
        }

        int clamped = MathHelper.clamp(idx, 0, entries.size() - 1);
        if (clamped == targetIndex && Math.abs(animIndex - targetIndex) < 0.001f) return;

        animFromIndex = animIndex;
        targetIndex = clamped;
        animStartTime = getTimeTicks(delta);
    }

    private int selectedIndex() {
        List<ShopEntry> entries = ClientShopState.catalog();
        if (entries.isEmpty()) return 0;
        int idx = Math.round(animIndex);
        return MathHelper.clamp(idx, 0, entries.size() - 1);
    }

    private void clampTargetToCatalog() {
        List<ShopEntry> entries = ClientShopState.catalog();
        if (entries.isEmpty()) {
            targetIndex = 0;
            animIndex = 0f;
            return;
        }
        targetIndex = MathHelper.clamp(targetIndex, 0, entries.size() - 1);
    }

    private float getTimeTicks(float delta) {
        return (client.world == null) ? 0f : (client.world.getTime() + delta);
    }

    /* ---------------- Input ---------------- */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        List<ShopEntry> entries = ClientShopState.catalog();
        if (entries.isEmpty()) return super.mouseClicked(mouseX, mouseY, button);

        int leftArrowX = x + (ARROW_CX_LEFT - ARROW_W / 2);
        int rightArrowX = x + (ARROW_CX_RIGHT - ARROW_W / 2);
        int arrowY = y + (ARROW_CY - ARROW_H / 2);

        if (isInRect(mouseX, mouseY, leftArrowX, arrowY, ARROW_W, ARROW_H)) {
            int sel = selectedIndex();
            if (sel > 0) setTarget(sel - 1, 0f);
            return true;
        }
        if (isInRect(mouseX, mouseY, rightArrowX, arrowY, ARROW_W, ARROW_H)) {
            int sel = selectedIndex();
            if (sel < entries.size() - 1) setTarget(sel + 1, 0f);
            return true;
        }

        int buyX = x + (backgroundWidth - BUY_W) / 2;
        int buyY = y + BUY_Y;

        if (isInRect(mouseX, mouseY, buyX, buyY, BUY_W, BUY_H)) {
            ShopEntry entry = entries.get(selectedIndex());
            var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
            buf.writeString(entry.id, 128);
            ClientPlayNetworking.send(ShopNetworking.C2S_BUY, buf);
            return true;
        }

        // Click inside viewport: nudge left/right
        int viewX = x + VIEW_X;
        int viewY = y + VIEW_Y;
        if (isInRect(mouseX, mouseY, viewX, viewY, VIEW_W, VIEW_H)) {
            double dx = mouseX - (x + backgroundWidth / 2.0);
            int sel = selectedIndex();
            if (dx < -30 && sel > 0) {
                setTarget(sel - 1, 0f);
                return true;
            }
            if (dx > 30 && sel < entries.size() - 1) {
                setTarget(sel + 1, 0f);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }


    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        List<ShopEntry> entries = ClientShopState.catalog();
        if (entries.isEmpty()) return false;

        int sel = selectedIndex();
        if (verticalAmount < 0) {
            if (sel < entries.size() - 1) setTarget(sel + 1, 0f);
        } else if (verticalAmount > 0) {
            if (sel > 0) setTarget(sel - 1, 0f);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        List<ShopEntry> entries = ClientShopState.catalog();
        if (!entries.isEmpty()) {
            int sel = selectedIndex();

            // left/right arrows (GLFW)
            if (keyCode == 263) {
                if (sel > 0) setTarget(sel - 1, 0f);
                return true;
            }
            if (keyCode == 262) {
                if (sel < entries.size() - 1) setTarget(sel + 1, 0f);
                return true;
            }

            // enter = purchase
            if (keyCode == 257 || keyCode == 335) {
                ShopEntry entry = entries.get(sel);
                var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                buf.writeString(entry.id, 128);
                ClientPlayNetworking.send(ShopNetworking.C2S_BUY, buf);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /* ---------------- Vanilla draw suppression ---------------- */

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        // handled in render()
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // intentionally empty: removes "Inventory" and vanilla title rendering
    }

    /* ---------------- Utils ---------------- */

    private static boolean isInRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < (x + w) && my >= y && my < (y + h);
    }
}
