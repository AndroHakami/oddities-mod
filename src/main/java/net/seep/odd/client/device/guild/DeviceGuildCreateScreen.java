package net.seep.odd.client.device.guild;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.seep.odd.device.guild.GuildColorOption;
import net.seep.odd.device.guild.GuildManager;
import net.seep.odd.device.guild.GuildNetworking;

@Environment(EnvType.CLIENT)
public final class DeviceGuildCreateScreen extends Screen {
    private final Screen parent;

    private TextFieldWidget nameField;
    private TextFieldWidget prefixField;
    private ButtonWidget createButton;
    private GuildColorOption color = GuildColorOption.AZURE;
    private String status = "";

    public DeviceGuildCreateScreen(Screen parent) {
        super(Text.literal("Create Team"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 160;
        int top = this.height / 2 - 86;

        this.nameField = new TextFieldWidget(this.textRenderer, left + 12, top + 34, 296, 18, Text.literal("Team Name"));
        this.nameField.setMaxLength(GuildManager.NAME_MAX_LEN);
        this.nameField.setPlaceholder(Text.literal("Team name"));

        this.prefixField = new TextFieldWidget(this.textRenderer, left + 12, top + 72, 296, 18, Text.literal("Prefix"));
        this.prefixField.setMaxLength(GuildManager.PREFIX_MAX_LEN);
        this.prefixField.setPlaceholder(Text.literal("1 character / emoji"));

        this.addSelectableChild(this.nameField);
        this.addSelectableChild(this.prefixField);
        this.setInitialFocus(this.nameField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Prev"), b -> this.color = this.color.previous())
                .dimensions(left + 12, top + 106, 52, 18)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Next"), b -> this.color = this.color.next())
                .dimensions(left + 256, top + 106, 52, 18)
                .build());

        this.createButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Create"), b -> create())
                .dimensions(left + 176, top + 140, 64, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close())
                .dimensions(left + 244, top + 140, 64, 20)
                .build());

        updateFormState();
    }

    private void create() {
        String name = this.nameField.getText().trim();
        String prefix = this.prefixField.getText().trim();

        if (name.isEmpty()) {
            this.status = "Enter a team name.";
            return;
        }
        if (!GuildManager.isSingleVisibleCharacter(prefix)) {
            this.status = "Prefix must be exactly 1 character.";
            return;
        }

        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeString(name, GuildManager.NAME_MAX_LEN);
        buf.writeString(prefix, GuildManager.PREFIX_MAX_LEN);
        buf.writeString(this.color.id(), 24);
        ClientPlayNetworking.send(GuildNetworking.C2S_CREATE_TEAM, buf);

        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    private void updateFormState() {
        if (this.createButton == null) return;

        String name = this.nameField == null ? "" : this.nameField.getText().trim();
        String prefix = this.prefixField == null ? "" : this.prefixField.getText().trim();
        this.createButton.active = !name.isEmpty() && GuildManager.isSingleVisibleCharacter(prefix);
    }

    @Override
    public void tick() {
        if (nameField != null) nameField.tick();
        if (prefixField != null) prefixField.tick();
        updateFormState();
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (nameField != null && nameField.charTyped(chr, modifiers)) return true;
        if (prefixField != null && prefixField.charTyped(chr, modifiers)) return true;
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameField != null && nameField.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (prefixField != null && prefixField.keyPressed(keyCode, scanCode, modifiers)) return true;
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

        int left = this.width / 2 - 160;
        int top = this.height / 2 - 86;

        context.fill(left, top, left + 320, top + 168, 0xE0101420);
        context.fill(left + 1, top + 1, left + 319, top + 167, 0xD1161C29);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, top + 10, 0xFFFFFFFF);

        context.drawTextWithShadow(this.textRenderer, Text.literal("Team name"), left + 12, top + 22, 0xFFC8D5F0);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Prefix"), left + 12, top + 60, 0xFFC8D5F0);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Color"), left + 12, top + 96, 0xFFC8D5F0);

        nameField.render(context, mouseX, mouseY, delta);
        prefixField.render(context, mouseX, mouseY, delta);

        int prefixCount = GuildManager.visibleCharacterCount(this.prefixField == null ? "" : this.prefixField.getText().trim());
        int prefixColor = prefixCount == 1 ? 0xFF8FF1A7 : 0xFFFFD07A;
        context.drawTextWithShadow(this.textRenderer, Text.literal("1 visible character only (now: " + prefixCount + ")"), left + 168, top + 60, prefixColor);

        context.fill(left + 74, top + 108, left + 246, top + 122, 0x90212938);
        context.fill(left + 78, top + 110, left + 92, top + 120, this.color.color());
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.color.label()), left + 160, top + 111, 0xFFF3F6FF);

        if (!status.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(status), this.width / 2, top + 142, 0xFFFFD0A0);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
