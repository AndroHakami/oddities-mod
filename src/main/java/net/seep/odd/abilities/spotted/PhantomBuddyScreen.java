package net.seep.odd.abilities.spotted;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

public class PhantomBuddyScreen extends HandledScreen<PhantomBuddyScreenHandler> {

    public PhantomBuddyScreen(PhantomBuddyScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 18*4 + 24 + 18*3 + 22 + 18 + 14; // ~206
    }

    @Override
    protected void init() {
        super.init();
        this.playerInventoryTitleY = this.titleY + 18*4 + 24 - 10;
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;

        ctx.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xD0101010);
        int gridW = 18*4 + 2;
        int gridX = x + (backgroundWidth - gridW) / 2;
        int gridY = y + 12;
        ctx.fill(gridX - 4, gridY - 4, gridX + gridW + 4, gridY + 18*4 + 6, 0x66000000);

        int invY = gridY + 18*4 + 18;
        ctx.fill(x + 6, invY - 6, x + backgroundWidth - 6, invY + 18*3 + 6 + 24, 0x66000000);
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        ctx.drawText(this.textRenderer, this.title, 8, 6, 0xE0E0E0, false);
        ctx.drawText(this.textRenderer, this.playerInventoryTitle, 8, this.playerInventoryTitleY, 0xE0E0E0, false);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }
}
