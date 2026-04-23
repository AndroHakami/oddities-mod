package net.seep.odd.client.device.info;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.seep.odd.client.device.social.DeviceMultilineTextEditor;
import net.seep.odd.client.device.social.SocialUrlTextureCache;
import net.seep.odd.device.info.InfoManager;
import net.seep.odd.device.info.InfoNetworking;

@Environment(EnvType.CLIENT)
public final class DeviceInfoComposeScreen extends Screen {
    private final Screen parent;

    private TextFieldWidget titleField;
    private TextFieldWidget sourceField;
    private TextFieldWidget imageUrlField;
    private DeviceMultilineTextEditor bodyEditor;

    private final List<String> imageUrls = new ArrayList<>();
    private String status = "";

    public DeviceInfoComposeScreen(Screen parent) {
        super(Text.literal("Post Info Article"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 170;
        int top = this.height / 2 - 120;

        this.titleField = new TextFieldWidget(this.textRenderer, left + 12, top + 26, 316, 18, Text.literal("Title"));
        this.titleField.setMaxLength(InfoManager.TITLE_MAX_LEN);
        this.titleField.setPlaceholder(Text.literal("Headline"));

        this.sourceField = new TextFieldWidget(this.textRenderer, left + 12, top + 58, 150, 18, Text.literal("Source"));
        this.sourceField.setMaxLength(InfoManager.SOURCE_MAX_LEN);
        this.sourceField.setPlaceholder(Text.literal("Written by / source"));

        this.imageUrlField = new TextFieldWidget(this.textRenderer, left + 12, top + 90, 228, 18, Text.literal("Image URL"));
        this.imageUrlField.setMaxLength(InfoManager.IMAGE_URL_MAX_LEN);
        this.imageUrlField.setPlaceholder(Text.literal("Direct image URL"));

        this.addSelectableChild(this.titleField);
        this.addSelectableChild(this.sourceField);
        this.addSelectableChild(this.imageUrlField);
        this.setInitialFocus(this.titleField);

        this.bodyEditor = new DeviceMultilineTextEditor(
                this.textRenderer,
                left + 12,
                top + 124,
                316,
                78,
                InfoManager.BODY_MAX_LEN
        );
        this.bodyEditor.setPlaceholder(Text.literal("Write the article body here...\nEnter = newline"));

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Add Image"), b -> addImage())
                .dimensions(left + 248, top + 90, 80, 18)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Remove Last"), b -> removeLastImage())
                .dimensions(left + 12, top + 206, 96, 18)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Post"), b -> create())
                .dimensions(left + 228, top + 206, 48, 18)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close())
                .dimensions(left + 280, top + 206, 48, 18)
                .build());
    }

    private void addImage() {
        String url = normalizeUrl(this.imageUrlField.getText());
        if (url == null) {
            this.status = "Enter a valid direct image URL.";
            return;
        }
        if (this.imageUrls.size() >= InfoManager.MAX_IMAGES) {
            this.status = "You've reached the image limit.";
            return;
        }

        this.imageUrls.add(url);
        this.imageUrlField.setText("");
        this.status = "Image added.";
    }

    private void removeLastImage() {
        if (this.imageUrls.isEmpty()) {
            this.status = "No images to remove.";
            return;
        }

        this.imageUrls.remove(this.imageUrls.size() - 1);
        this.status = "Removed the last image.";
    }

    private void create() {
        String source = this.sourceField.getText().trim();
        String title = this.titleField.getText().trim();
        String body = this.bodyEditor.getText();

        if (title.isEmpty()) {
            this.status = "Title can't be empty.";
            return;
        }
        if (body.trim().isEmpty()) {
            this.status = "Article body can't be empty.";
            return;
        }

        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeString(source, InfoManager.SOURCE_MAX_LEN);
        buf.writeString(title, InfoManager.TITLE_MAX_LEN);
        buf.writeString(body, InfoManager.BODY_MAX_LEN);
        buf.writeVarInt(this.imageUrls.size());
        for (String url : this.imageUrls) {
            buf.writeString(url, InfoManager.IMAGE_URL_MAX_LEN);
        }

        ClientPlayNetworking.send(InfoNetworking.C2S_CREATE_POST, buf);

        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public void tick() {
        if (titleField != null) titleField.tick();
        if (sourceField != null) sourceField.tick();
        if (imageUrlField != null) imageUrlField.tick();
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
        if (sourceField != null && sourceField.charTyped(chr, modifiers)) return true;
        if (imageUrlField != null && imageUrlField.charTyped(chr, modifiers)) return true;
        if (bodyEditor != null && bodyEditor.charTyped(chr, modifiers)) return true;
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (titleField != null && titleField.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (sourceField != null && sourceField.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (imageUrlField != null && imageUrlField.keyPressed(keyCode, scanCode, modifiers)) return true;
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

        context.fill(left, top, left + 340, top + 232, 0xE01B1612);
        context.fill(left + 1, top + 1, left + 339, top + 231, 0xD129221C);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, top + 8, 0xFFF9F0DE);

        context.drawTextWithShadow(this.textRenderer, Text.literal("Title"), left + 12, top + 16, 0xFFE6D5B8);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Source"), left + 12, top + 48, 0xFFE6D5B8);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Images"), left + 12, top + 80, 0xFFE6D5B8);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Body"), left + 12, top + 114, 0xFFE6D5B8);

        titleField.render(context, mouseX, mouseY, delta);
        sourceField.render(context, mouseX, mouseY, delta);
        imageUrlField.render(context, mouseX, mouseY, delta);
        bodyEditor.render(context, mouseX, mouseY, delta);

        int thumbX = left + 114;
        int thumbY = top + 206;
        int shown = Math.min(4, this.imageUrls.size());
        for (int i = 0; i < shown; i++) {
            SocialUrlTextureCache.drawContained(context, this.imageUrls.get(i), thumbX + (i * 26), thumbY, 22, 18);
        }

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Chars: " + bodyEditor.length() + "/" + InfoManager.BODY_MAX_LEN),
                left + 12,
                top + 220,
                0xFFF9F0DE
        );

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Images: " + this.imageUrls.size() + "/" + InfoManager.MAX_IMAGES),
                left + 170,
                top + 220,
                0xFFF9F0DE
        );

        if (!status.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(status), this.width / 2, top + 220, 0xFFFFD7A8);
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
}
