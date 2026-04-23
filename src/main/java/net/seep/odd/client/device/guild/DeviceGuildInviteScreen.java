package net.seep.odd.client.device.guild;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.seep.odd.device.guild.GuildNetworking;

@Environment(EnvType.CLIENT)
public final class DeviceGuildInviteScreen extends Screen {
    private final Screen parent;

    private TextFieldWidget playerField;
    private String status = "";

    public DeviceGuildInviteScreen(Screen parent) {
        super(Text.literal("Invite Player"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 150;
        int top = this.height / 2 - 58;

        this.playerField = new TextFieldWidget(this.textRenderer, left + 12, top + 34, 276, 18, Text.literal("Player"));
        this.playerField.setMaxLength(64);
        this.playerField.setPlaceholder(Text.literal("Player name"));

        this.addSelectableChild(this.playerField);
        this.setInitialFocus(this.playerField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Send Invite"), b -> invite())
                .dimensions(left + 142, top + 66, 70, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close())
                .dimensions(left + 218, top + 66, 70, 20)
                .build());
    }

    private void invite() {
        String target = this.playerField.getText().trim();
        if (target.isEmpty()) {
            this.status = "Enter a player name.";
            return;
        }

        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeString(target, 64);
        ClientPlayNetworking.send(GuildNetworking.C2S_INVITE, buf);

        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public void tick() {
        if (playerField != null) playerField.tick();
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (playerField != null && playerField.charTyped(chr, modifiers)) return true;
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (playerField != null && playerField.keyPressed(keyCode, scanCode, modifiers)) return true;
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
        context.drawTextWithShadow(this.textRenderer, Text.literal("Player name"), left + 12, top + 22, 0xFFC8D5F0);

        playerField.render(context, mouseX, mouseY, delta);

        if (!status.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(status), this.width / 2, top + 70, 0xFFFFD0A0);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
