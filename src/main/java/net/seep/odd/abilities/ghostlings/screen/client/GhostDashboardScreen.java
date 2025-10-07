package net.seep.odd.abilities.ghostlings.screen.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.abilities.ghostlings.entity.GhostlingEntity;

public class GhostDashboardScreen extends Screen {
    public static record GhostSummary(
            java.util.UUID uuid, int entityId, String name,
            GhostlingEntity.Job job, BlockPos pos,
            float hp, float max, boolean working,
            String status, float progress,
            float mood // 0..1
    ) {}

    private final GhostSummary[] ghosts;

    public GhostDashboardScreen(GhostSummary[] ghosts) {
        super(Text.of("Ghostlings Dashboard"));
        this.ghosts = ghosts;
    }

    @Override
    public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        renderBackground(draw);
        draw.drawCenteredTextWithShadow(textRenderer, title, width/2, 8, 0xFFFFFF);

        int y = 28;
        int rowH = 92;

        for (GhostSummary g : ghosts) {
            // background card
            draw.fill(8, y-6, width-8, y+rowH-8, 0x66000000);

            // headline
            String line = String.format("%s — %s — %s (%.0f/%.0f) @ %s",
                    g.name(), g.job(), g.working()? "Working" : "Idle", g.hp(), g.max(), g.pos().toShortString());
            draw.drawTextWithShadow(textRenderer, Text.of(line), 14, y, 0xC0FFFF);

            // status
            draw.drawTextWithShadow(textRenderer, Text.of(g.status()), 14, y + 14, 0xA0E0FF);

            // progress bar if any (0..1)
            if (g.progress() > 0f && g.progress() < 1.0001f) {
                int barX = 14, barY = y + 28, barW = Math.min(180, width - 40), barH = 6;
                draw.fill(barX, barY, barX + barW, barY + barH, 0xFF202020);
                int filled = (int)(barW * Math.max(0f, Math.min(1f, g.progress())));
                draw.fill(barX, barY, barX + filled, barY + barH, 0xFF55AAFF);
            }

            // mood bar + label (0..1)
            float mood = Math.max(0f, Math.min(1f, g.mood()));
            int mbX = 14, mbY = y + 40, mbW = Math.min(180, width - 40), mbH = 8;
            int moodFill = (int)(mbW * mood);
            int col = 0xFF55FF55; // happy green
            String mLabel = "Happy";
            if (mood <= 0.10f) { col = 0xFFAA2222; mLabel = "Depressed"; }
            else if (mood <= 0.30f) { col = 0xFFFF8844; mLabel = "Sad"; }
            else if (mood < 0.80f) { col = 0xFFE6D15A; mLabel = "Okay"; }
            draw.fill(mbX, mbY, mbX + mbW, mbY + mbH, 0xFF202020);
            draw.fill(mbX, mbY, mbX + moodFill, mbY + mbH, col);

            String pct = String.format("Mood: %s (%.0f%%)", mLabel, mood*100f);
            draw.drawTextWithShadow(textRenderer, Text.of(pct), mbX, mbY + mbH + 2, 0xC0FFFFFF);

            // 3D preview
            try {
                Entity e = (client != null && client.world != null) ? client.world.getEntityById(g.entityId()) : null;
                if (e instanceof GhostlingEntity ge) {
                    int size = 32;
                    int cx = width - 24 - size;
                    int cy = y + 52;
                    InventoryScreen.drawEntity(draw, cx, cy, size, 0f, 0f, ge);
                }
            } catch (Throwable ignored) {}

            y += rowH;
        }

        super.render(draw, mouseX, mouseY, delta);
    }
}
