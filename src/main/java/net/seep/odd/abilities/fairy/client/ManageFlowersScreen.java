package net.seep.odd.abilities.fairy.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.seep.odd.block.falseflower.FalseFlowerTracker;

public class ManageFlowersScreen extends Screen {

    public ManageFlowersScreen() {
        super(Text.literal("Manage Flowers"));
    }

    @Override
    protected void init() {
        // ask server for fresh snapshot
        FalseFlowerTracker.requestSnapshot();

        int y = 36;
        for (var entry : FalseFlowerTracker.clientSnapshot()) {
            final int id = entry.id();
            final boolean active = entry.active();
            final String label = (entry.name().isBlank() ? "Flower" : entry.name())
                    + "  @ " + entry.pos().getX() + "," + entry.pos().getY() + "," + entry.pos().getZ()
                    + "  | mana " + Math.round(entry.mana());

            // Label button (non-interactive)
            this.addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {})
                    .dimensions(12, y, 220, 20).build());

            // Activate/Deactivate
            this.addDrawableChild(ButtonWidget.builder(Text.literal(active ? "Deactivate" : "Activate"),
                            b -> FalseFlowerTracker.sendToggle(id, !active))
                    .dimensions(240, y, 90, 20).build());

            // TODO: add a slider & rename field (call FalseFlowerTracker.sendPower / sendRename)

            y += 24;
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xE4E0FF);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }
}
