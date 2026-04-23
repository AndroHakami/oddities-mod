package net.seep.odd.quest.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.seep.odd.quest.QuestNetworking;

import java.util.ArrayList;
import java.util.List;

public final class LoreQuizScreen extends Screen {
    private final int librarianEntityId;
    private final String questId;
    private final String volumeId;
    private final String volumeTitle;
    private final String question;
    private final String[] answers;
    private final Screen parent;

    private final List<ButtonWidget> answerButtons = new ArrayList<>();
    private ButtonWidget closeButton;
    private boolean waitingForServer = false;

    public LoreQuizScreen(int librarianEntityId, String questId, String volumeId, String volumeTitle, String question, String[] answers) {
        this(null, librarianEntityId, questId, volumeId, volumeTitle, question, answers);
    }

    public LoreQuizScreen(Screen parent, int librarianEntityId, String questId, String volumeId, String volumeTitle, String question, String[] answers) {
        super(Text.literal("Lore Quiz"));
        this.parent = parent;
        this.librarianEntityId = librarianEntityId;
        this.questId = questId;
        this.volumeId = volumeId;
        this.volumeTitle = volumeTitle;
        this.question = question;
        this.answers = answers;
    }

    public String quizQuestId() {
        return this.questId;
    }

    @Override
    protected void init() {
        this.answerButtons.clear();

        int left = this.width / 2 - 150;
        int top = this.height / 2 - 90;

        for (int i = 0; i < this.answers.length; i++) {
            final int idx = i;
            ButtonWidget button = this.addDrawableChild(
                    ButtonWidget.builder(Text.literal(this.answers[i]), b -> submit(idx))
                            .dimensions(left + 10, top + 76 + i * 24, 280, 20)
                            .build()
            );
            this.answerButtons.add(button);
        }

        this.closeButton = this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Close"), b -> this.close())
                        .dimensions(left + 100, top + 176, 100, 20)
                        .build()
        );

        refreshButtonState();
    }

    private void submit(int index) {
        if (this.waitingForServer) return;

        this.waitingForServer = true;
        refreshButtonState();

        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeInt(this.librarianEntityId);
        buf.writeString(this.questId);
        buf.writeInt(index);
        ClientPlayNetworking.send(QuestNetworking.C2S_SUBMIT_LORE_QUIZ, buf);
    }

    private void refreshButtonState() {
        for (ButtonWidget button : this.answerButtons) {
            button.active = !this.waitingForServer;
        }
        if (this.closeButton != null) {
            this.closeButton.active = !this.waitingForServer;
        }
    }

    @Override
    public void close() {
        if (this.client != null && this.parent != null) {
            this.client.setScreen(this.parent);
            return;
        }
        super.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        int left = this.width / 2 - 150;
        int top = this.height / 2 - 90;

        context.fillGradient(0, 0, this.width, this.height, 0x7A000000, 0x9A000000);
        context.fill(left, top, left + 300, top + 204, 0xE0121018);
        context.drawBorder(left, top, 300, 204, 0xA0FFFFFF);

        context.drawText(this.textRenderer, Text.literal("Librarian Quiz"), left + 10, top + 10, 0xFFFFFF, false);
        context.drawText(this.textRenderer, Text.literal(this.volumeTitle), left + 10, top + 24, 0xFFD890, false);

        int y = top + 44;
        List<OrderedText> wrapped = this.textRenderer.wrapLines(Text.literal(this.question), 280);
        for (OrderedText line : wrapped) {
            context.drawText(this.textRenderer, line, left + 10, y, 0xE0E0E0, false);
            y += 10;
        }

        if (this.waitingForServer) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("Checking answer..."),
                    this.width / 2,
                    top + 158,
                    0xFFE38A
            );
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}