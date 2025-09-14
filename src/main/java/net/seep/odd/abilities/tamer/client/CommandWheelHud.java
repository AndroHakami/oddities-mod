package net.seep.odd.abilities.tamer.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * Crosshair-centered radial for Tamer "Command" with 4 pie slices:
 * 0=Passive, 1=Follow, 2=Recall (action), 3=Aggressive.
 * Non-pausing HUD overlay; temporarily UNGRABS the cursor and locks camera yaw/pitch
 * so you can move with WASD but the view doesn't rotate.
 */
public final class CommandWheelHud implements HudRenderCallback, ClientTickEvents.EndTick {

    private static boolean open;
    private static boolean hasActive;
    // currentMode: 0=PASSIVE 1=FOLLOW 2=AGGRESSIVE (Recall is not a mode)
    private static int currentMode;
    private static int hovered = -1, lastHovered = -1;
    private static boolean mouseDown;

    // remember & restore cursor mode
    private static int savedCursorMode = GLFW.GLFW_CURSOR_DISABLED;

    // lock camera while wheel is open
    private static float lockYaw, lockPitch;

    // visuals
    private static final int OUTER_R = 72;
    private static final int INNER_R = 26;
    private static final int CURSOR_R = 7;
    private static final int SEGMENTS = 48;

    private static int hoverSoundCooldown = 0;

    private CommandWheelHud() {}

    public static void init() {
        HudRenderCallback.EVENT.register(new CommandWheelHud());
        ClientTickEvents.END_CLIENT_TICK.register(new CommandWheelHud());
    }

    /** S2C → open command wheel. */
    public static void open(boolean _hasActive, int modeOrdinal) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        hasActive   = _hasActive;
        currentMode = Math.max(0, Math.min(2, modeOrdinal));
        open        = true;
        hovered     = -1;
        lastHovered = -1;
        mouseDown   = false;
        hoverSoundCooldown = 0;

        // lock the current camera rotation so look doesn't drift
        lockYaw = mc.player.getYaw();
        lockPitch = mc.player.getPitch();

        // ungrab cursor so we can read & render it
        long win = mc.getWindow().getHandle();
        savedCursorMode = GLFW.glfwGetInputMode(win, GLFW.GLFW_CURSOR);
        GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        GLFW.glfwSetCursorPos(win, mc.getWindow().getWidth() / 2.0, mc.getWindow().getHeight() / 2.0);
    }

    private static void close() {
        if (!open) return;
        open = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            long win = mc.getWindow().getHandle();
            GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, savedCursorMode);
        }
    }

    @Override
    public void onHudRender(DrawContext ctx, float tickDelta) {
        if (!open) return;
        final MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        final int sw = mc.getWindow().getScaledWidth();
        final int sh = mc.getWindow().getScaledHeight();
        final int cx = sw / 2;
        final int cy = sh / 2;

        // GUI-space mouse (moves because cursor is ungrabbed)
        final double mx = mc.mouse.getX() * sw / mc.getWindow().getWidth();
        final double my = mc.mouse.getY() * sh / mc.getWindow().getHeight();

        // hover slice by angle
        final double dx = mx - cx, dy = my - cy;
        final double r  = Math.hypot(dx, dy);
        int newHovered = -1;
        if (r > INNER_R * 0.8) {
            double ang = Math.atan2(dy, dx);
            if (ang < 0) ang += Math.PI * 2;
            newHovered = (int)Math.floor(ang / (Math.PI / 2.0)) & 3; // 0:right,1:down,2:left,3:up
        }
        hovered = newHovered;

        RenderSystem.enableBlend();

        // subtle crosshair hint
        ctx.fill(cx - 1, cy - OUTER_R, cx + 1, cy + OUTER_R, 0x66000000);
        ctx.fill(cx - OUTER_R, cy - 1, cx + OUTER_R, cy + 1, 0x66000000);

        // pie slices
        String[] labels = {"Passive","Follow","Recall","Aggressive"};
        int baseCol  = 0xAA101010;
        int hoverCol = 0xDD2C2C2C;
        int modeCol  = 0xDD2A6F2A;
        int ringCol  = 0x66000000;

        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        for (int i = 0; i < 4; i++) {
            int col = baseCol;
            if (i != 2 && mapModeIndexToSlice(currentMode) == i) col = modeCol;
            if (i == hovered) col = hoverCol;

            double mid = i * (Math.PI / 2.0);
            double a0 = mid - Math.PI / 4.0;
            double a1 = mid + Math.PI / 4.0;
            drawDonutSector(m, cx, cy, INNER_R, OUTER_R, a0, a1, col);
        }

        // center disk & labels
        drawDisk(m, cx, cy, INNER_R, ringCol);
        for (int i = 0; i < 4; i++) {
            double mid = i * (Math.PI / 2.0);
            int lx = cx + (int)(Math.cos(mid) * (INNER_R + (OUTER_R - INNER_R) * 0.62));
            int ly = cy + (int)(Math.sin(mid) * (INNER_R + (OUTER_R - INNER_R) * 0.62)) - 4;
            int color = (i == hovered) ? 0xFFFFFFFF : 0xFFDDDDDD;
            ctx.drawTextWithShadow(mc.textRenderer, labels[i], lx - mc.textRenderer.getWidth(labels[i]) / 2, ly, color);
        }

        // custom cursor
        drawDisk(m, (int)mx, (int)my, CURSOR_R + 2, 0x66000000);
        drawDisk(m, (int)mx, (int)my, CURSOR_R, 0xFFEFEFEF);

        // hint
        ctx.drawTextWithShadow(mc.textRenderer, Text.literal("Drag + release"), cx - 28, cy + OUTER_R + 6, 0xFFFFFFFF);

        RenderSystem.disableBlend();
    }

    @Override
    public void onEndTick(MinecraftClient mc) {
        if (!open || mc.player == null) return;

        // keep camera locked while open
        mc.player.setYaw(lockYaw);
        mc.player.setPitch(lockPitch);

        // hover sound
        if (hoverSoundCooldown > 0) hoverSoundCooldown--;
        if (hovered != lastHovered && hovered != -1 && hoverSoundCooldown == 0) {
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 0.6f));
            hoverSoundCooldown = 5;
        }
        lastHovered = hovered;

        long win = mc.getWindow().getHandle();
        boolean nowDown = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS;

        // release = pick
        if (mouseDown && !nowDown) {
            if (hovered == 2) {
                if (hasActive) net.seep.odd.abilities.net.TamerNet.sendRecall();
            } else if (hovered == 0) {
                net.seep.odd.abilities.net.TamerNet.sendModeSet(0);
            } else if (hovered == 1) {
                net.seep.odd.abilities.net.TamerNet.sendModeSet(1);
            } else if (hovered == 3) {
                net.seep.odd.abilities.net.TamerNet.sendModeSet(2);
            }
            close();
            return;
        }
        mouseDown = nowDown;

        // ESC/Inventory/Sneak closes
        if (mc.options.inventoryKey.isPressed() || mc.options.sneakKey.isPressed()) close();
    }

    /* ---------- drawing helpers ---------- */

    private static void drawDisk(Matrix4f m, int cx, int cy, int r, int argb) {
        drawArcStrip(m, cx, cy, 0, r, 0, Math.PI * 2, argb);
    }

    private static void drawDonutSector(Matrix4f m, int cx, int cy, int innerR, int outerR,
                                        double a0, double a1, int argb) {
        drawArcStrip(m, cx, cy, innerR, outerR, a0, a1, argb);
    }

    private static void drawArcStrip(Matrix4f m, int cx, int cy, int innerR, int outerR,
                                     double a0, double a1, int argb) {
        int a = (argb >>> 24) & 0xFF, r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);

        int steps = Math.max(8, (int)Math.ceil(SEGMENTS * Math.abs(a1 - a0) / (Math.PI * 2)));
        double step = (a1 - a0) / steps;

        for (int i = 0; i <= steps; i++) {
            double ang = a0 + step * i;
            float cos = (float)Math.cos(ang);
            float sin = (float)Math.sin(ang);

            float xOut = cx + cos * outerR;
            float yOut = cy + sin * outerR;
            float xIn  = cx + cos * innerR;
            float yIn  = cy + sin * innerR;

            buf.vertex(m, xOut, yOut, 0).color(r, g, b, a).next();
            buf.vertex(m, xIn,  yIn,  0).color(r, g, b, a).next();
        }
        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    /** Map our 0/1/2 mode to slice index 0/1/3 (recall is 2). */
    private static int mapModeIndexToSlice(int mode) { return (mode == 2) ? 3 : mode; }
}
