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
            float mood, String behavior // NEW
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
        int rowH = 90;

        for (GhostSummary g : ghosts) {
            // background card
            draw.fill(8, y-6, width-8, y+rowH-8, 0x66000000);

            // headline
            String line = String.format("%s — %s — %s (%.0f/%.0f) @ %s",
                    g.name(), g.job(), g.working()? "Working" : "Idle", g.hp(), g.max(), g.pos().toShortString());
            draw.drawTextWithShadow(textRenderer, Text.of(line), 14, y, 0xC0FFFF);

            // status + behavior
            String sub = g.status();
            if (!"NORMAL".equalsIgnoreCase(g.behavior())) {
                sub += "  [" + g.behavior() + "]";
            }
            draw.drawTextWithShadow(textRenderer, Text.of(sub), 14, y + 14, 0xA0E0FF);

            // progress bar (0..1)
            if (g.progress() > 0f && g.progress() < 1.0001f) {
                int barX = 14, barY = y + 30, barW = Math.min(180, width - 40), barH = 6;
                draw.fill(barX, barY, barX + barW, barY + barH, 0xFF202020);
                int filled = (int)(barW * Math.max(0f, Math.min(1f, g.progress())));
                draw.fill(barX, barY, barX + filled, barY + barH, 0xFF55AAFF);
            }

            // mood bar
            int mX = 14, mY = y + 42, mW = Math.min(180, width - 40), mH = 6;
            draw.fill(mX, mY, mX + mW, mY + mH, 0xFF202020);
            int mFill = (int)(mW * Math.max(0f, Math.min(1f, g.mood())));
            int col = moodColor(g.mood());
            draw.fill(mX, mY, mX + mFill, mY + mH, col);

            String moodLabel = g.mood() >= 0.80f ? "HAPPY" : (g.mood() <= 0.10f ? "DEPRESSED" : (g.mood() <= 0.30f ? "SAD" : "OK"));
            draw.drawTextWithShadow(textRenderer, Text.of("Mood: " + moodLabel), mX, mY + 8, 0xAAAAAA);

            // 3D preview
            try {
                Entity e = (client != null && client.world != null) ? client.world.getEntityById(g.entityId()) : null;
                if (e instanceof GhostlingEntity ge) {
                    int size = 32;
                    int cx = width - 24 - size;
                    int cy = y + 54;
                    InventoryScreen.drawEntity(draw, cx, cy, size, 0f, 0f, ge);
                }
            } catch (Throwable ignored) {}

            y += rowH;
        }

        super.render(draw, mouseX, mouseY, delta);
    }

    private int moodColor(float m) {
        // >=80% green, <=30% orange, <=10% red, else yellowish
        if (m >= 0.80f) return 0xFF3DDC84;
        if (m <= 0.10f) return 0xFFE53935;
        if (m <= 0.30f) return 0xFFFFA000;
        return 0xFFF4D03F;
    }
}
