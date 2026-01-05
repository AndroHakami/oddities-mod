package net.seep.odd.abilities.lunar.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.seep.odd.abilities.lunar.item.LunarDrillItem;
import net.seep.odd.abilities.lunar.net.LunarPackets;

@Environment(EnvType.CLIENT)
public class LunarPatternScreen extends Screen {
    private final Hand hand;

    private long mask;   // lowest 25 bits
    private int depth;   // 1..6

    private int gridX, gridY;
    private int cell;
    private DepthSlider slider;

    public LunarPatternScreen(Hand hand) {
        super(Text.literal("Lunar Pattern"));
        this.hand = hand;
    }

    private ItemStack stack() {
        var mc = MinecraftClient.getInstance();
        if (mc.player == null) return ItemStack.EMPTY;
        return mc.player.getStackInHand(hand);
    }

    @Override
    protected void init() {
        ItemStack s = stack();
        var nbt = s.getOrCreateNbt();
        this.mask = LunarDrillItem.normalizeMask(nbt.getLong(LunarDrillItem.NBT_PATTERN));
        this.depth = LunarDrillItem.clampDepth(nbt.getInt(LunarDrillItem.NBT_DEPTH));

        // layout
        this.cell = 18;
        int gridPx = LunarDrillItem.PATTERN_SIZE * cell;

        this.gridX = (this.width - gridPx) / 2;
        this.gridY = (this.height - gridPx) / 2 - 18;

        // Depth slider under the grid
        int sliderW = 170;
        int sliderX = (this.width - sliderW) / 2;
        int sliderY = gridY + gridPx + 10;

        this.slider = new DepthSlider(sliderX, sliderY, sliderW, 20, depth);
        this.addDrawableChild(slider);

        // Done button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> this.close())
                .dimensions((this.width - 80) / 2, sliderY + 26, 80, 20)
                .build());

        writeToStackAndSend();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);

        // title
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, gridY - 18, 0xFFFFFF);

        // grid background
        int gridPx = LunarDrillItem.PATTERN_SIZE * cell;
        ctx.fill(gridX - 2, gridY - 2, gridX + gridPx + 2, gridY + gridPx + 2, 0xAA000000);

        // cells
        for (int r = 0; r < LunarDrillItem.PATTERN_SIZE; r++) {
            for (int c = 0; c < LunarDrillItem.PATTERN_SIZE; c++) {
                int x = gridX + c * cell;
                int y = gridY + r * cell;

                boolean isCenter = (r == LunarDrillItem.PATTERN_CENTER && c == LunarDrillItem.PATTERN_CENTER);
                boolean on = bitAt(r, c);

                int col;
                if (isCenter) col = 0xFFFFA500;           // orange center
                else if (on)  col = 0xFFEAEAEA;           // selected
                else          col = 0xFF2B2B2B;           // off

                ctx.fill(x + 1, y + 1, x + cell - 1, y + cell - 1, col);

                // thin border
                ctx.fill(x, y, x + cell, y + 1, 0xFF000000);
                ctx.fill(x, y + cell - 1, x + cell, y + cell, 0xFF000000);
                ctx.fill(x, y, x + 1, y + cell, 0xFF000000);
                ctx.fill(x + cell - 1, y, x + cell, y + cell, 0xFF000000);
            }
        }

        super.render(ctx, mouseX, mouseY, delta);

        // hint
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Click tiles (center is fixed)"), this.width / 2, gridY + gridPx + 60, 0xA0A0A0);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int r = (int) ((mouseY - gridY) / cell);
            int c = (int) ((mouseX - gridX) / cell);

            if (r >= 0 && r < LunarDrillItem.PATTERN_SIZE && c >= 0 && c < LunarDrillItem.PATTERN_SIZE) {
                // center is locked ON
                if (!(r == LunarDrillItem.PATTERN_CENTER && c == LunarDrillItem.PATTERN_CENTER)) {
                    toggleBit(r, c);
                    writeToStackAndSend();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        writeToStackAndSend();
        super.close();
    }

    private boolean bitAt(int r, int c) {
        int i = r * LunarDrillItem.PATTERN_SIZE + c;
        return ((mask >>> i) & 1L) == 1L;
    }

    private void toggleBit(int r, int c) {
        int i = r * LunarDrillItem.PATTERN_SIZE + c;
        mask ^= (1L << i);
        mask = LunarDrillItem.normalizeMask(mask);
    }

    private void writeToStackAndSend() {
        ItemStack s = stack();
        if (s.isEmpty()) return;

        depth = LunarDrillItem.clampDepth(slider != null ? slider.getDepth() : depth);
        mask = LunarDrillItem.normalizeMask(mask);

        var nbt = s.getOrCreateNbt();
        nbt.putLong(LunarDrillItem.NBT_PATTERN, mask);
        nbt.putInt(LunarDrillItem.NBT_DEPTH, depth);

        // IMPORTANT: must match server read order: long -> depth -> handId
        var buf = PacketByteBufs.create();
        buf.writeLong(mask);
        buf.writeVarInt(depth);
        buf.writeVarInt(hand.ordinal());
        ClientPlayNetworking.send(LunarPackets.C2S_SET_PATTERN, buf);
    }

    private final class DepthSlider extends SliderWidget {
        private int depth;

        DepthSlider(int x, int y, int w, int h, int initialDepth) {
            super(x, y, w, h, Text.empty(), (initialDepth - 1) / 5.0);
            this.depth = LunarDrillItem.clampDepth(initialDepth);
            updateMessage();
        }

        int getDepth() { return depth; }

        @Override
        protected void updateMessage() {
            this.setMessage(Text.literal("Depth: " + depth + " block" + (depth == 1 ? "" : "s")));
        }

        @Override
        protected void applyValue() {
            int d = 1 + (int) Math.round(this.value * 5.0); // 1..6
            this.depth = LunarDrillItem.clampDepth(d);
            updateMessage();
            writeToStackAndSend();
        }
    }
}
