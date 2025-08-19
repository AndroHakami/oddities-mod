package net.seep.odd.block.grandanvil.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.seep.odd.block.grandanvil.GrandAnvilScreenHandler;
import net.seep.odd.block.grandanvil.net.GrandAnvilNet;
import net.seep.odd.sound.ModSounds;

public class GrandAnvilScreen extends HandledScreen<GrandAnvilScreenHandler> {
    private static final Identifier TEX = new Identifier("odd", "textures/gui/grand_anvil.png");
    private ButtonWidget startBtn;

    public GrandAnvilScreen(GrandAnvilScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        // Start button
        startBtn = ButtonWidget.builder(Text.literal("Forge"),
                        b -> GrandAnvilNet.c2sStart(handler.getPos()))
                .dimensions(x + 62, y + 60, 50, 20)
                .build();
        addDrawableChild(startBtn);
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        RenderSystem.setShaderTexture(0, TEX);
        ctx.drawTexture(TEX, x, y, 0, 0, backgroundWidth, backgroundHeight);

        int dur = handler.duration();
        int diff = Math.max(1, handler.difficulty());
        int seed = handler.seed();

        // progress bar
        if (handler.active()) {
            int p = handler.progress() * 120 / Math.max(1, dur);
            ctx.fill(x + 28, y + 20, x + 28 + p, y + 28, 0xFFFFD400);
        }

        // randomized hit marks (client reproduces from seed + diff)
        if (handler.active()) {
            java.util.Random r = new java.util.Random(seed);
            int minGap = 6;
            int[] marks = new int[diff];
            int i = 0, tries = 0;
            while (i < diff && tries++ < 300) {
                int m = 8 + r.nextInt(Math.max(1, dur - 16));
                boolean ok = true;
                for (int j = 0; j < i; j++) if (Math.abs(marks[j] - m) < minGap) { ok = false; break; }
                if (ok) marks[i++] = m;
            }
            java.util.Arrays.sort(marks);
            for (int m : marks) {
                int px = x + 28 + (m * 120 / Math.max(1, dur));
                ctx.fill(px - 1, y + 18, px + 1, y + 30, 0xAAFFFFFF);
            }
        }

        // labels
        String msg = handler.active() ? "" : "";
        ctx.drawText(this.textRenderer, msg, x + 8, y + 6, 0xFFFFFF, false);
        ctx.drawText(this.textRenderer, "Hits: " + handler.successes() + "/" + handler.required(), x + 8, y + 60, 0xFFEEC400, true);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // SPACE = hit
        if (handler.active() && keyCode == 32) {
            GrandAnvilNet.c2sHit(handler.getPos());

            // client cosmetic spark & sound
            var mc = MinecraftClient.getInstance();
            if (mc.player != null && mc.world != null) {
                mc.world.addParticle(net.minecraft.particle.ParticleTypes.CRIT,
                        mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(),
                        0.0, 0.02, 0.0);
                mc.world.playSound(mc.player,
                        mc.player.getBlockPos(),
                        ModSounds.FORGER_HIT, SoundCategory.PLAYERS, 1, 1);

            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // draw the vanilla dark gradient over the game first
        this.renderBackground(ctx);

        // then let HandledScreen draw the GUI (slots, widgets, etc.)
        super.render(ctx, mouseX, mouseY, delta);

        // and finally tooltips
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }
}
