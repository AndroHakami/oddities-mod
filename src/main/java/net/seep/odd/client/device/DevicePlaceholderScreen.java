package net.seep.odd.client.device;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

public final class DevicePlaceholderScreen extends Screen {
    private static final Identifier HOME_BG = new Identifier(Oddities.MOD_ID, "textures/gui/device/home_bg.png");
    private static final Identifier HOME_OVERLAY = new Identifier(Oddities.MOD_ID, "textures/gui/device/home_overlay.png");

    private final DeviceApp app;

    public DevicePlaceholderScreen(DeviceApp app) {
        super(Text.literal(app.title()));
        this.app = app;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(new DeviceHomeScreen());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        int left = (this.width - DeviceHomeScreen.GUI_W) / 2;
        int top = (this.height - DeviceHomeScreen.GUI_H) / 2;

        context.fill(left - 5, top - 5, left + DeviceHomeScreen.GUI_W + 5, top + DeviceHomeScreen.GUI_H + 5, 0x66000000);
        context.drawTexture(HOME_BG, left, top, 0, 0, DeviceHomeScreen.GUI_W, DeviceHomeScreen.GUI_H,
                DeviceHomeScreen.GUI_W, DeviceHomeScreen.GUI_H);

        int cardX1 = left + 26;
        int cardY1 = top + 74;
        int cardX2 = left + DeviceHomeScreen.GUI_W - 26;
        int cardY2 = top + 246;

        context.fill(cardX1, cardY1, cardX2, cardY2, 0xA0101420);
        context.fill(cardX1 + 1, cardY1 + 1, cardX2 - 1, cardY2 - 1, 0xD91A2230);

        float time = (System.currentTimeMillis() / 85.0f);
        float scale = DeviceHomeScreen.BASE_ICON_SCALE * (1.0f + ((float) Math.sin(time * 0.10f) * 0.05f));
        int cx = this.width / 2;
        int iconY = top + 102;

        context.getMatrices().push();
        context.getMatrices().translate(cx, iconY, 0.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.getMatrices().translate(-12.0f, -12.0f, 0.0f);
        context.drawTexture(app.texture(), 0, 0, 0, 0, 24, 24, 24, 24);
        context.getMatrices().pop();

        context.drawCenteredTextWithShadow(this.textRenderer, app.title(), this.width / 2, top + 154, 0xFFF5F7FF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("App shell ready."), this.width / 2, top + 176, 0xFFB6C7E6);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("We'll wire the real features next."), this.width / 2, top + 190, 0xFF9FB0CC);

        boolean overBack = isOverBack(mouseX, mouseY, left, top);
        int backColor = overBack ? 0xFFF7FAFF : 0xFFC7D2E6;
        context.drawTextWithShadow(this.textRenderer, Text.literal("← Home"), left + 22, top + 18, backColor);

        context.drawTexture(HOME_OVERLAY, left, top, 0, 0, DeviceHomeScreen.GUI_W, DeviceHomeScreen.GUI_H,
                DeviceHomeScreen.GUI_W, DeviceHomeScreen.GUI_H);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = (this.width - DeviceHomeScreen.GUI_W) / 2;
        int top = (this.height - DeviceHomeScreen.GUI_H) / 2;

        if (button == 0 && isOverBack(mouseX, mouseY, left, top)) {
            playClickSound();
            if (this.client != null) {
                this.client.setScreen(new DeviceHomeScreen());
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isOverBack(double mouseX, double mouseY, int left, int top) {
        return mouseX >= left + 18 && mouseX <= left + 82 && mouseY >= top + 14 && mouseY <= top + 30;
    }

    private void playClickSound() {
        if (this.client == null || this.client.player == null) return;
        this.client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.40f, 0.95f);
    }
}