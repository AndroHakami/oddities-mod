package net.seep.odd.quest.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.seep.odd.quest.QuestNetworking;

import java.util.List;

public final class ScaredRascalDialogScreen extends Screen {
    private final int rascalEntityId;
    private final String dialogTitle;
    private final String line;
    private final String buttonText;

    private ButtonWidget continueButton;

    public ScaredRascalDialogScreen(int rascalEntityId, String line) {
        this(rascalEntityId, "Scared Rascal", line, "continue");
    }

    public ScaredRascalDialogScreen(int rascalEntityId, String dialogTitle, String line, String buttonText) {
        super(Text.literal(dialogTitle == null || dialogTitle.isBlank() ? "Scared Rascal" : dialogTitle));
        this.rascalEntityId = rascalEntityId;
        this.dialogTitle = dialogTitle == null || dialogTitle.isBlank() ? "Scared Rascal" : dialogTitle;
        this.line = line == null ? "" : line;
        this.buttonText = buttonText == null || buttonText.isBlank() ? "continue" : buttonText;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 150;
        int top = this.height / 2 - 70;

        this.continueButton = this.addDrawableChild(
                ButtonWidget.builder(Text.literal(this.buttonText), button -> submit())
                        .dimensions(left + 100, top + 96, 100, 20)
                        .build()
        );
    }

    private void submit() {
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeInt(this.rascalEntityId);
        ClientPlayNetworking.send(QuestNetworking.C2S_CONTINUE_SCARED_RASCAL_DIALOG, buf);
        this.close();
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(null);
            return;
        }
        super.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        int left = this.width / 2 - 150;
        int top = this.height / 2 - 70;

        context.fillGradient(0, 0, this.width, this.height, 0x7A000000, 0x96000000);
        context.fill(left, top, left + 300, top + 130, 0xE0121018);
        context.drawBorder(left, top, 300, 130, 0xA0FFFFFF);

        context.drawText(this.textRenderer, Text.literal(this.dialogTitle), left + 10, top + 12, 0xFFFFFF, false);

        int y = top + 34;
        List<OrderedText> wrapped = this.textRenderer.wrapLines(Text.literal(this.line), 280);
        for (OrderedText orderedText : wrapped) {
            context.drawText(this.textRenderer, orderedText, left + 10, y, 0xE8E8E8, false);
            y += 10;
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
