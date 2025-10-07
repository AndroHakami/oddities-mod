package net.seep.odd.abilities.ghostlings.screen.inventory;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

public class GhostCargoScreen extends HandledScreen<GhostCargoScreenHandler> {
    public GhostCargoScreen(GhostCargoScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 9*18 + 16; // 9 cols + padding
        this.backgroundHeight = 9*18 + 12 + 76 + 16; // cargo + gap + player inv + padding
    }

    @Override
    protected void init() {
        super.init();
        this.titleX = this.x + 8;
        this.titleY = this.y + 6;
    }

    @Override
    public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        renderBackground(draw);
        super.render(draw, mouseX, mouseY, delta);
        drawMouseoverTooltip(draw, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        // simple flat panel
        int r = 0x55_000000;
        ctx.fill(x, y, x + backgroundWidth, y + backgroundHeight, r);

        // titles
        ctx.drawTextWithShadow(textRenderer, title, x + 8, y + 6, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, this.playerInventoryTitle, x + 8, y + 9*18 + 18, 0xCCCCCC);
    }
}
