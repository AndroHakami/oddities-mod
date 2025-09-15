package net.seep.odd.abilities.artificer.condenser;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.artificer.EssenceType;

public class CondenserScreen extends HandledScreen<CondenserScreenHandler> {
    private static final Identifier BG_TEX = new Identifier("odd", "textures/gui/condenser.png");

    // Actual PNG size so it won't stretch
    private static final int BG_W = 176;
    private static final int BG_H = 166;

    // Click-hitboxes that line up with your drawn buttons on the texture.
    // Adjust these 4 numbers if your art places the buttons somewhere else.
    private static final int BTN_X0  = 12;   // leftmost button X (relative to GUI left)
    private static final int BTN_Y   = 52;   // buttons row Y
    private static final int BTN_W   = 18;   // button width
    private static final int BTN_H   = 18;   // button height
    private static final int BTN_PAD = 4;    // gap between buttons

    private final CondenserBlockEntity be;

    public CondenserScreen(CondenserScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        // first slot belongs to the BE, this is just to recover the BE instance
        this.be = (CondenserBlockEntity) handler.slots.get(0).inventory;

        this.backgroundWidth  = BG_W;
        this.backgroundHeight = BG_H;

        // Tweak these if you want to move the vanilla title text.
        this.titleX = 8;
        this.titleY = 6;
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        // Draw your 176x166 PNG exactly as-is (no stretching)
        ctx.drawTexture(
                BG_TEX,
                this.x, this.y,     // screen pos
                0, 0,               // u, v in texture
                BG_W, BG_H,         // draw size
                BG_W, BG_H          // actual texture size
        );

        // No colored placeholder squares, no outlines — your texture handles visuals.
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int hovered = hoveredButton((int) mx, (int) my);
        if (hovered >= 0) {
            EssenceType[] list = EssenceType.values();
            CondenserNet.sendPress(be.getPos(), list[hovered]); // key-based packet
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    // Optional: keep vanilla title text. Remove this override if your texture already includes a title.
    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        ctx.drawText(this.textRenderer, this.title, this.titleX, this.titleY, 0xFFFFFF, false);
    }

    private int hoveredButton(int mouseX, int mouseY) {
        int gx = mouseX - this.x;
        int gy = mouseY - this.y;

        if (gy < BTN_Y || gy >= BTN_Y + BTN_H) return -1;

        EssenceType[] list = EssenceType.values();
        for (int i = 0; i < list.length; i++) {
            int bx = BTN_X0 + i * (BTN_W + BTN_PAD);
            if (gx >= bx && gx < bx + BTN_W) return i;
        }
        return -1;
    }
}
