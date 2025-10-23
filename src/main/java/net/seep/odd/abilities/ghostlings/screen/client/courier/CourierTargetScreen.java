package net.seep.odd.abilities.ghostlings.screen.client.courier;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.abilities.ghostlings.GhostPackets;

public class CourierTargetScreen extends Screen {
    private final int ghostId;
    private TextFieldWidget xField, yField, zField;
    private String errorMsg = null;

    public CourierTargetScreen(int ghostId) {
        super(Text.of("Courier: Target"));
        this.ghostId = ghostId;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;

        xField = new TextFieldWidget(textRenderer, cx - 102, cy - 22, 60, 20, Text.of("X"));
        yField = new TextFieldWidget(textRenderer, cx - 30,  cy - 22, 60, 20, Text.of("Y"));
        zField = new TextFieldWidget(textRenderer, cx + 42,  cy - 22, 60, 20, Text.of("Z"));

        xField.setMaxLength(8);
        yField.setMaxLength(8);
        zField.setMaxLength(8);

        if (client != null && client.player != null) {
            BlockPos p = client.player.getBlockPos();
            xField.setText(Integer.toString(p.getX()));
            yField.setText(Integer.toString(p.getY()));
            zField.setText(Integer.toString(p.getZ()));
        }

        // Must be addDrawableChild so they render and receive input!
        addDrawableChild(xField);
        addDrawableChild(yField);
        addDrawableChild(zField);

        // Continue
        addDrawableChild(ButtonWidget.builder(Text.of("Continue"), b -> submit())
                .dimensions(cx - 50, cy + 16, 100, 20).build());

        // Helpers
        addDrawableChild(ButtonWidget.builder(Text.of("Use Here"), b -> {
            if (client != null && client.player != null) {
                BlockPos p = client.player.getBlockPos();
                xField.setText(Integer.toString(p.getX()));
                yField.setText(Integer.toString(p.getY()));
                zField.setText(Integer.toString(p.getZ()));
            }
        }).dimensions(cx - 108, cy + 16, 56, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.of("Use Look"), b -> {
            if (client != null && client.crosshairTarget instanceof BlockHitResult bhr) {
                BlockPos p = bhr.getBlockPos();
                xField.setText(Integer.toString(p.getX()));
                yField.setText(Integer.toString(p.getY()));
                zField.setText(Integer.toString(p.getZ()));
            }
        }).dimensions(cx + 52, cy + 16, 56, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.of("Cancel"), b -> close())
                .dimensions(cx - 50, cy + 40, 100, 20).build());

        setInitialFocus(xField);
    }

    private void submit() {
        try {
            int x = Integer.parseInt(xField.getText().trim());
            int y = Integer.parseInt(yField.getText().trim());
            int z = Integer.parseInt(zField.getText().trim());
            errorMsg = null;
            GhostPackets.Client.requestConfirm(ghostId, new BlockPos(x, y, z));
        } catch (NumberFormatException nfe) {
            errorMsg = "Type integers for X / Y / Z.";
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter/Return submits
        if (keyCode == 257 || keyCode == 335) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        xField.tick();
        yField.tick();
        zField.tick();
    }

    @Override
    public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        renderBackground(draw);
        draw.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 50, 0xFFFFFF);

        draw.drawTextWithShadow(textRenderer, Text.of("X"), width/2 - 115, height/2 - 18, 0xA0A0A0);
        draw.drawTextWithShadow(textRenderer, Text.of("Y"), width/2 - 43,  height/2 - 18, 0xA0A0A0);
        draw.drawTextWithShadow(textRenderer, Text.of("Z"), width/2 + 29,  height/2 - 18, 0xA0A0A0);

        super.render(draw, mouseX, mouseY, delta);

        if (errorMsg != null) {
            draw.drawCenteredTextWithShadow(textRenderer, Text.of(errorMsg), width / 2, height / 2 - 34, 0xFF5555);
        }
    }
}
