package net.seep.odd.abilities.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.power.Powers;
import net.seep.odd.abilities.power.ChargedPower;

import org.joml.Matrix4f;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.IntSupplier;

public final class AbilityHudOverlay {
    private AbilityHudOverlay() {}

    /** Your icon textures are 68×68. */
    private static final int ICON_SRC  = 66;
    private static final int RING_DRAW = 18; // diameter (px) for the small charge ring

    /** On-screen icon size (height). 22 matches hotbar height; bump to 24–28 if you want larger. */
    private static final int ICON_DRAW = 22;

    /** Space between icons on the hotbar row. */
    private static final int ICON_PAD  = 8;

    /** Ring texture (same as before) but now drawn around the icon on the same line. */
    private static final Identifier CHARGE_RING_TEX = new Identifier("odd", "textures/gui/charge_ring.png");
    private static final int RING_SRC = 32; // source size of your ring image

    /** Accent color (same supplier you had). */
    private static IntSupplier ACCENT = () -> 0xFFEBC034;
    public static void setAccentSupplier(IntSupplier supplier) { if (supplier != null) ACCENT = supplier; }

    public static void register() {
        HudRenderCallback.EVENT.register(AbilityHudOverlay::render);
    }

    private static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.isPaused()) return;

        String powerId = ClientPowerHolder.get();
        var p = Powers.get(powerId);
        if (p == null) return;

        String[] all = {"primary", "secondary", "third", "fourth"};
        List<String> slots = new ArrayList<>(4);
        for (String s : all) if (p.hasSlot(s)) slots.add(s);
        if (slots.isEmpty()) return;

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        final int HOTBAR_WIDTH  = 182;
        final int HOTBAR_HEIGHT = 22;

        int y0 = h - HOTBAR_HEIGHT + (HOTBAR_HEIGHT - ICON_DRAW) / 2;

        int hotbarRight = (w / 2) + (HOTBAR_WIDTH / 2);
        int totalWidth = slots.size() * ICON_DRAW + (slots.size() - 1) * ICON_PAD;

        int x0 = hotbarRight + 8;
        int rightMargin = 8;
        if (x0 + totalWidth > w - rightMargin) {
            x0 = Math.max(rightMargin, w - rightMargin - totalWidth);
        }

        for (int i = 0; i < slots.size(); i++) {
            int x = x0 + i * (ICON_DRAW + ICON_PAD);
            drawSlot(ctx, x, y0, slots.get(i), ICON_DRAW);
        }
    }

    private static void drawSlot(DrawContext ctx, int x, int y, String slot, int size) {
        var mc = MinecraftClient.getInstance();

        String powerId = ClientPowerHolder.get();
        var p = Powers.get(powerId);
        if (p == null) return;

        Identifier icon = p.iconTexture(slot);

        int totalCd = switch (slot) {
            case "primary"   -> (int) p.cooldownTicks();
            case "secondary" -> (int) p.secondaryCooldownTicks();
            case "third"     -> (int) p.thirdCooldownTicks();
            case "fourth"    -> (p instanceof AbilityHudOverlay.HasFourthCooldown hf) ? (int) hf.fourthCooldownTicks() : 0;
            default -> 0;
        };
        int remain = ClientCooldowns.get(slot);

        // icon
        var m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0);
        float s = (float) size / (float) ICON_SRC;
        m.scale(s, s, 1f);
        ctx.drawTexture(icon, 0, 0, 0, 0, ICON_SRC, ICON_SRC, ICON_SRC, ICON_SRC);
        m.pop();

        boolean isChargeSlot = (p instanceof ChargedPower cp) && cp.usesCharges(slot);

        // wipe for classic cooldowns
        if (!isChargeSlot && remain > 0 && totalCd > 0) {
            float ratio = Math.min(1f, remain / (float) totalCd);
            int cover = (int) (size * ratio);
            ctx.fill(x, y + (size - cover), x + size, y + size, 0x99000000);
        }

        // held tint
        if (ClientHeldState.isHeld(slot)) {
            ctx.fill(x, y, x + size, y + size, 0x66000000);
        }

        if (isChargeSlot) {
            drawChargeCircle(ctx, x, y, size, slot);
        } else if (remain > 0) {
            String txt = (remain >= 20)
                    ? String.valueOf((int) Math.ceil(remain / 20.0))
                    : String.format(Locale.ROOT, "%.1f", remain / 20.0);
            int tw = mc.textRenderer.getWidth(txt);
            ctx.drawTextWithShadow(mc.textRenderer, txt, x + (size - tw) / 2, y + (size / 2) - 4, ACCENT.getAsInt());
        }
    }

    /** Small circle + NEW square snake *around the ring* for charge lanes. */
    private static void drawChargeCircle(DrawContext ctx, int iconX, int iconY, int iconSize, String slot) {
        int accent = ACCENT.getAsInt();

        var lane = ClientCharges.get(slot);
        int  have     = lane.have;
        int  max      = Math.max(1, lane.max);
        long recharge = Math.max(0L, lane.recharge);
        long now      = lane.approxNow();

        if (recharge > 0 && have < max) {
            while (have < max && now >= lane.nextReady) {
                have++;
                lane.have = have;
                lane.nextReady += recharge;
            }
        } else if (recharge <= 0) {
            have = lane.have = max;
        }

        // position of ring above icon
        int rx = iconX + (iconSize - RING_DRAW) / 2;
        int ry = iconY - RING_DRAW - 4;

        float cx = rx + RING_DRAW / 2f;
        float cy = ry + RING_DRAW / 2f;

        float outer = (RING_DRAW / 2f) - 1.0f;
        float inner = outer - Math.max(2.0f, RING_DRAW * 0.10f);

        float fill = 0f;
        if (have < max && recharge > 0L) {
            long left = Math.max(0L, lane.nextReady - now);
            fill = 1.0f - Math.min(1f, left / (float) recharge);
        }

        if (fill > 0f) {
            drawRadialAnnulus(ctx.getMatrices(), cx, cy, inner, outer, fill, accent);
        }

        // NEW: square snake hugging the ring (not the big icon)
        if (have < max && recharge > 0L) {
            drawSquareSnakeAroundRing(ctx, rx, ry, RING_DRAW, fill, accent);
        }

        // ring texture
        var m = ctx.getMatrices();
        m.push();
        m.translate(rx, ry, 0);
        float s = (float) RING_DRAW / (float) RING_SRC;
        m.scale(s, s, 1f);
        ctx.drawTexture(CHARGE_RING_TEX, 0, 0, 0, 0, RING_SRC, RING_SRC, RING_SRC, RING_SRC);
        m.pop();

        // count inside ring
        String n = Integer.toString(Math.min(99, have));
        int tw = MinecraftClient.getInstance().textRenderer.getWidth(n);
        int tx = rx + (RING_DRAW - tw) / 2;
        int ty = ry + (RING_DRAW - 8) / 2;
        ctx.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, n, tx, ty, accent);
    }

    /** Square “snake” path around a box at (rx,ry,size×size). */
    private static void drawSquareSnakeAroundRing(DrawContext ctx, int rx, int ry, int size, float progress, int accent) {
        progress = Math.max(0f, Math.min(1f, progress));

        final int MARGIN    = 2; // just outside the ring
        final int THICKNESS = 3;

        int left   = rx - MARGIN;
        int top    = ry - MARGIN;
        int side   = size + MARGIN * 2;
        int right  = left + side;
        int bottom = top + side;

        int bg = (accent & 0x00FFFFFF) | 0x33000000;
        ctx.fill(left, top, right, top + THICKNESS, bg);
        ctx.fill(right - THICKNESS, top, right, bottom, bg);
        ctx.fill(left, bottom - THICKNESS, right, bottom, bg);
        ctx.fill(left, top, left + THICKNESS, bottom, bg);

        float perimeter = 4f * (float) side;
        float length    = perimeter * progress;
        float remain    = length;

        int fg = (accent & 0x00FFFFFF) | 0xFF000000;

        if (remain > 0f) {
            int take = Math.min((int)Math.ceil(remain), side);
            ctx.fill(left, top, left + take, top + THICKNESS, fg);
            remain -= take;
        }
        if (remain > 0f) {
            int take = Math.min((int)Math.ceil(remain), side);
            ctx.fill(right - THICKNESS, top, right, top + take, fg);
            remain -= take;
        }
        if (remain > 0f) {
            int take = Math.min((int)Math.ceil(remain), side);
            ctx.fill(right - take, bottom - THICKNESS, right, bottom, fg);
            remain -= take;
        }
        if (remain > 0f) {
            int take = Math.min((int)Math.ceil(remain), side);
            ctx.fill(left, bottom - take, left + THICKNESS, bottom, fg);
        }
    }

    /** Annular pie slice for the tiny ring. */
    private static void drawRadialAnnulus(MatrixStack matrices, float cx, float cy, float rInner, float rOuter, float fill, int rgba) {
        fill = Math.max(0f, Math.min(1f, fill));
        if (fill <= 0f) return;

        int a = (rgba >>> 24) & 0xFF, rr = (rgba >>> 16) & 0xFF, g = (rgba >>> 8) & 0xFF, b = rgba & 0xFF;
        float angle = 360f * fill;
        int segs = Math.max(12, (int)(angle / 6f));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Matrix4f m = matrices.peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuffer();
        bb.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);

        int aa = (int)(a * 0.90f);
        for (int i = 0; i <= segs; i++) {
            float t = i / (float) segs;
            double deg = Math.toRadians(-90 + angle * t);
            float ox = cx + (float) Math.cos(deg) * rOuter;
            float oy = cy + (float) Math.sin(deg) * rOuter;
            float ix = cx + (float) Math.cos(deg) * rInner;
            float iy = cy + (float) Math.sin(deg) * rInner;
            bb.vertex(m, ox, oy, 0).color(rr, g, b, aa).next();
            bb.vertex(m, ix, iy, 0).color(rr, g, b, aa).next();
        }

        BufferRenderer.drawWithGlobalProgram(bb.end());
        RenderSystem.disableBlend();
    }

    /* ---------- optional cooldown interfaces ---------- */
    public interface HasThirdCooldown { long thirdCooldownTicks(); }
    public interface HasFourthCooldown { long fourthCooldownTicks(); }

    /* ---------- client helpers ---------- */
    public static final class ClientCharges {
        private static final HashMap<String, Lane> CH = new HashMap<>();
        public static final class Lane {
            public int have = 0, max = 1;
            public long recharge = 0L, nextReady = 0L;
            public long serverNowAtSync = 0L, clientTickAtSync = 0L;
            public long approxNow() {
                var mc = MinecraftClient.getInstance();
                long clientNow = (mc.world == null) ? 0 : mc.world.getTime();
                long delta = clientNow - clientTickAtSync;
                return serverNowAtSync + Math.max(0, delta);
            }
        }
        public static Lane get(String slot) { return CH.computeIfAbsent(slot, s -> new Lane()); }
        public static void set(String slot, int have, int max, long recharge, long nextReady, long serverNow) {
            var mc = MinecraftClient.getInstance();
            long clientTick = (mc.world == null) ? 0 : mc.world.getTime();
            Lane l = CH.computeIfAbsent(slot, s -> new Lane());
            l.have = Math.max(0, have);
            l.max  = Math.max(1, max);
            l.recharge = Math.max(0L, recharge);
            l.nextReady = Math.max(0L, nextReady);
            l.serverNowAtSync = serverNow;
            l.clientTickAtSync = clientTick;
        }
        public static void clear() { CH.clear(); }
    }

    public static final class ClientHeldState {
        private static final HashMap<String, Boolean> HELD = new HashMap<>();
        public static boolean isHeld(String slot) { return HELD.getOrDefault(slot, false); }
        public static void set(String slot, boolean held) { HELD.put(slot, held); }
        public static void clear() { HELD.clear(); }
    }
}
