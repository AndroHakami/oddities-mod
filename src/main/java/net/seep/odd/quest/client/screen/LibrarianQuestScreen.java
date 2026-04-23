package net.seep.odd.quest.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.seep.odd.lore.AtheneumLoreBooks;
import net.seep.odd.quest.QuestDefinition;
import net.seep.odd.quest.QuestNetworking;
import net.seep.odd.quest.QuestObjectiveData;
import net.seep.odd.quest.client.QuestClientState;
import net.seep.odd.sound.ModSounds;

import java.util.List;

public final class LibrarianQuestScreen extends Screen {
    private static final int PANEL_W = 408;
    private static final int PANEL_H = 228;
    private static final int LIST_X = 14;
    private static final int LIST_Y = 34;
    private static final int LIST_W = 182;
    private static final int LIST_H = 140;
    private static final int DETAILS_X = 206;
    private static final int DETAILS_Y = 34;
    private static final int DETAILS_W = 188;
    private static final int DETAILS_H = 140;
    private static final int ROW_H = 30;
    private static final int ROW_GAP = 2;

    private final int librarianEntityId;

    private ButtonWidget acceptButton;
    private ButtonWidget claimButton;
    private ButtonWidget quizButton;
    private ButtonWidget abandonButton;
    private ButtonWidget closeButton;

    private QuestDefinition selectedQuest;
    private SoundInstance screenMusic;
    private int scrollOffset = 0;

    public LibrarianQuestScreen(int librarianEntityId) {
        super(Text.literal("Librarian"));
        this.librarianEntityId = librarianEntityId;
    }

    @Override
    protected void init() {
        int left = left();
        int top = top();

        this.acceptButton = this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Accept"), button -> acceptSelected())
                        .dimensions(left + 206, top + 194, 90, 20)
                        .build()
        );

        this.claimButton = this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Claim"), button -> openRewardScreen())
                        .dimensions(left + 206, top + 194, 90, 20)
                        .build()
        );

        this.quizButton = this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Quiz"), button -> openQuizScreen())
                        .dimensions(left + 206, top + 194, 90, 20)
                        .build()
        );

        this.closeButton = this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Close"), button -> this.close())
                        .dimensions(left + 304, top + 194, 90, 20)
                        .build()
        );

        this.abandonButton = this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Abandon"), button -> abandonQuest())
                        .dimensions(left + 14, top + 194, 112, 20)
                        .build()
        );

        QuestClientState state = QuestClientState.INSTANCE;
        if (this.selectedQuest == null) {
            if (state.hasActiveQuest() && state.quest(state.activeQuestId()) != null) {
                this.selectedQuest = state.quest(state.activeQuestId());
            } else if (!state.sortedQuests().isEmpty()) {
                this.selectedQuest = state.sortedQuests().get(0);
            }
        }

        clampScroll();
        startScreenMusic();
        refreshButtons();
    }

    private int left() { return this.width / 2 - PANEL_W / 2; }
    private int top() { return this.height / 2 - PANEL_H / 2; }
    private int listLeft() { return left() + LIST_X; }
    private int listTop() { return top() + LIST_Y; }
    private int listRight() { return listLeft() + LIST_W; }
    private int listBottom() { return listTop() + LIST_H; }
    private int visibleRows() { return Math.max(1, LIST_H / ROW_H); }
    private List<QuestDefinition> quests() { return QuestClientState.INSTANCE.sortedQuests(); }
    private int maxScroll() { return Math.max(0, quests().size() - visibleRows()); }

    private void clampScroll() {
        int max = maxScroll();
        if (this.scrollOffset < 0) this.scrollOffset = 0;
        if (this.scrollOffset > max) this.scrollOffset = max;
    }

    private void startScreenMusic() {
        if (this.client == null || this.screenMusic != null) return;
        this.screenMusic = PositionedSoundInstance.master(ModSounds.QUEST_SCREEN, 1.0f, 1.0f);
        this.client.getSoundManager().play(this.screenMusic);
    }

    private void stopScreenMusic() {
        if (this.client == null) return;
        if (this.screenMusic != null) {
            this.client.getSoundManager().stop(this.screenMusic);
            this.screenMusic = null;
        }
    }

    private void refreshButtons() {
        QuestClientState state = QuestClientState.INSTANCE;

        boolean hasSelection = this.selectedQuest != null;
        boolean unlocked = hasSelection && state.isUnlocked(this.selectedQuest);
        boolean claimed = hasSelection && state.isClaimed(this.selectedQuest.id);
        boolean canClaim = hasSelection && state.canClaim(this.selectedQuest);
        boolean canQuiz = hasSelection && isQuizReady(this.selectedQuest);

        this.acceptButton.visible = hasSelection && unlocked && !claimed && !state.hasActiveQuest();
        this.acceptButton.active = this.acceptButton.visible;

        this.claimButton.visible = canClaim;
        this.claimButton.active = canClaim;

        this.quizButton.visible = canQuiz;
        this.quizButton.active = canQuiz;

        this.abandonButton.visible = state.hasActiveQuest();
        this.abandonButton.active = state.hasActiveQuest();

        this.closeButton.visible = true;
        this.closeButton.active = true;
    }

    private boolean isQuizReady(QuestDefinition def) {
        QuestClientState state = QuestClientState.INSTANCE;
        if (def == null || def.objective == null) return false;
        if (def.objective.type != QuestObjectiveData.Type.LORE_BOOK_QUIZ) return false;
        if (!state.isActive(def.id)) return false;
        if (state.canClaim(def)) return false;
        if (state.isClaimed(def.id)) return false;

        // This is the important part:
        // if your quest shows 1/1 complete, the quiz button should appear.
        return state.progressFor(def) >= Math.max(1, def.goal());
    }

    private void ensureSelectionValid() {
        List<QuestDefinition> quests = quests();
        if (quests.isEmpty()) {
            this.selectedQuest = null;
            return;
        }
        if (this.selectedQuest == null || QuestClientState.INSTANCE.quest(this.selectedQuest.id) == null) {
            this.selectedQuest = quests.get(0);
        }
    }

    private void acceptSelected() {
        if (this.selectedQuest == null) return;
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeInt(this.librarianEntityId);
        buf.writeString(this.selectedQuest.id);
        ClientPlayNetworking.send(QuestNetworking.C2S_ACCEPT, buf);
    }

    private void abandonQuest() {
        ClientPlayNetworking.send(
                QuestNetworking.C2S_ABANDON,
                net.fabricmc.fabric.api.networking.v1.PacketByteBufs.empty()
        );
    }

    private void openRewardScreen() {
        if (this.selectedQuest == null || this.client == null) return;
        this.client.setScreen(new QuestRewardScreen(this, this.selectedQuest, this.librarianEntityId));
    }

    private void openQuizScreen() {
        if (this.selectedQuest == null || this.client == null) return;

        QuestClientState state = QuestClientState.INSTANCE;
        String loreId = state.requestedLoreId();
        if (loreId.isBlank()) return;

        AtheneumLoreBooks.Volume volume = AtheneumLoreBooks.get(loreId);
        if (volume == null) return;

        this.client.setScreen(new LoreQuizScreen(
                this,
                this.librarianEntityId,
                this.selectedQuest.id,
                volume.id(),
                volume.title(),
                volume.question(),
                volume.answers()
        ));
    }

    @Override
    public void tick() {
        super.tick();
        ensureSelectionValid();
        clampScroll();
        if (this.selectedQuest != null) {
            QuestDefinition updated = QuestClientState.INSTANCE.quest(this.selectedQuest.id);
            if (updated != null) this.selectedQuest = updated;
        }
        refreshButtons();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleQuestListClick(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleQuestListClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int x1 = listLeft();
        int y1 = listTop();
        int x2 = listRight();
        int y2 = listBottom();
        if (mouseX < x1 || mouseX >= x2 || mouseY < y1 || mouseY >= y2) return false;

        int localY = (int) mouseY - y1 - 4;
        if (localY < 0) return true;
        int rowStride = ROW_H + ROW_GAP;
        int rowIndex = localY / rowStride;
        int rowRemainder = localY % rowStride;
        if (rowRemainder >= ROW_H) return true;

        int questIndex = this.scrollOffset + rowIndex;
        List<QuestDefinition> quests = quests();
        if (questIndex >= 0 && questIndex < quests.size()) {
            this.selectedQuest = quests.get(questIndex);
            refreshButtons();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseX >= listLeft() && mouseX < listRight() && mouseY >= listTop() && mouseY < listBottom()) {
            if (amount < 0.0D) this.scrollOffset++;
            else if (amount > 0.0D) this.scrollOffset--;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public void close() {
        stopScreenMusic();
        super.close();
    }

    @Override
    public void removed() {
        stopScreenMusic();
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        ensureSelectionValid();
        int left = left();
        int top = top();
        int right = left + PANEL_W;
        int bottom = top + PANEL_H;
        int listLeft = left + LIST_X;
        int listTop = top + LIST_Y;
        int detailsLeft = left + DETAILS_X;
        int detailsTop = top + DETAILS_Y;

        context.fillGradient(0, 0, this.width, this.height, 0x7A000000, 0x9A000000);
        context.fill(left, top, right, bottom, 0xE0101016);
        context.drawBorder(left, top, PANEL_W, PANEL_H, 0xA0FFFFFF);
        context.drawText(this.textRenderer, Text.literal("Librarian Quests"), left + 14, top + 12, 0xFFFFFF, false);
        renderLevelBar(context, left + 112, top + 12, 282, 10);

        context.fill(listLeft, listTop, listLeft + LIST_W, listTop + LIST_H, 0x88070910);
        context.drawBorder(listLeft, listTop, LIST_W, LIST_H, 0x55FFFFFF);
        context.fill(detailsLeft, detailsTop, detailsLeft + DETAILS_W, detailsTop + DETAILS_H, 0x88070910);
        context.drawBorder(detailsLeft, detailsTop, DETAILS_W, DETAILS_H, 0x55FFFFFF);

        renderQuestRows(context, mouseX, mouseY, listLeft, listTop, LIST_W, LIST_H);
        super.render(context, mouseX, mouseY, delta);
        renderDetailsPanel(context, detailsLeft, detailsTop, DETAILS_W, DETAILS_H);
    }

    private void renderQuestRows(DrawContext context, int mouseX, int mouseY, int x, int y, int width, int height) {
        List<QuestDefinition> quests = quests();
        int visible = visibleRows();
        for (int i = 0; i < visible; i++) {
            int questIndex = this.scrollOffset + i;
            if (questIndex >= quests.size()) break;
            QuestDefinition def = quests.get(questIndex);

            int rowX = x + 4;
            int rowY = y + 4 + i * (ROW_H + ROW_GAP);
            int rowW = width - 8;
            int rowH = ROW_H;
            boolean hovered = mouseX >= rowX && mouseX < rowX + rowW && mouseY >= rowY && mouseY < rowY + rowH;
            boolean selected = this.selectedQuest != null && this.selectedQuest.id.equals(def.id);

            QuestClientState state = QuestClientState.INSTANCE;
            int bg = 0x66000000;
            if (state.isClaimed(def.id)) bg = 0x66356622;
            else if (state.canClaim(def)) bg = 0x66775415;
            else if (isQuizReady(def)) bg = 0x66593d12;
            else if (!state.isUnlocked(def)) bg = 0x66402020;
            else if (state.isActive(def.id)) bg = 0x66314E74;
            if (hovered) bg |= 0x00181818;

            context.fill(rowX, rowY, rowX + rowW, rowY + rowH, bg);
            context.drawBorder(rowX, rowY, rowW, rowH, selected ? 0xCCFFFFFF : 0x44FFFFFF);
            try {
                context.drawTexture(def.iconId(), rowX + 6, rowY + 5, 0, 0, 20, 20, 20, 20);
            } catch (Exception ignored) {
            }
            context.drawText(this.textRenderer, Text.literal(def.title), rowX + 32, rowY + 11, 0xFFFFFF, false);
        }
        renderScrollbar(context, x + width - 6, y + 4, height - 8, quests.size(), visible);
    }

    private void renderScrollbar(DrawContext context, int x, int y, int height, int total, int visible) {
        if (total <= visible) return;
        context.fill(x, y, x + 2, y + height, 0x44FFFFFF);
        int thumbHeight = Math.max(16, (int) (height * (visible / (float) total)));
        int maxThumbY = height - thumbHeight;
        int thumbY = maxScroll() == 0 ? 0 : (int) ((scrollOffset / (float) maxScroll()) * maxThumbY);
        context.fill(x - 1, y + thumbY, x + 3, y + thumbY + thumbHeight, 0xCCFFFFFF);
    }

    private void renderLevelBar(DrawContext context, int x, int y, int width, int height) {
        QuestClientState state = QuestClientState.INSTANCE;
        int floor = state.levelFloorXp();
        int ceil = Math.max(floor + 1, state.levelCeilXp());
        int progress = Math.min(width, (int) (((state.questXp() - floor) / (float) (ceil - floor)) * width));
        context.drawText(this.textRenderer, Text.literal("Lvl " + state.level()), x, y - 10, 0xFFE39B3A, false);
        context.fill(x, y, x + width, y + height, 0x66000000);
        context.fill(x + 1, y + 1, x + progress, y + height - 1, 0xFFD79A2B);
        context.drawBorder(x, y, width, height, 0xAAFFFFFF);
    }

    private void renderDetailsPanel(DrawContext context, int x, int y, int width, int height) {
        QuestClientState state = QuestClientState.INSTANCE;
        if (this.selectedQuest == null) {
            context.drawText(this.textRenderer, Text.literal("Select a quest from the list."), x + 10, y + 10, 0xFFFFFF, false);
            return;
        }

        context.drawText(this.textRenderer, Text.literal(this.selectedQuest.title), x + 10, y + 10, 0xFFFFFF, false);
        try {
            context.drawTexture(this.selectedQuest.iconId(), x + width - 30, y + 8, 0, 0, 20, 20, 20, 20);
        } catch (Exception ignored) {
        }

        int textY = y + 34;
        for (OrderedText line : this.textRenderer.wrapLines(Text.literal(this.selectedQuest.description), width - 22)) {
            context.drawText(this.textRenderer, line, x + 10, textY, 0xE0E0E0, false);
            textY += 10;
        }

        String status;
        int statusColor;
        if (!state.isUnlocked(this.selectedQuest)) {
            status = "Locked until level " + this.selectedQuest.unlockLevel;
            statusColor = 0xFF9C9C;
        } else if (state.isClaimed(this.selectedQuest.id)) {
            status = "Completed";
            statusColor = 0x8FFF8F;
        } else if (state.canClaim(this.selectedQuest)) {
            status = "Reward ready to claim";
            statusColor = 0xFFE38A;
        } else if (isQuizReady(this.selectedQuest)) {
            status = "Ready for quiz";
            statusColor = 0xFFE38A;
        } else if (state.isActive(this.selectedQuest.id)) {
            status = state.objectiveHint().isBlank()
                    ? "In Progress: " + state.progressFor(this.selectedQuest) + "/" + this.selectedQuest.goal()
                    : state.objectiveHint();
            statusColor = 0xFFFFFF;
        } else if (state.hasActiveQuest()) {
            status = "Another quest is active";
            statusColor = 0xFFBABA;
        } else {
            status = "Available";
            statusColor = 0xA8FF9A;
        }

        context.drawText(this.textRenderer, Text.literal(status), x + 10, y + height - 28, statusColor, false);
        context.drawText(this.textRenderer, Text.literal("Reward XP: " + this.selectedQuest.questXp), x + 10, y + height - 16, 0xA8A8FF, false);
    }
}