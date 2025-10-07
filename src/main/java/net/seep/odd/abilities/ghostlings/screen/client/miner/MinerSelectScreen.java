package net.seep.odd.abilities.ghostlings.screen.client.miner;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.abilities.ghostlings.GhostPackets;

public class MinerSelectScreen extends Screen {
    private final int ghostId;
    private BlockPos a;
    private BlockPos b;

    public MinerSelectScreen(int ghostId) {
        super(Text.of("Select Mining Area"));
        this.ghostId = ghostId;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y  = this.height / 2 - 40;

        // Set A from the block you're looking at
        addDrawableChild(ButtonWidget.builder(Text.of("Set Corner A (look at block)"), btn -> {
            var mc = MinecraftClient.getInstance();
            HitResult hr = mc.crosshairTarget;
            if (hr instanceof BlockHitResult bhr) {
                a = bhr.getBlockPos().toImmutable();
                toast("Corner A: " + a.toShortString());
            } else {
                toast("Look at a block first.");
            }
        }).dimensions(cx - 120, y, 240, 20).build());
        y += 24;

        // Set B from the block you're looking at
        addDrawableChild(ButtonWidget.builder(Text.of("Set Corner B (look at block)"), btn -> {
            var mc = MinecraftClient.getInstance();
            HitResult hr = mc.crosshairTarget;
            if (hr instanceof BlockHitResult bhr) {
                b = bhr.getBlockPos().toImmutable();
                toast("Corner B: " + b.toShortString());
            } else {
                toast("Look at a block first.");
            }
        }).dimensions(cx - 120, y, 240, 20).build());
        y += 24;

        // Confirm -> send to server
        addDrawableChild(ButtonWidget.builder(Text.of("Confirm & Start"), btn -> {
            if (a != null && b != null) {
                // FIX: use the registered packet / helper
                GhostPackets.minerBegin(ghostId, a, b);
                close();
            } else {
                toast("Pick both corners first.");
            }
        }).dimensions(cx - 120, y, 240, 20).build());
        y += 24;

        addDrawableChild(ButtonWidget.builder(Text.of("Cancel"), btn -> close())
                .dimensions(cx - 120, y, 240, 20).build());
    }

    private void toast(String msg) {
        var mc = MinecraftClient.getInstance();
        if (mc != null && mc.player != null) {
            mc.player.sendMessage(Text.literal(msg), true);
        }
    }
}
