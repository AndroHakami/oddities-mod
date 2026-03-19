package net.seep.odd.client.device.social;

import java.util.UUID;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.seep.odd.device.social.SocialManager;
import net.seep.odd.device.social.SocialNetworking;


@Environment(EnvType.CLIENT)
public final class DeviceSocialReplyScreen extends Screen {
    private final Screen parent;
    private final UUID postId;

    private TextFieldWidget bodyField;
    private String status = "";

    public DeviceSocialReplyScreen(Screen parent, UUID postId) {
        super(Text.literal("Reply"));
        this.parent = parent;
        this.postId = postId;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 110;
        int top = this.height / 2 - 70;

        this.bodyField = new TextFieldWidget(this.textRenderer, left, top + 25, 220, 20, Text.literal("Reply"));
        this.bodyField.setMaxLength(SocialManager.REPLY_MAX_LEN);
        this.bodyField.setPlaceholder(Text.literal("Write a reply..."));
        this.addSelectableChild(this.bodyField);
        this.setInitialFocus(this.bodyField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Send"), b -> sendReply())
                .dimensions(left, top + 55, 106, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> this.client.setScreen(parent))
                .dimensions(left + 114, top + 55, 106, 20)
                .build());
    }

    private void sendReply() {
        String body = bodyField.getText().trim();
        if (body.isEmpty()) {
            status = "Reply can't be empty.";
            return;
        }

        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeUuid(postId);
        buf.writeString(body, SocialManager.REPLY_MAX_LEN);
        ClientPlayNetworking.send(SocialNetworking.C2S_REPLY, buf);

        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public void tick() {
        if (bodyField != null) bodyField.tick();
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (bodyField != null && bodyField.charTyped(chr, modifiers)) return true;
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (bodyField != null && bodyField.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        int left = this.width / 2 - 120;
        int top = this.height / 2 - 80;

        context.fill(left, top, left + 240, top + 110, 0xE0101420);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, top + 10, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Reply"), left, top + 15, 0xFFC8D5F0);

        if (bodyField != null) bodyField.render(context, mouseX, mouseY, delta);

        if (!status.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(status), this.width / 2, top + 85, 0xFFFFA0A0);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}