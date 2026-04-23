package net.seep.odd.client.device.guild;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.seep.odd.client.device.social.DeviceMultilineTextEditor;
import net.seep.odd.device.guild.GuildManager;
import net.seep.odd.device.guild.GuildNetworking;

@Environment(EnvType.CLIENT)
public final class DeviceGuildNotesEditScreen extends Screen {
    private final Screen parent;
    private final String initialNotes;

    private DeviceMultilineTextEditor editor;

    public DeviceGuildNotesEditScreen(Screen parent, String initialNotes) {
        super(Text.literal("Team Notes"));
        this.parent = parent;
        this.initialNotes = initialNotes == null ? "" : initialNotes;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 160;
        int top = this.height / 2 - 115;

        this.editor = new DeviceMultilineTextEditor(
                this.textRenderer,
                left + 12,
                top + 28,
                296,
                154,
                GuildManager.NOTES_MAX_LEN
        );
        this.editor.setPlaceholder(Text.literal("Write team notes here...\nEnter = newline"));
        this.editor.setText(this.initialNotes);
        this.editor.setFocused(true);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b -> save())
                .dimensions(left + 114, top + 190, 92, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close())
                .dimensions(left + 216, top + 190, 92, 20)
                .build());
    }

    private void save() {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeString(editor.getText(), GuildManager.NOTES_MAX_LEN);
        ClientPlayNetworking.send(GuildNetworking.C2S_SET_NOTES, buf);

        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public void tick() {
        if (editor != null) editor.tick();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (editor != null && editor.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (editor != null) {
            editor.setFocused(false);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (editor != null && editor.mouseScrolled(mouseX, mouseY, amount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (editor != null && editor.charTyped(chr, modifiers)) return true;
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editor != null && editor.keyPressed(keyCode, scanCode, modifiers)) return true;
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
        int top = this.height / 2 - 115;

        context.fill(left, top, left + 320, top + 218, 0xE0101420);
        context.fill(left + 1, top + 1, left + 319, top + 217, 0xD1161C29);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, top + 8, 0xFFFFFFFF);

        if (editor != null) {
            editor.render(context, mouseX, mouseY, delta);
        }

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Chars: " + (editor == null ? 0 : editor.length()) + "/" + GuildManager.NOTES_MAX_LEN),
                left + 12,
                top + 184,
                0xFFE4EDFF
        );

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Lines: " + (editor == null ? 0 : editor.lineCount())),
                left + 190,
                top + 184,
                0xFFE4EDFF
        );

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
