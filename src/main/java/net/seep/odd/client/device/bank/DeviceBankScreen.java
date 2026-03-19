package net.seep.odd.client.device.bank;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.seep.odd.Oddities;
import net.seep.odd.client.device.DeviceHomeScreen;
import net.seep.odd.device.bank.DabloonBankNetworking;

@Environment(EnvType.CLIENT)
public final class DeviceBankScreen extends Screen {
    private static final Identifier HOME_BG = new Identifier(Oddities.MOD_ID, "textures/gui/device/bank/bank_bg.png");
    private static final Identifier HOME_OVERLAY = new Identifier(Oddities.MOD_ID, "textures/gui/device/home_overlay.png");

    private static final Identifier ICON_HOME = new Identifier(Oddities.MOD_ID, "textures/gui/device/social/top/home.png");
    private static final Identifier BTN_DEPOSIT = new Identifier(Oddities.MOD_ID, "textures/gui/device/bank/deposit_button.png");
    private static final Identifier BTN_DEPOSIT_DISABLED = new Identifier(Oddities.MOD_ID, "textures/gui/device/bank/deposit_button_disabled.png");
    private static final Identifier BTN_WITHDRAW = new Identifier(Oddities.MOD_ID, "textures/gui/device/bank/withdraw_button.png");
    private static final Identifier DABLOON_ID = new Identifier(Oddities.MOD_ID, "dabloon");

    private static final Identifier BANK_BG_GIF = new Identifier(Oddities.MOD_ID, "textures/gui/device/bank/background.gif");
    private static final AssetGifPlayer BANK_GIF = new AssetGifPlayer(BANK_BG_GIF, "odd_bank_bg");

    private static final int TOP_ICON_SIZE = 22;
    private static final int WIDE_W = 90;
    private static final int WIDE_H = 48;
    private static final int BUTTON_GAP = 6;
    private static final int COIN_RENDER_SIZE = 68;

    private final Map<String, Float> hoverProgress = new HashMap<>();
    private final Set<String> hoveredLastFrame = new HashSet<>();
    private final Set<String> hoveredThisFrame = new HashSet<>();
    private final java.util.List<ClickZone> zones = new ArrayList<>();

    public DeviceBankScreen() {
        super(Text.literal("Bank"));
    }

    @Override
    protected void init() {
        BANK_GIF.ensureLoaded();
        requestSync();
    }

    private void requestSync() {
        ClientPlayNetworking.send(
                DabloonBankNetworking.C2S_REQUEST_SYNC,
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

        context.fill(left - 5, top - 5, left + DeviceHomeScreen.GUI_W + 5, top + DeviceHomeScreen.GUI_H + 5, 0x66000000);
        context.drawTexture(HOME_BG, left, top, 0, 0, DeviceHomeScreen.GUI_W, DeviceHomeScreen.GUI_H, DeviceHomeScreen.GUI_W, DeviceHomeScreen.GUI_H);
        renderGifOverlay(context, left, top);

        renderBackdropGlow(context, left, top);
        renderTopButtons(context, left, top, mouseX, mouseY);
        renderCoinShowcase(context, left, top, mouseX, mouseY);
        renderBalanceSection(context, left, top);
        renderActionButtons(context, left, top, mouseX, mouseY);

        context.drawTexture(HOME_OVERLAY, left, top, 0, 0, DeviceHomeScreen.GUI_W, DeviceHomeScreen.GUI_H, DeviceHomeScreen.GUI_W, DeviceHomeScreen.GUI_H);

        hoveredLastFrame.clear();
        hoveredLastFrame.addAll(hoveredThisFrame);

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderGifOverlay(DrawContext context, int left, int top) {
        context.enableScissor(left, top, left + DeviceHomeScreen.GUI_W, top + DeviceHomeScreen.GUI_H);
        BANK_GIF.drawCover(context, left, top, DeviceHomeScreen.GUI_W, DeviceHomeScreen.GUI_H, 0.78f);
        context.disableScissor();
    }

    private void renderBackdropGlow(DrawContext context, int left, int top) {
        int centerX = left + (DeviceHomeScreen.GUI_W / 2);
        int glowTop = top + 42;

        context.fill(centerX - 56, glowTop, centerX + 56, glowTop + 158, 0x081E1400);
        context.fill(centerX - 44, glowTop + 10, centerX + 44, glowTop + 146, 0x0E241700);
        context.fill(centerX - 34, glowTop + 20, centerX + 34, glowTop + 132, 0x12281700);
    }

    private void renderTopButtons(DrawContext context, int left, int top, int mouseX, int mouseY) {
        int homeX = left + 18;
        int homeY = top + 14;

        renderIconButton(context, "bank_home", ICON_HOME, homeX, homeY, TOP_ICON_SIZE, mouseX, mouseY);

        zones.add(new ClickZone(homeX, homeY, homeX + TOP_ICON_SIZE, homeY + TOP_ICON_SIZE, () -> {
            if (this.client != null) {
                this.client.setScreen(new DeviceHomeScreen());
            }
        }));
    }

    private void renderCoinShowcase(DrawContext context, int left, int top, int mouseX, int mouseY) {
        int centerX = left + (DeviceHomeScreen.GUI_W / 2);
        int coinX = centerX - (COIN_RENDER_SIZE / 2);
        int coinY = top + 50;

        boolean hovered = mouseX >= coinX && mouseX <= coinX + COIN_RENDER_SIZE && mouseY >= coinY && mouseY <= coinY + COIN_RENDER_SIZE;
        float hover = advanceHover("bank_coin", hovered);

        float time = System.currentTimeMillis() / 18.0f;
        float bob = (float) Math.sin(time * 0.08f) * 2.5f;
        float flip = (float) Math.sin(time * 0.065f);
        float xScale = 0.78f + (Math.abs(flip) * 0.32f);
        float yScale = 1.0f + (hover * 0.10f);
        float tilt = (float) Math.sin(time * 0.04f) * 8.0f;
        int drawY = coinY + Math.round(bob);

        int shadowAlpha = 34 + (int) (hover * 18.0f);
        context.fill(centerX - 18, drawY + 52, centerX + 18, drawY + 56, (shadowAlpha << 24) | 0x000000);

        renderCoinSparkles(context, centerX, drawY + (COIN_RENDER_SIZE / 2), hover, time);

        Item item = Registries.ITEM.get(DABLOON_ID);
        if (item != null && item != Items.AIR) {
            ItemStack stack = new ItemStack(item);
            float baseScale = COIN_RENDER_SIZE / 16.0f;

            context.getMatrices().push();
            context.getMatrices().translate(centerX, drawY + (COIN_RENDER_SIZE / 2), 0.0f);
            context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(tilt));
            context.getMatrices().scale(baseScale * xScale * (1.0f + hover * 0.06f), baseScale * yScale, 1.0f);
            context.getMatrices().translate(-8.0f, -8.0f, 0.0f);
            context.drawItem(stack, 0, 0);
            context.getMatrices().pop();
        } else {
            context.fill(centerX - 18, drawY + 10, centerX + 18, drawY + 46, 0xAAE0B84C);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("?"), centerX, drawY + 20, 0xFF2A1D00);
        }

        zones.add(new ClickZone(coinX, coinY, coinX + COIN_RENDER_SIZE, coinY + COIN_RENDER_SIZE, this::requestSync));
    }

    private void renderCoinSparkles(DrawContext context, int cx, int cy, float hover, float time) {
        for (int i = 0; i < 8; i++) {
            float t = (time * 0.03f) + (i * 0.7853982f);
            float outward = 22.0f + ((float) Math.sin((time * 0.09f) + i) * 6.0f) + (hover * 7.0f);
            float radiusX = outward + ((i % 2 == 0) ? 6.0f : 0.0f);
            float radiusY = outward * 0.68f;

            int sx = cx + Math.round((float) Math.cos(t) * radiusX);
            int sy = cy + Math.round((float) Math.sin(t) * radiusY);

            float pulse = 0.5f + 0.5f * (float) Math.sin((time * 0.12f) + i * 0.8f);
            int alpha = 45 + (int) (pulse * 125.0f);
            int size = pulse > 0.72f ? 2 : 1;

            drawSparkle(context, sx, sy, size, (alpha << 24) | 0xFFE17A);
            drawSparkle(context, sx, sy, 1, ((alpha / 2) << 24) | 0xFFFFF0);
        }

        for (int i = 0; i < 4; i++) {
            float t = (time * 0.05f) + (i * 1.5707964f);
            int sx = cx + Math.round((float) Math.cos(t) * 30.0f);
            int sy = cy + Math.round((float) Math.sin(t) * 18.0f);
            context.fill(sx, sy, sx + 1, sy + 1, 0x88FFD86A);
        }
    }

    private void drawSparkle(DrawContext context, int x, int y, int size, int color) {
        context.fill(x - size, y, x + size + 1, y + 1, color);
        context.fill(x, y - size, x + 1, y + size + 1, color);
    }

    private void renderBalanceSection(DrawContext context, int left, int top) {
        int cardX = left + 18;
        int cardY = top + 126;
        int cardW = DeviceHomeScreen.GUI_W - 36;
        int cardH = 62;

        context.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xD8151614);
        context.fill(cardX, cardY, cardX + cardW, cardY + 1, 0x99E8C56A);
        context.fill(cardX, cardY + cardH - 1, cardX + cardW, cardY + cardH, 0x442A2412);

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Total balance"),
                left + (DeviceHomeScreen.GUI_W / 2),
                cardY + 9,
                0xFFF6E2A5
        );

        String amountText = NumberFormat.getIntegerInstance().format(DabloonBankClientCache.balance());

        context.getMatrices().push();
        context.getMatrices().translate(left + (DeviceHomeScreen.GUI_W / 2), cardY + 28, 0.0f);
        context.getMatrices().scale(2.2f, 2.2f, 1.0f);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(amountText), 0, 0, 0xFFFFF5D6);
        context.getMatrices().pop();

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("dabloons"),
                left + (DeviceHomeScreen.GUI_W / 2),
                cardY + 46,
                0xFFE2C25E
        );
    }

    private void renderActionButtons(DrawContext context, int left, int top, int mouseX, int mouseY) {
        boolean depositAllowed = DabloonBankClientCache.depositAllowed();

        int totalW = (WIDE_W * 2) + BUTTON_GAP;
        int startX = left + (DeviceHomeScreen.GUI_W / 2) - (totalW / 2);

        int depositX = startX;
        int withdrawX = startX + WIDE_W + BUTTON_GAP;
        int buttonY = top + 204;

        if (depositAllowed) {
            renderWideButton(context, "bank_deposit", BTN_DEPOSIT, depositX, buttonY, WIDE_W, WIDE_H, mouseX, mouseY);
            zones.add(new ClickZone(depositX, buttonY, depositX + WIDE_W, buttonY + WIDE_H, () -> {
                if (this.client != null) {
                    this.client.setScreen(new DeviceBankAmountScreen(this, true));
                }
            }));
        } else {
            renderWideButtonDisabled(context, BTN_DEPOSIT_DISABLED, depositX, buttonY, WIDE_W, WIDE_H);
        }

        renderWideButton(context, "bank_withdraw", BTN_WITHDRAW, withdrawX, buttonY, WIDE_W, WIDE_H, mouseX, mouseY);
        zones.add(new ClickZone(withdrawX, buttonY, withdrawX + WIDE_W, buttonY + WIDE_H, () -> {
            if (this.client != null) {
                this.client.setScreen(new DeviceBankAmountScreen(this, false));
            }
        }));

        if (!depositAllowed) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("Deposit only within 50 blocks of your spawn"),
                    left + (DeviceHomeScreen.GUI_W / 2),
                    buttonY - 10,
                    0xFFB7A677
            );
        }
    }

    private float advanceHover(String key, boolean hovered) {
        if (hovered) {
            hoveredThisFrame.add(key);
            if (!hoveredLastFrame.contains(key)) {
                playHoverSound();
            }
        }

        float current = hoverProgress.getOrDefault(key, 0.0f);
        float target = hovered ? 1.0f : 0.0f;
        float next = MathHelper.lerp(0.24f, current, target);
        hoverProgress.put(key, next);
        return next;
    }

    private void renderIconButton(DrawContext context, String key, Identifier texture, int x, int y, int size, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size;
        float hover = advanceHover(key, hovered);

        float time = (System.currentTimeMillis() / 90.0f) + (Math.abs(key.hashCode()) % 997);
        float idleBob = (float) Math.sin(time * 0.12f) * 0.020f;
        float pulse = (float) Math.sin(time * 0.07f) * 0.012f;
        float scale = 1.0f + idleBob + pulse + (hover * 0.18f);

        int cx = x + (size / 2);
        int cy = y + (size / 2);

        if (hover > 0.01f) {
            int glowAlpha = (int) (hover * 72.0f) & 0xFF;
            context.fill(cx - (size / 2) - 2, cy - (size / 2) - 2, cx + (size / 2) + 2, cy + (size / 2) + 2, (glowAlpha << 24) | 0xF5D98B);
        }

        context.getMatrices().push();
        context.getMatrices().translate(cx, cy, 0.0f);
        context.getMatrices().scale((size / 24.0f) * scale, (size / 24.0f) * scale, 1.0f);
        context.getMatrices().translate(-12.0f, -12.0f, 0.0f);
        context.drawTexture(texture, 0, 0, 0, 0, 24, 24, 24, 24);
        context.getMatrices().pop();
    }

    private void renderWideButton(DrawContext context, String key, Identifier texture, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        float hover = advanceHover(key, hovered);

        float scale = 1.0f + (hover * 0.06f);

        if (hover > 0.01f) {
            int glowAlpha = (int) (hover * 54.0f) & 0xFF;
            context.fill(x - 2, y - 2, x + w + 2, y + h + 2, (glowAlpha << 24) | 0xF0CB69);
        }

        int cx = x + (w / 2);
        int cy = y + (h / 2);

        context.getMatrices().push();
        context.getMatrices().translate(cx, cy, 0.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.getMatrices().translate(-(w / 2.0f), -(h / 2.0f), 0.0f);
        context.drawTexture(texture, 0, 0, 0, 0, w, h, w, h);
        context.getMatrices().pop();
    }

    private void renderWideButtonDisabled(DrawContext context, Identifier texture, int x, int y, int w, int h) {
        context.drawTexture(texture, x, y, 0, 0, w, h, w, h);
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
}