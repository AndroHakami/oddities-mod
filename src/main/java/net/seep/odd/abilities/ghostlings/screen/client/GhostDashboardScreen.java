package net.seep.odd.abilities.ghostlings.screen.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.abilities.ghostlings.entity.GhostlingEntity;

public class GhostDashboardScreen extends Screen {
    public static record GhostSummary(java.util.UUID uuid, int entityId, String name,
                                      GhostlingEntity.Job job, BlockPos pos,
                                      float hp, float max, boolean working) {}

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
        int rowH = 70;

        for (GhostSummary g : ghosts) {
            draw.fill(8, y-4, width-8, y+rowH-8, 0x66000000);

            String line = String.format("%s — %s — %s (%.0f/%.0f) @ %s",
                    g.name(), g.job(), g.working()? "Working" : "Idle", g.hp(), g.max(), g.pos().toShortString());
            draw.drawTextWithShadow(textRenderer, Text.of(line), 14, y, 0xC0FFFF);

            // 3D preview (1.20.1: use InventoryScreen.drawEntity)
            try {
                Entity e = (client != null && client.world != null) ? client.world.getEntityById(g.entityId()) : null;
                if (e instanceof GhostlingEntity ge) {
                    int size = 32;
                    int cx = width - 24 - size;
                    int cy = y + 40;
                    InventoryScreen.drawEntity(draw, cx, cy, size, 0f, 0f, ge);
                }
            } catch (Throwable ignored) {}

            y += rowH;
        }

        super.render(draw, mouseX, mouseY, delta);
    }
}
