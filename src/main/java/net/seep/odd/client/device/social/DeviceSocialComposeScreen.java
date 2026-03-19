package net.seep.odd.client.device.social;

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
public final class DeviceSocialComposeScreen extends Screen {
    private final Screen parent;

    private TextFieldWidget titleField;
    private TextFieldWidget mainUrlField;
    private DeviceMultilineTextEditor bodyEditor;

    private String mainImageUrl;
    private String status = "";

    public DeviceSocialComposeScreen(Screen parent) {
        super(Text.literal("Create Post"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 170;
        int top = this.height / 2 - 120;

        this.titleField = new TextFieldWidget(this.textRenderer, left + 12, top + 26, 316, 18, Text.literal("Title"));
        this.titleField.setMaxLength(SocialManager.TITLE_MAX_LEN);
        this.titleField.setPlaceholder(Text.literal("Post title"));

        this.mainUrlField = new TextFieldWidget(this.textRenderer, left + 12, top + 58, 228, 18, Text.literal("Main URL"));
        this.mainUrlField.setMaxLength(SocialManager.IMAGE_URL_MAX_LEN);
        this.mainUrlField.setPlaceholder(Text.literal("Direct image URL (.png/.jpg/.gif)"));

        this.addSelectableChild(this.titleField);
        this.addSelectableChild(this.mainUrlField);
        this.setInitialFocus(this.titleField);

        this.bodyEditor = new DeviceMultilineTextEditor(
                this.textRenderer,
                left + 12,
                top + 92,
                316,
                104,
                SocialManager.BODY_MAX_LEN
        );
        this.bodyEditor.setPlaceholder(Text.literal("Write your post here...\nEnter = newline"));

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Set Main"), b -> setMainUrl())
                .dimensions(left + 248, top + 58, 80, 18)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Clear Main"), b -> {
                    this.mainImageUrl = null;
                    this.status = "Main image cleared.";
                })
                .dimensions(left + 12, top + 206, 92, 18)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Post"), b -> create())
                .dimensions(left + 228, top + 206, 48, 18)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> {
                    if (this.client != null) this.client.setScreen(parent);
                })
                .dimensions(left + 280, top + 206, 48, 18)
                .build());
    }

    private void setMainUrl() {
        String url = normalizeUrl(mainUrlField.getText());
        if (url == null) {
            this.status = "Enter a valid direct image URL.";
            return;
        }

        this.mainImageUrl = url;
        this.mainUrlField.setText("");
        this.status = "Main image URL set.";
    }

    private void create() {
        String title = titleField.getText().trim();
        String body = bodyEditor.getText();

        if (title.isEmpty()) {
            status = "Title can't be empty.";
            return;
        }

        if (body.trim().isEmpty()) {
            status = "Post text can't be empty.";
            return;
        }

        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeString(title, SocialManager.TITLE_MAX_LEN);
        buf.writeString(body, SocialManager.BODY_MAX_LEN);

        buf.writeBoolean(hasText(mainImageUrl));
        if (hasText(mainImageUrl)) {
            buf.writeString(mainImageUrl, SocialManager.IMAGE_URL_MAX_LEN);
        }

        ClientPlayNetworking.send(SocialNetworking.C2S_CREATE_POST, buf);

        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public void tick() {
        if (titleField != null) titleField.tick();
        if (mainUrlField != null) mainUrlField.tick();
        if (bodyEditor != null) bodyEditor.tick();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (bodyEditor != null && bodyEditor.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (bodyEditor != null) {
            bodyEditor.setFocused(false);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (bodyEditor != null && bodyEditor.mouseScrolled(mouseX, mouseY, amount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (titleField != null && titleField.charTyped(chr, modifiers)) return true;
        if (mainUrlField != null && mainUrlField.charTyped(chr, modifiers)) return true;
        if (bodyEditor != null && bodyEditor.charTyped(chr, modifiers)) return true;
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (titleField != null && titleField.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (mainUrlField != null && mainUrlField.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (bodyEditor != null && bodyEditor.keyPressed(keyCode, scanCode, modifiers)) return true;
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

        int left = this.width / 2 - 170;
        int top = this.height / 2 - 120;

        context.fill(left, top, left + 340, top + 232, 0xE0101420);
        context.fill(left + 1, top + 1, left + 339, top + 231, 0xD1161C29);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, top + 8, 0xFFFFFFFF);

        context.drawTextWithShadow(this.textRenderer, Text.literal("Title"), left + 12, top + 16, 0xFFC8D5F0);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Main image URL"), left + 12, top + 48, 0xFFC8D5F0);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Body"), left + 12, top + 82, 0xFFC8D5F0);

        titleField.render(context, mouseX, mouseY, delta);
        mainUrlField.render(context, mouseX, mouseY, delta);
        bodyEditor.render(context, mouseX, mouseY, delta);

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Chars: " + bodyEditor.length() + "/" + SocialManager.BODY_MAX_LEN),
                left + 12,
                top + 202,
                0xFFE4EDFF
        );

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Lines: " + bodyEditor.lineCount()),
                left + 138,
                top + 202,
                0xFFE4EDFF
        );

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Main: " + (hasText(mainImageUrl) ? "set" : "none")),
                left + 12,
                top + 220,
                0xFFBFD0EC
        );

        if (hasText(mainImageUrl)) {
            SocialUrlTextureCache.drawContained(context, mainImageUrl, left + 250, top + 196, 76, 28);
        }

        if (!status.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(status), this.width / 2, top + 220, 0xFFFFD0A0);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static String normalizeUrl(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        if (s.startsWith("http://") || s.startsWith("https://")) return s;
        return null;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}