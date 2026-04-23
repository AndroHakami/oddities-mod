// src/main/java/net/seep/odd/block/combiner/client/CombinerScreen.java
package net.seep.odd.block.combiner.enchant.client;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import net.seep.odd.block.combiner.CombinerScreenHandler;
import net.seep.odd.block.combiner.net.CombinerNet;

public class CombinerScreen extends HandledScreen<CombinerScreenHandler> {

    private static final Identifier TEX_GUI      = new Identifier("odd", "textures/gui/combiner.png");

    // ✅ You will add these
    private static final Identifier TEX_QTE_BG   = new Identifier("odd", "textures/gui/combiner_qte_bg.png");
    private static final Identifier TEX_HITMARK  = new Identifier("odd", "textures/gui/combiner_hitmark.png");
    private static final Identifier TEX_CURSOR   = new Identifier("odd", "textures/gui/combiner_cursor.png");

    // Bar layout (same as your old one)
    private static final int BAR_X = 28;
    private static final int BAR_Y = 20;
    private static final int BAR_W = 120;
    private static final int BAR_H = 8;
    private boolean localFailed = false; // ✅ client-side immediate fail lock

    // QTE overlay area (you can make your bg image fit this)
    private static final int QTE_BG_X = 22;
    private static final int QTE_BG_Y = 14;
    private static final int QTE_BG_W = 132;
    private static final int QTE_BG_H = 20;

    // icon sizes (you can match these to your images)
    private static final int HIT_W = 6;
    private static final int HIT_H = 14;

    private static final int CURSOR_W = 6;
    private static final int CURSOR_H = 10;

    private ButtonWidget startBtn;

    private int localHitCooldown = 0;

    // ✅ client-side "what I see" timing
    private boolean prevActive = false;
    private long qteStartWorldTime = -1; // computed when QTE becomes active (client side)
    private int[] cachedMarks = null;
    private boolean[] usedMarks = null;

    // slightly wider local hit window so high-ping players have a fairer QTE
    private static final int CLIENT_TOL = 6;

    public CombinerScreen(CombinerScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 166;
    }

    @Override
    protected void init() {
        super.init();

        startBtn = ButtonWidget.builder(Text.literal("Forge"),
                        b -> {
                            // reset local caches so the moment QTE starts, client is clean
                            cachedMarks = null;
                            usedMarks = null;
                            qteStartWorldTime = -1;
                            prevActive = false;
                            CombinerNet.c2sStart(handler.getPos());
                        })
                .dimensions(x + 62, y + 60, 50, 20)
                .build();

        addDrawableChild(startBtn);
    }

    @Override
    public void handledScreenTick() {
        super.handledScreenTick();
        if (localHitCooldown > 0) localHitCooldown--;
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        RenderSystem.setShaderTexture(0, TEX_GUI);
        ctx.drawTexture(TEX_GUI, x, y, 0, 0, backgroundWidth, backgroundHeight);

        var mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        boolean active = handler.active();
        int dur = Math.max(1, handler.duration());
        int diff = Math.max(1, handler.difficulty());
        int seed = handler.seed();

        // detect activation edge
        if (active && !prevActive) {
            // start the local bar from the moment the client actually sees the QTE
            qteStartWorldTime = mc.world.getTime();
            cachedMarks = generateMarks(diff, dur, seed);
            usedMarks = new boolean[cachedMarks.length];
            localFailed = false; // ✅ reset
        }

// when active ended:
        if (!active && prevActive) {
            qteStartWorldTime = -1;
            cachedMarks = null;
            usedMarks = null;
            localFailed = false; // ✅ reset
        }
        prevActive = active;

        if (!active) {
            ctx.drawText(this.textRenderer, "Hits: " + handler.successes() + "/" + handler.required(),
                    x + 8, y + 60, 0xFFEEC400, true);
            return;
        }

        if (qteStartWorldTime < 0) qteStartWorldTime = mc.world.getTime();

        // ✅ local progress tick based on what client sees (not packet arrival)
        int localTick = (int) (mc.world.getTime() - qteStartWorldTime);
        localTick = MathHelper.clamp(localTick, 0, dur);

        // ✅ QTE overlay background
        RenderSystem.setShaderTexture(0, TEX_QTE_BG);
        ctx.drawTexture(TEX_QTE_BG, x + QTE_BG_X, y + QTE_BG_Y, 0, 0, QTE_BG_W, QTE_BG_H, QTE_BG_W, QTE_BG_H);

        // progress fill (gold)
        int p = localTick * BAR_W / dur;
        ctx.fill(x + BAR_X, y + BAR_Y, x + BAR_X + p, y + BAR_Y + BAR_H, 0xFFFFD400);

        // hit marks (use your custom icon)
        if (cachedMarks == null || cachedMarks.length != diff) cachedMarks = generateMarks(diff, dur, seed);
        if (usedMarks == null || usedMarks.length != cachedMarks.length) usedMarks = new boolean[cachedMarks.length];

        RenderSystem.setShaderTexture(0, TEX_HITMARK);

        for (int i = 0; i < cachedMarks.length; i++) {
            int m = cachedMarks[i];
            int px = x + BAR_X + (m * BAR_W / dur);

            // draw centered on px
            ctx.drawTexture(TEX_HITMARK,
                    px - (HIT_W / 2),
                    y + (BAR_Y - 6),
                    0, 0,
                    HIT_W, HIT_H,
                    HIT_W, HIT_H
            );
        }

        // optional cursor icon (also custom)
        RenderSystem.setShaderTexture(0, TEX_CURSOR);
        int cx = x + BAR_X + p;
        ctx.drawTexture(TEX_CURSOR,
                cx - (CURSOR_W / 2),
                y + (BAR_Y - 2),
                0, 0,
                CURSOR_W, CURSOR_H,
                CURSOR_W, CURSOR_H
        );

        ctx.drawText(this.textRenderer, "Hits: " + handler.successes() + "/" + handler.required(),
                x + 8, y + 60, 0xFFEEC400, true);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (handler.active() && keyCode == 32) {
            if (localFailed) return true;              // ✅ already failed locally
            if (localHitCooldown != 0) return true;

            var mc = MinecraftClient.getInstance();
            if (mc.world == null) return true;

            int dur = Math.max(1, handler.duration());
            int diff = Math.max(1, handler.difficulty());
            int seed = handler.seed();

            if (qteStartWorldTime < 0) qteStartWorldTime = mc.world.getTime();

            int clientTick = (int) (mc.world.getTime() - qteStartWorldTime);
            clientTick = MathHelper.clamp(clientTick, 0, dur);

            localHitCooldown = 4;

            if (cachedMarks == null || cachedMarks.length != diff) cachedMarks = generateMarks(diff, dur, seed);
            if (usedMarks == null || usedMarks.length != cachedMarks.length) usedMarks = new boolean[cachedMarks.length];

            int idx = pickUnusedMarkIndex(cachedMarks, usedMarks, clientTick, CLIENT_TOL);

            // ✅ IMMEDIATE local fail lock (so it doesn't "keep going")
            if (idx < 0) {
                localFailed = true;
                localHitCooldown = 999999; // lock input until server stops it
                if (mc.player != null && mc.world != null) {
                    mc.world.playSound(mc.player, mc.player.getBlockPos(),
                            SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 0.8f, 1.0f);
                }
            } else {
                usedMarks[idx] = true;
            }

            // still send to server (server will decide and consume tablet)
            CombinerNet.c2sHit(handler.getPos(), clientTick);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }

    /* =============================
       shared deterministic mark gen
       ============================= */

    private static int[] generateMarks(int diff, int dur, int seed) {
        diff = Math.max(1, Math.min(7, diff));
        java.util.Random r = new java.util.Random(seed);

        int[] out = new int[diff];
        int edgePadding = Math.min(16, Math.max(8, dur / 5));
        int minGap = 8;
        int safeSpan = Math.max(1, dur - (edgePadding * 2));
        int i = 0, attempts = 0;

        while (i < diff && attempts++ < 500) {
            int m = edgePadding + r.nextInt(safeSpan);
            boolean ok = true;
            for (int j = 0; j < i; j++) {
                if (Math.abs(out[j] - m) < minGap) { ok = false; break; }
            }
            if (ok) out[i++] = m;
        }

        while (i < diff) {
            out[i] = edgePadding + ((i + 1) * safeSpan / (diff + 1));
            i++;
        }

        java.util.Arrays.sort(out);
        return out;
    }

    private static int pickUnusedMarkIndex(int[] marks, boolean[] used, int tick, int tol) {
        int bestIdx = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < marks.length; i++) {
            if (used[i]) continue;
            int d = Math.abs(tick - marks[i]);
            if (d <= tol && d < bestDist) {
                bestDist = d;
                bestIdx = i;
            }
        }
        return bestIdx;
    }
}