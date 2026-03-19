package net.seep.odd.client.device;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.Oddities;

public final class DeviceHomeScreen extends Screen {
    public static final int GUI_W = 240;
    public static final int GUI_H = 320;
    public static final int ICON_DRAW = 64;
    public static final float BASE_ICON_SCALE = ICON_DRAW / 24.0f;

    private static final Identifier HOME_BG = new Identifier(Oddities.MOD_ID, "textures/gui/device/home_bg.png");
    private static final Identifier HOME_OVERLAY = new Identifier(Oddities.MOD_ID, "textures/gui/device/home_overlay.png");

    private final Map<DeviceApp, Float> hoverProgress = new EnumMap<>(DeviceApp.class);
    private DeviceApp hovered = null;

    public DeviceHomeScreen() {
        super(Text.literal("Device"));
        for (DeviceApp app : DeviceApp.values()) {
            hoverProgress.put(app, 0.0f);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        int left = (this.width - GUI_W) / 2;
        int top = (this.height - GUI_H) / 2;

        updateHovered(mouseX, mouseY);
        animateHover();

        context.fill(left - 5, top - 5, left + GUI_W + 5, top + GUI_H + 5, 0x66000000);
        context.drawTexture(HOME_BG, left, top, 0, 0, GUI_W, GUI_H, GUI_W, GUI_H);

        for (DeviceApp app : DeviceApp.values()) {
            renderApp(context, app, left, top, delta);
        }

        context.drawTexture(HOME_OVERLAY, left, top, 0, 0, GUI_W, GUI_H, GUI_W, GUI_H);

        if (hovered != null) {
            int labelY = top + GUI_H - 28;
            context.drawCenteredTextWithShadow(this.textRenderer, hovered.title(), this.width / 2, labelY, 0xFFF5F7FF);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void updateHovered(int mouseX, int mouseY) {
        DeviceApp newHovered = getAppAt(mouseX, mouseY);
        if (newHovered != hovered) {
            hovered = newHovered;
            if (hovered != null) {
                playHoverSound();
            }
        }
    }

    private void animateHover() {
        for (DeviceApp app : DeviceApp.values()) {
            float current = hoverProgress.get(app);
            float target = (app == hovered) ? 1.0f : 0.0f;
            hoverProgress.put(app, MathHelper.lerp(0.22f, current, target));
        }
    }

    private void renderApp(DrawContext context, DeviceApp app, int left, int top, float delta) {
        float hover = hoverProgress.get(app);
        float time = (System.currentTimeMillis() / 85.0f) + (app.ordinal() * 5.0f);
        float idleBob = (float) Math.sin(time * 0.20f) * 0.020f;
        float pulse = (float) Math.sin(time * 0.11f) * 0.012f;
        float scale = 1.0f + idleBob + pulse + (hover * 0.18f);

        int x = left + app.x();
        int y = top + app.y();
        int cx = x + ICON_DRAW / 2;
        int cy = y + ICON_DRAW / 2;

        if (hover > 0.01f) {
            int glowAlpha = (int) (hover * 95.0f) & 0xFF;
            int shadowAlpha = (int) (hover * 55.0f) & 0xFF;
            context.fill(cx - 30, cy - 30, cx + 30, cy + 30, (glowAlpha << 24) | 0xC7DAFF);
            context.fill(cx - 34, cy + 24, cx + 34, cy + 34, (shadowAlpha << 24));
        }

        context.getMatrices().push();
        context.getMatrices().translate(cx, cy, 0.0f);
        context.getMatrices().scale(BASE_ICON_SCALE * scale, BASE_ICON_SCALE * scale, 1.0f);
        context.getMatrices().translate(-12.0f, -12.0f, 0.0f);
        context.drawTexture(app.texture(), 0, 0, 0, 0, 24, 24, 24, 24);
        context.getMatrices().pop();
    }

    private DeviceApp getAppAt(double mouseX, double mouseY) {
        int left = (this.width - GUI_W) / 2;
        int top = (this.height - GUI_H) / 2;

        for (DeviceApp app : DeviceApp.values()) {
            int x = left + app.x();
            int y = top + app.y();
            if (mouseX >= x && mouseX <= x + ICON_DRAW && mouseY >= y && mouseY <= y + ICON_DRAW) {
                return app;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            DeviceApp app = getAppAt(mouseX, mouseY);
            if (app != null) {
                playClickSound();
                if (this.client != null) {
                    switch (app) {
                        case SOCIAL -> this.client.setScreen(new net.seep.odd.client.device.social.DeviceSocialScreen());
                        case NOTES -> this.client.setScreen(new net.seep.odd.client.device.notes.DeviceNotesScreen());
                        case DABLOON_BANK -> this.client.setScreen(new net.seep.odd.client.device.bank.DeviceBankScreen());
                        default -> this.client.setScreen(new DevicePlaceholderScreen(app));
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void playHoverSound() {
        if (this.client == null || this.client.player == null) return;
        this.client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.24f, 1.65f);
    }

    private void playClickSound() {
        if (this.client == null || this.client.player == null) return;
        this.client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.40f, 1.00f);
    }
}