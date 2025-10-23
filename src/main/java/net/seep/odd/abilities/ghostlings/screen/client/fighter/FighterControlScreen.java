package net.seep.odd.abilities.ghostlings.screen.client.fighter;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.abilities.ghostlings.entity.GhostlingEntity;
import net.seep.odd.abilities.ghostlings.GhostPackets;

public class FighterControlScreen extends Screen {
    private final int ghostId;
    private GhostlingEntity.BehaviorMode mode;
    private BlockPos guardCenter;

    public FighterControlScreen(int ghostId, GhostlingEntity.BehaviorMode mode, BlockPos guardCenter) {
        super(Text.of("Fighter Control"));
        this.ghostId = ghostId;
        this.mode = mode;
        this.guardCenter = guardCenter;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = height / 2 - 30;

        addDrawableChild(ButtonWidget.builder(Text.of("Follow Me"), b -> {
            GhostPackets.Client.setFollow(ghostId, true);
            this.mode = GhostlingEntity.BehaviorMode.FOLLOW;
        }).dimensions(cx - 80, y, 160, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.of("Guard Area…"), b -> {
            GhostPackets.Client.beginGuardPick(ghostId);
            this.mode = GhostlingEntity.BehaviorMode.GUARD;
            this.close();
        }).dimensions(cx - 80, y + 24, 160, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.of("Normal Mode"), b -> {
            GhostPackets.Client.clearMode(ghostId);
            this.mode = GhostlingEntity.BehaviorMode.NORMAL;
        }).dimensions(cx - 80, y + 48, 160, 20).build());
    }

    @Override
    public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        renderBackground(draw);
        draw.drawCenteredTextWithShadow(textRenderer, title, width/2, 30, 0xFFFFFF);

        String state = "Mode: " + mode.name() + (guardCenter != null ? (" @ " + guardCenter.toShortString()) : "");
        draw.drawCenteredTextWithShadow(textRenderer, Text.of(state), width/2, 48, 0xA0E0FF);

        super.render(draw, mouseX, mouseY, delta);
    }
}
