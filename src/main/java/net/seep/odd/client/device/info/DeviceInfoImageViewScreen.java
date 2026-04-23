package net.seep.odd.client.device.info;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.seep.odd.client.device.social.SocialUrlTextureCache;

@Environment(EnvType.CLIENT)
public final class DeviceInfoImageViewScreen extends Screen {
    private final Screen parent;
    private final String imageUrl;

    public DeviceInfoImageViewScreen(Screen parent, String imageUrl) {
        super(Text.literal("Image"));
        this.parent = parent;
        this.imageUrl = imageUrl;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        context.fill(0, 0, this.width, this.height, 0xCC000000);

        int pad = 24;
        int boxX = pad;
        int boxY = pad;
        int boxW = this.width - (pad * 2);
        int boxH = this.height - (pad * 2) - 18;

        context.fill(boxX - 2, boxY - 2, boxX + boxW + 2, boxY + boxH + 2, 0xFFC8C2A9);
        context.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xEE1F1A16);

        SocialUrlTextureCache.drawContained(context, imageUrl, boxX + 8, boxY + 8, boxW - 16, boxH - 16);

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Click anywhere or press Esc to close"),
                this.width / 2,
                this.height - 16,
                0xFFF7EBD5
        );

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.close();
        return true;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
