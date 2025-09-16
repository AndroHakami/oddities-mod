package net.seep.odd.abilities.artificer.mixer.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.artificer.EssenceType;
import net.seep.odd.abilities.artificer.mixer.MixerNet;
import net.seep.odd.abilities.artificer.mixer.PotionMixerScreenHandler;

import java.util.EnumSet;
import java.util.Set;

/**
 * Simple UI:
 * - Left side: toggle 3 essence types (click to select/unselect).
 * - Bottom button: "Brew" sends C2S packet with the selected set.
 * Note: without a dedicated sync packet, we don't show live tank amounts here.
 */
public class PotionMixerScreen extends HandledScreen<PotionMixerScreenHandler> {
    private final Set<EssenceType> picked = EnumSet.noneOf(EssenceType.class);

    // Optional: a plain background resource (not required, we paint a flat panel)
    private static final Identifier BG = new Identifier("minecraft", "textures/gui/demo_background.png");

    public PotionMixerScreen(PotionMixerScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        backgroundWidth = 176;
        backgroundHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        titleX = 8;
        titleY = 6;
        playerInventoryTitleX = 8;
        playerInventoryTitleY = backgroundHeight - 94;
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mx, int my) {
        // Flat panel background
        ctx.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xAA141414);

        int ty = y + 8;
        ctx.drawText(textRenderer, title, x + 8, ty, 0xFFFFFF, false);
        ty += 12;

        // Essence toggles
        int ix = x + 8;
        for (EssenceType t : EssenceType.values()) {
            String label = (picked.contains(t) ? "[x] " : "[ ] ") + t.name();
            int color = picked.contains(t) ? 0xFFE0A0 : 0xC0C0C0;
            ctx.drawText(textRenderer, Text.literal(label), ix, ty, color, false);
            ty += 10;
        }

        // Brew button
        ty += 6;
        int btnX = x + 8;
        int btnY = ty;
        int btnW = 68;
        int btnH = 14;
        boolean hover = mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH;
        ctx.fill(btnX, btnY, btnX + btnW, btnY + btnH, hover ? 0xFF307040 : 0xFF204830);
        String btnText = "Brew (" + picked.size() + "/3)";
        ctx.drawText(textRenderer, Text.literal(btnText), btnX + 6, btnY + 4,
                picked.size() == 3 ? 0xFFFFFF : 0xAAAAAA, false);

        // Optional hint
        ctx.drawText(textRenderer, Text.literal("Pick exactly 3 essences."), x + 8, btnY + 18, 0x808080, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Toggle essence choices
        int ty = y + 8 + 12;
        int ix = x + 8;
        for (EssenceType t : EssenceType.values()) {
            // crude hitbox: text width up to ~90px and height 10px per line
            int w = 90;
            if (mx >= ix && mx <= ix + w && my >= ty && my <= ty + 10) {
                if (picked.contains(t)) picked.remove(t);
                else if (picked.size() < 3) picked.add(t);
                return true;
            }
            ty += 10;
        }

        // Brew button
        ty += 6;
        int btnX = x + 8;
        int btnY = ty;
        int btnW = 68;
        int btnH = 14;
        if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH) {
            if (picked.size() == 3) {
                MixerNet.sendBrew(handler.pos, picked);
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
}
