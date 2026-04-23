package net.seep.odd.client.device.guild;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.seep.odd.device.guild.GuildManager;
import net.seep.odd.device.guild.GuildNetworking;

@Environment(EnvType.CLIENT)
public final class DeviceGuildStatusEditScreen extends Screen {
    private final Screen parent;
    private final String initialStatus;

    private TextFieldWidget statusField;

    public DeviceGuildStatusEditScreen(Screen parent, String initialStatus) {
        super(Text.literal("My Status"));
        this.parent = parent;
        this.initialStatus = initialStatus == null ? "" : initialStatus;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 150;
        int top = this.height / 2 - 58;

        this.statusField = new TextFieldWidget(this.textRenderer, left + 12, top + 34, 276, 18, Text.literal("Status"));
        this.statusField.setMaxLength(GuildManager.STATUS_MAX_LEN);
        this.statusField.setPlaceholder(Text.literal("What are you up to?"));
        this.statusField.setText(this.initialStatus);

        this.addSelectableChild(this.statusField);
        this.setInitialFocus(this.statusField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b -> save())
                .dimensions(left + 180, top + 66, 52, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close())
                .dimensions(left + 236, top + 66, 52, 20)
                .build());
    }

    private void save() {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeString(this.statusField.getText().trim(), GuildManager.STATUS_MAX_LEN);
        ClientPlayNetworking.send(GuildNetworking.C2S_SET_STATUS, buf);

        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public void tick() {
        if (statusField != null) statusField.tick();
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (statusField != null && statusField.charTyped(chr, modifiers)) return true;
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (statusField != null && statusField.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
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
        int top = this.height / 2 - 58;

        context.fill(left, top, left + 300, top + 94, 0xE0101420);
        context.fill(left + 1, top + 1, left + 299, top + 93, 0xD1161C29);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, top + 10, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Your team status"), left + 12, top + 22, 0xFFC8D5F0);

        statusField.render(context, mouseX, mouseY, delta);

        context.drawTextWithShadow(this.textRenderer,
                Text.literal(statusField.getText().length() + "/" + GuildManager.STATUS_MAX_LEN),
                left + 12,
                top + 70,
                0xFFE4EDFF);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
