// src/main/java/net/seep/odd/abilities/fairy/client/RenameFlowerScreen.java
package net.seep.odd.abilities.fairy.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.seep.odd.block.falseflower.FalseFlowerTracker;

public final class RenameFlowerScreen extends Screen {
    private final Screen parent;
    private final int flowerId;
    private final String initial;

    private TextFieldWidget field;

    public RenameFlowerScreen(Screen parent, int flowerId, String initial) {
        super(Text.literal("Rename Flower"));
        this.parent = parent;
        this.flowerId = flowerId;
        this.initial = (initial == null ? "" : initial);
    }

    @Override
    protected void init() {
        int boxW = 220;
        int boxH = 80;

        int left = (this.width - boxW) / 2;
        int top  = (this.height - boxH) / 2;

        field = new TextFieldWidget(this.textRenderer, left + 10, top + 26, boxW - 20, 18, Text.literal("Name"));
        field.setText(initial);
        field.setMaxLength(64);
        this.addSelectableChild(field);
        this.setInitialFocus(field);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("OK"), b -> {
            FalseFlowerTracker.sendRename(flowerId, field.getText());
            MinecraftClient.getInstance().setScreen(parent);
        }).dimensions(left + 10, top + 52, 96, 18).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> {
            MinecraftClient.getInstance().setScreen(parent);
        }).dimensions(left + boxW - 10 - 96, top + 52, 96, 18).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // transparent overlay; no dirt bg
        int boxW = 220;
        int boxH = 80;
        int left = (this.width - boxW) / 2;
        int top  = (this.height - boxH) / 2;

        // faint dim only behind the popup
        ctx.fill(left, top, left + boxW, top + boxH, 0xAA000000);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, top + 8, 0xFFFFFF);

        field.render(ctx, mouseX, mouseY, delta);

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
