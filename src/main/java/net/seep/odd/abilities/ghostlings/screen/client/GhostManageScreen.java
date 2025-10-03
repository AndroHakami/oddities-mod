package net.seep.odd.abilities.ghostlings.screen.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.abilities.ghostlings.GhostPackets;
import net.seep.odd.abilities.ghostlings.screen.GhostManageScreenHandler;

public class GhostManageScreen extends HandledScreen<GhostManageScreenHandler> {
    public GhostManageScreen(GhostManageScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 220;
        this.backgroundHeight = 120;
    }

    @Override protected void init() {
        super.init();
        int cx = x + backgroundWidth/2;
        int cy = y + 24;

        addDrawableChild(ButtonWidget.builder(Text.of("Set Work Origin: Here"), b -> {
            BlockPos pos = client.player.getBlockPos();
            var out = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
            out.writeVarInt(handler.ghostEntityId);
            out.writeBlockPos(pos);
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(GhostPackets.C2S_SET_WORK_ORIGIN, out);
        }).dimensions(cx-100, cy-10, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.of("Toggle Stay Within Range (16)"), b -> {
            var out = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
            out.writeVarInt(handler.ghostEntityId);
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(GhostPackets.C2S_TOGGLE_STAY_RANGE, out);
        }).dimensions(cx-100, cy+15, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.of("Set Courier Target: Here"), b -> {
            BlockPos pos = client.player.getBlockPos();
            var out = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
            out.writeVarInt(handler.ghostEntityId);
            out.writeBlockPos(pos);
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(GhostPackets.C2S_SET_COURIER_POS, out);
        }).dimensions(cx-100, cy+40, 200, 20).build());
    }

    @Override
    public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        renderBackground(draw);
        draw.drawCenteredTextWithShadow(textRenderer, title, x + backgroundWidth/2, y + 6, 0xFFFFFF);
        super.render(draw, mouseX, mouseY, delta);
        drawMouseoverTooltip(draw, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {

    }
}
