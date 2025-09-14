package net.seep.odd.abilities.tamer.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 6-slice Summon wheel that mirrors the Command wheel UX.
 * Shows each party member's name and spawn egg icon.
 */
public final class SummonWheelHud implements HudRenderCallback, ClientTickEvents.EndTick {

    private static boolean open;
    private static int hovered = -1, lastHovered = -1;
    private static boolean mouseDown;
    private static int savedCursorMode = GLFW.GLFW_CURSOR_DISABLED;
    private static float lockYaw, lockPitch;

    private static final int OUTER_R = 86;
    private static final int INNER_R = 28;
    private static final int CURSOR_R = 7;
    private static final int SEGMENTS = 64;
    private static int hoverSoundCooldown = 0;

    /** Up to 6 entries for the radial. */
    private static final List<ClientEntry> entries = new ArrayList<>(6);

    private record ClientEntry(String name, Identifier typeId, ItemStack icon) {}

    private SummonWheelHud() {}

    public static void init() {
        HudRenderCallback.EVENT.register(new SummonWheelHud());
        ClientTickEvents.END_CLIENT_TICK.register(new SummonWheelHud());
    }

    /** Called by S2C OPEN_WHEEL with the party NBT payload. */
    public static void openFromNbt(NbtCompound root) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        entries.clear();
        NbtList arr = root.getList("party", NbtCompound.COMPOUND_TYPE);
        int n = Math.min(6, arr.size());
        for (int i = 0; i < n; i++) {
            NbtCompound e = arr.getCompound(i);
            // Be flexible with field names: "type" / "entityTypeId", "name" / "n"
            String typeStr = e.contains("type") ? e.getString("type")
                    : (e.contains("entityTypeId") ? e.getString("entityTypeId") : "");
            Identifier id = Identifier.tryParse(typeStr);
            String name = e.contains("name") ? e.getString("name")
                    : (e.contains("n") ? e.getString("n") : (id != null ? id.getPath() : "???"));

            ItemStack icon = makeSpawnEggIcon(id);
            entries.add(new ClientEntry(name, id, icon));
        }
        // pad to 6 with placeholders
        while (entries.size() < 6) entries.add(new ClientEntry("Empty", new Identifier("minecraft", "air"), new ItemStack(Items.BARRIER)));

        // open + input state
        open = true;
        hovered = -1;
        lastHovered = -1;
        mouseDown = false;
        hoverSoundCooldown = 0;

        lockYaw = mc.player.getYaw();
        lockPitch = mc.player.getPitch();

        long win = mc.getWindow().getHandle();
        savedCursorMode = GLFW.glfwGetInputMode(win, GLFW.GLFW_CURSOR);
        GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        GLFW.glfwSetCursorPos(win, mc.getWindow().getWidth() / 2.0, mc.getWindow().getHeight() / 2.0);
    }

    private static ItemStack makeSpawnEggIcon(Identifier entityTypeId) {
        if (entityTypeId != null) {
            EntityType<?> type = Registries.ENTITY_TYPE.get(entityTypeId);
            if (type != null) {
                SpawnEggItem egg = SpawnEggItem.forEntity(type);
                if (egg != null) return new ItemStack(egg);
            }
        }
        return new ItemStack(Items.NAME_TAG);
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

        final double mx = mc.mouse.getX() * sw / mc.getWindow().getWidth();
        final double my = mc.mouse.getY() * sh / mc.getWindow().getHeight();

        final double dx = mx - cx, dy = my - cy;
        final double r  = Math.hypot(dx, dy);
        int newHovered = -1;
        if (r > INNER_R * 0.8) {
            double ang = Math.atan2(dy, dx);
            if (ang < 0) ang += Math.PI * 2;
            // 6 slices → width 60° (π/3). Index 0 at +X (right), clockwise.
            newHovered = (int)Math.floor(ang / (Math.PI / 3.0)) % 6;
        }
        hovered = newHovered;

        RenderSystem.enableBlend();

        // subtle crosshair hint
        ctx.fill(cx - 1, cy - OUTER_R, cx + 1, cy + OUTER_R, 0x66000000);
        ctx.fill(cx - OUTER_R, cy - 1, cx + OUTER_R, cy + 1, 0x66000000);

        int baseCol  = 0xAA101010;
        int hoverCol = 0xDD2C2C2C;
        int ringCol  = 0x66000000;

        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        // draw 6 donut sectors
        for (int i = 0; i < 6; i++) {
            int col = (i == hovered) ? hoverCol : baseCol;
            double mid = i * (Math.PI / 3.0);
            double a0 = mid - (Math.PI / 6.0);
            double a1 = mid + (Math.PI / 6.0);
            drawDonutSector(m, cx, cy, INNER_R, OUTER_R, a0, a1, col);
        }

        drawDisk(m, cx, cy, INNER_R, ringCol);

        // labels + icons
        for (int i = 0; i < 6; i++) {
            double mid = i * (Math.PI / 3.0);
            int bx = cx + (int)(Math.cos(mid) * (INNER_R + (OUTER_R - INNER_R) * 0.58));
            int by = cy + (int)(Math.sin(mid) * (INNER_R + (OUTER_R - INNER_R) * 0.58));

            ClientEntry e = entries.get(i);
            // icon (centered above label)
            int ix = bx - 8;
            int iy = by - 14;
            ctx.drawItem(e.icon(), ix, iy);

            String label = e.name();
            int color = (i == hovered) ? 0xFFFFFFFF : 0xFFDDDDDD;
            ctx.drawTextWithShadow(mc.textRenderer, label, bx - mc.textRenderer.getWidth(label) / 2, by + 2, color);
        }

        // cursor dot
        drawDisk(m, (int)mx, (int)my, CURSOR_R + 2, 0x66000000);
        drawDisk(m, (int)mx, (int)my, CURSOR_R, 0xFFEFEFEF);

        ctx.drawTextWithShadow(mc.textRenderer, Text.literal("Drag + release to summon"), cx - 56, cy + OUTER_R + 6, 0xFFFFFFFF);

        RenderSystem.disableBlend();
    }

    @Override
    public void onEndTick(MinecraftClient mc) {
        if (!open || mc.player == null) return;

        // keep camera steady
        mc.player.setYaw(lockYaw);
        mc.player.setPitch(lockPitch);

        // hover sound
        if (hoverSoundCooldown > 0) hoverSoundCooldown--;
        if (hovered != lastHovered && hovered != -1 && hoverSoundCooldown == 0) {
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 0.7f));
            hoverSoundCooldown = 5;
        }
        lastHovered = hovered;

        long win = mc.getWindow().getHandle();
        boolean nowDown = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS;

        if (mouseDown && !nowDown) {
            if (hovered >= 0 && hovered < entries.size()) {
                net.seep.odd.abilities.net.TamerNet.sendSummonSelect(hovered);
            }
            close();
            return;
        }
        mouseDown = nowDown;

        if (mc.options.inventoryKey.isPressed() || mc.options.sneakKey.isPressed()) close();
    }

    /* ---- drawing helpers (same style as Command wheel) ---- */

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

        int steps = Math.max(10, (int)Math.ceil(SEGMENTS * Math.abs(a1 - a0) / (Math.PI * 2)));
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
}
