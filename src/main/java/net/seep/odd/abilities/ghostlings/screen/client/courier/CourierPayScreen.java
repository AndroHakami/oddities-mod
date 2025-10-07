package net.seep.odd.abilities.ghostlings.screen.client.courier;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.seep.odd.abilities.ghostlings.GhostPackets;
import net.seep.odd.abilities.ghostlings.screen.courier.CourierPayScreenHandler;

public class CourierPayScreen extends HandledScreen<CourierPayScreenHandler> {
    public CourierPayScreen(CourierPayScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        backgroundWidth = 176; backgroundHeight = 166;
    }

    @Override protected void init() {
        super.init();
        int cx = x + backgroundWidth/2;
        addDrawableChild(ButtonWidget.builder(Text.of("Start Delivery"), b ->
                GhostPackets.payAndStart(handler.ghostId)
        ).dimensions(cx-50, y + 60, 100, 20).build());
    }

    @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        renderBackground(draw);
        draw.drawCenteredTextWithShadow(textRenderer, Text.of("Insert "+handler.tearsNeeded+" Ghast Tears"), x + backgroundWidth/2, y + 20, 0xFFFFFF);
        super.render(draw, mouseX, mouseY, delta);
        drawMouseoverTooltip(draw, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {

    }
}
