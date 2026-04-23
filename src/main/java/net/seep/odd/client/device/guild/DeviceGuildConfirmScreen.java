package net.seep.odd.client.device.guild;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

@Environment(EnvType.CLIENT)
public final class DeviceGuildConfirmScreen extends Screen {
    private final Screen parent;
    private final Text header;
    private final String body;
    private final String confirmLabel;
    private final Runnable onConfirm;

    public DeviceGuildConfirmScreen(Screen parent, Text header, String body, String confirmLabel, Runnable onConfirm) {
        super(header);
        this.parent = parent;
        this.header = header;
        this.body = body == null ? "" : body;
        this.confirmLabel = confirmLabel == null ? "Confirm" : confirmLabel;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 150;
        int top = this.height / 2 - 64;

        this.addDrawableChild(ButtonWidget.builder(Text.literal(confirmLabel), b -> {
                    if (onConfirm != null) onConfirm.run();
                })
                .dimensions(left + 128, top + 84, 76, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close())
                .dimensions(left + 212, top + 84, 76, 20)
                .build());
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        int left = this.width / 2 - 150;
        int top = this.height / 2 - 64;

        context.fill(left, top, left + 300, top + 116, 0xE0101420);
        context.fill(left + 1, top + 1, left + 299, top + 115, 0xD1161C29);

        context.drawCenteredTextWithShadow(this.textRenderer, header, this.width / 2, top + 10, 0xFFFFFFFF);

        List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal(body), 272);
        int y = top + 32;
        for (OrderedText line : lines) {
            context.drawTextWithShadow(this.textRenderer, line, left + 14, y, 0xFFD8E4F8);
            y += 10;
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
