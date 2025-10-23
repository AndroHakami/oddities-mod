package net.seep.odd.abilities.ghostlings.screen.client.courier;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.abilities.ghostlings.GhostPackets;

public class CourierConfirmScreen extends Screen {
    private final int ghostId;
    private final BlockPos target;
    private final double distance;
    private final int tears;

    public CourierConfirmScreen(int ghostId, BlockPos target, double distance, int tears) {
        super(Text.of("Courier: Confirm"));
        this.ghostId = ghostId;
        this.target = target;
        this.distance = distance;
        this.tears = tears;
    }

    @Override protected void init() {
        int cx = width/2, cy = height/2;
        addDrawableChild(ButtonWidget.builder(Text.of("Proceed to Payment"), b -> GhostPackets.Client.openPayment(ghostId))
                .dimensions(cx-80, cy+10, 160, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.of("Back"), b -> this.close())
                .dimensions(cx-40, cy+35, 80, 20).build());
    }

    @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        renderBackground(draw);
        draw.drawCenteredTextWithShadow(textRenderer, title, width/2, height/2-50, 0xFFFFFF);
        draw.drawCenteredTextWithShadow(textRenderer,
                Text.of("Target: "+target.getX()+", "+target.getY()+", "+target.getZ()),
                width/2, height/2-30, 0xC0FFFF);
        draw.drawCenteredTextWithShadow(textRenderer,
                Text.of(String.format("Distance: %.1f blocks", distance)),
                width/2, height/2-15, 0xC0FFFF);
        draw.drawCenteredTextWithShadow(textRenderer,
                Text.of("Ghast Tears required: "+tears),
                width/2, height/2, 0xFFD080);
        super.render(draw, mouseX, mouseY, delta);
    }
}
