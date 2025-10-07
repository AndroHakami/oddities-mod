package net.seep.odd.abilities.ghostlings.screen.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.abilities.ghostlings.GhostPackets;
import net.seep.odd.abilities.ghostlings.entity.GhostlingEntity;
import net.seep.odd.abilities.ghostlings.screen.GhostManageScreenHandler;

public class GhostManageScreen extends HandledScreen<GhostManageScreenHandler> {
    public GhostManageScreen(GhostManageScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 220;
        this.backgroundHeight = 170; // room for extra buttons
    }

    @Override
    protected void init() {
        super.init();
        int cx = x + backgroundWidth / 2;
        int row = y + 24;

        // Set Work Origin
        addDrawableChild(ButtonWidget.builder(Text.of("Set Work Origin: Here"), b -> {
            var mc = client;
            if (mc == null || mc.player == null) return;
            BlockPos pos = mc.player.getBlockPos();
            var out = PacketByteBufs.create();
            out.writeVarInt(handler.ghostEntityId);
            out.writeBlockPos(pos);
            ClientPlayNetworking.send(GhostPackets.C2S_SET_WORK_ORIGIN, out);
        }).dimensions(cx - 100, row - 10, 200, 20).build());

        row += 25;

        // Toggle Stay
        addDrawableChild(ButtonWidget.builder(Text.of("Toggle Stay Within Range (16)"), b -> {
            var out = PacketByteBufs.create();
            out.writeVarInt(handler.ghostEntityId);
            ClientPlayNetworking.send(GhostPackets.C2S_TOGGLE_STAY_RANGE, out);
        }).dimensions(cx - 100, row - 10, 200, 20).build());

        row += 25;

        // Set Home
        addDrawableChild(ButtonWidget.builder(Text.of("Set Home: Here"), b -> {
            var mc = client;
            if (mc == null || mc.player == null) return;
            BlockPos pos = mc.player.getBlockPos();
            var out = PacketByteBufs.create();
            out.writeVarInt(handler.ghostEntityId);
            out.writeBlockPos(pos);
            ClientPlayNetworking.send(GhostPackets.C2S_SET_HOME, out);
        }).dimensions(cx - 100, row - 10, 200, 20).build());

        row += 25;

        // Courier-only
        if (handler.ghostJob == GhostlingEntity.Job.COURIER) {
            addDrawableChild(ButtonWidget.builder(Text.of("Courier: Set Target"), b ->
                    GhostPackets.openTargetInput(handler.ghostEntityId)
            ).dimensions(cx - 100, row - 10, 200, 20).build());
            row += 25;
        }

        // Farmer-only: go into "select a chest" mode with the next right-click
        if (handler.ghostJob == GhostlingEntity.Job.FARMER) {
            addDrawableChild(ButtonWidget.builder(Text.of("Farmer: Select Deposit Chest (right-click in world)"), b -> {
                GhostPackets.beginFarmerDepositPick(handler.ghostEntityId);
                if (client != null) client.setScreen(null); // close UI so you can click in world
            }).dimensions(cx - 100, row - 10, 200, 20).build());
            row += 25;
        }

        // Miner-only: area (A then B) + deposit chest, both via right-click
        if (handler.ghostJob == GhostlingEntity.Job.MINER) {
            addDrawableChild(ButtonWidget.builder(Text.of("Miner: Select Area (A then B, right-click)"), b -> {
                GhostPackets.beginMinerAreaPick(handler.ghostEntityId);
                if (client != null) client.setScreen(null);
            }).dimensions(cx - 100, row - 10, 200, 20).build());
            row += 25;

            addDrawableChild(ButtonWidget.builder(Text.of("Miner: Select Deposit Chest (right-click)"), b -> {
                GhostPackets.beginMinerDepositPick(handler.ghostEntityId);
                if (client != null) client.setScreen(null);
            }).dimensions(cx - 100, row - 10, 200, 20).build());
            row += 25;
        }
    }

    @Override
    public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        renderBackground(draw);
        draw.drawCenteredTextWithShadow(textRenderer, title, x + backgroundWidth / 2, y + 6, 0xFFFFFF);
        super.render(draw, mouseX, mouseY, delta);
        drawMouseoverTooltip(draw, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        // simple panel
    }
}
