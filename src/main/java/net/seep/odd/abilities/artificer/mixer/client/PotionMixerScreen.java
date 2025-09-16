package net.seep.odd.abilities.artificer.mixer.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.seep.odd.abilities.artificer.EssenceType;
import net.seep.odd.abilities.artificer.mixer.PotionMixerScreenHandler;
import net.seep.odd.abilities.artificer.mixer.MixerNet;

import java.util.EnumSet;
import java.util.Set;

public class PotionMixerScreen extends HandledScreen<PotionMixerScreenHandler> {
    private final Set<EssenceType> picked = EnumSet.noneOf(EssenceType.class);

    public PotionMixerScreen(PotionMixerScreenHandler h, PlayerInventory inv, Text title) {
        super(h, inv, title);
        backgroundWidth = 176; backgroundHeight = 166;
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mx, int my) {
        ctx.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xAA141414);
        int ty = y + 8;
        ctx.drawText(textRenderer, title, x + 8, ty, 0xFFFFFF, false);
        ty += 12;

        int ix = x + 8;
        for (EssenceType t : EssenceType.values()) {
            boolean sel = picked.contains(t);
            int col = sel ? 0xFFFFA500 : 0xFFAAAAAA;
            ctx.drawText(textRenderer, Text.literal((sel ? "[x] " : "[ ] ") + t.key), ix, ty, col, false);
            ty += 10;
        }

        ty += 6;
        ctx.fill(x + 8, ty, x + 68, ty + 14, 0xFF303030);
        ctx.drawText(textRenderer, Text.literal("Brew"), x + 8 + 20, ty + 3, 0xFFFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int ty = y + 20;
        int ix = x + 8;

        for (EssenceType t : EssenceType.values()) {
            if (mx >= ix && mx <= ix + 120 && my >= ty && my <= ty + 10) {
                if (picked.contains(t)) picked.remove(t);
                else if (picked.size() < 3) picked.add(t);
                return true;
            }
            ty += 10;
        }

        ty += 6;
        if (mx >= x + 8 && mx <= x + 68 && my >= ty && my <= ty + 14) {
            MixerNet.sendBrew(handler.pos, picked);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
}
