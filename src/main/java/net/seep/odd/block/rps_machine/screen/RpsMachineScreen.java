package net.seep.odd.block.rps_machine.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.block.rps_machine.game.RpsEnemyType;
import net.seep.odd.block.rps_machine.game.RpsMove;
import net.seep.odd.block.rps_machine.game.RpsPhase;
import net.seep.odd.block.rps_machine.game.RpsRoundResult;

public class RpsMachineScreen extends HandledScreen<RpsMachineScreenHandler> {
    private static final Identifier BG =
            new Identifier(Oddities.MOD_ID, "textures/gui/rps_machine/rps_machine.png");

    private ButtonWidget rockButton;
    private ButtonWidget paperButton;
    private ButtonWidget scissorsButton;

    public RpsMachineScreen(RpsMachineScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 256;
        this.backgroundHeight = 180;
        this.playerInventoryTitleX = -9999;
        this.playerInventoryTitleY = -9999;
        this.titleX = 0;
        this.titleY = 0;
    }

    @Override
    protected void init() {
        super.init();
        this.titleX = this.x + 14;
        this.titleY = this.y + 10;

        this.rockButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("ROCK"), btn -> clickMove(0))
                .dimensions(this.x + 16, this.y + 144, 70, 20).build());

        this.paperButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("PAPER"), btn -> clickMove(1))
                .dimensions(this.x + 93, this.y + 144, 70, 20).build());

        this.scissorsButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("SCISSORS"), btn -> clickMove(2))
                .dimensions(this.x + 170, this.y + 144, 70, 20).build());

        updateButtons();
    }

    private void clickMove(int buttonId) {
        if (this.client != null && this.client.interactionManager != null) {
            this.client.interactionManager.clickButton(this.handler.syncId, buttonId);
        }
    }

    private void updateButtons() {
        boolean active = handler.getPhase() == RpsPhase.PLAYER_CHOOSE;
        if (rockButton != null) rockButton.active = active;
        if (paperButton != null) paperButton.active = active;
        if (scissorsButton != null) scissorsButton.active = active;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(BG, this.x, this.y, 0, 0, this.backgroundWidth, this.backgroundHeight);

        drawBar(context, this.x + 16, this.y + 56, 96, 10,
                handler.getEnemyHp(), Math.max(1, handler.getEnemyMaxHp()),
                0xFF3B2323, 0xFFE04D4D);

        drawBar(context, this.x + 144, this.y + 56, 96, 10,
                handler.getPlayerHp(), Math.max(1, handler.getPlayerMaxHp()),
                0xFF1E2A45, 0xFF4DB0FF);
    }

    private void drawBar(DrawContext context, int x, int y, int width, int height,
                         int value, int max, int bgColor, int fillColor) {
        context.fill(x, y, x + width, y + height, bgColor);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF111111);

        int innerWidth = width - 2;
        int fill = (int) ((value / (float) max) * innerWidth);
        context.fill(x + 1, y + 1, x + 1 + fill, y + height - 1, fillColor);
    }

    @Override
    public void handledScreenTick() {
        super.handledScreenTick();
        updateButtons();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);

        RpsEnemyType enemy = handler.getEnemyType();
        RpsMove playerMove = handler.getLastPlayerMove();
        RpsMove enemyMove = handler.getLastEnemyMove();
        RpsRoundResult result = handler.getLastRoundResult();

        context.drawText(this.textRenderer, this.title, this.x + 14, this.y + 10, 0xFFFFFF, false);
        context.drawText(this.textRenderer, "RPS ARCADE ONLINE", this.x + 14, this.y + 25, 0x7CFFB2, false);
        context.drawText(this.textRenderer, "ROUND " + handler.getRoundNumber(), this.x + 196, this.y + 25, 0xFFD55A, false);

        context.drawText(this.textRenderer, enemy.getDisplayName(), this.x + 16, this.y + 42, 0xFFFFFF, false);
        context.drawText(this.textRenderer, "YOU", this.x + 144, this.y + 42, 0xFFFFFF, false);

        context.drawText(this.textRenderer,
                handler.getEnemyHp() + " / " + Math.max(1, handler.getEnemyMaxHp()),
                this.x + 16, this.y + 68, 0xD0D0D0, false);

        context.drawText(this.textRenderer,
                handler.getPlayerHp() + " / " + handler.getPlayerMaxHp(),
                this.x + 144, this.y + 68, 0xD0D0D0, false);

        context.drawText(this.textRenderer, getStatusLine(), this.x + 16, this.y + 92, 0xEAEAEA, false);

        if (playerMove != null && enemyMove != null) {
            context.drawText(this.textRenderer,
                    "YOU: " + playerMove.getDisplayName(),
                    this.x + 16, this.y + 108, 0xB8E0FF, false);

            context.drawText(this.textRenderer,
                    "ENEMY: " + enemyMove.getDisplayName(),
                    this.x + 16, this.y + 120, 0xFFB8B8, false);
        }

        if (result != null) {
            int color = switch (result) {
                case WIN -> 0x7CFF7C;
                case LOSE -> 0xFF7C7C;
                case DRAW -> 0xFFD55A;
            };

            context.drawText(this.textRenderer,
                    getResultLine(result),
                    this.x + 144, this.y + 108, color, false);
        }

        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    private String getStatusLine() {
        return switch (handler.getPhase()) {
            case IDLE -> "Machine idle.";
            case PLAYER_CHOOSE -> "Choose Rock, Paper, or Scissors.";
            case VICTORY -> "Victory! Close the machine to finish.";
            case DEFEAT -> "Defeat... Close the machine to finish.";
        };
    }

    private String getResultLine(RpsRoundResult result) {
        return switch (result) {
            case WIN -> "You won the clash!";
            case LOSE -> "The enemy won the clash!";
            case DRAW -> "Draw round.";
        };
    }
}