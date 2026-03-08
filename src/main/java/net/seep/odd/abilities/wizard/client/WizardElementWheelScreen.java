// FILE: src/main/java/net/seep/odd/abilities/wizard/client/WizardElementWheelScreen.java
package net.seep.odd.abilities.wizard.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import net.seep.odd.abilities.power.WizardPower;
import net.seep.odd.abilities.wizard.WizardElement;

@Environment(EnvType.CLIENT)
public final class WizardElementWheelScreen extends Screen {
    private int cx, cy;
    private int selected = -1;

    private static final int OUTER_R = 90;
    private static final int INNER_R = 18;

    private static final int ICON_SIZE = 28;

    // ✅ Wheel background texture (you will replace the PNG)
    // resources/assets/odd/textures/gui/wizard/wheels/element_wheel.png
    private static final Identifier WHEEL_BG = new Identifier("odd", "textures/gui/wizard/wheels/element_wheel.png");

    // Old outer circle radius was (OUTER_R + 14) = 104 -> diameter 208
    private static final int WHEEL_SIZE = (OUTER_R + 14) * 2; // 208

    private static final Identifier ICON_FIRE  = new Identifier("odd", "textures/gui/wizard/elements/fire.png");
    private static final Identifier ICON_WATER = new Identifier("odd", "textures/gui/wizard/elements/water.png");
    private static final Identifier ICON_AIR   = new Identifier("odd", "textures/gui/wizard/elements/air.png");
    private static final Identifier ICON_EARTH = new Identifier("odd", "textures/gui/wizard/elements/earth.png");

    // ✅ open animation
    private static final int OPEN_TICKS = 10;
    private int openAge = 0;

    public WizardElementWheelScreen() {
        super(net.minecraft.text.Text.empty());
    }

    @Override
    protected void init() {
        cx = this.width / 2;
        cy = this.height / 2;
        openAge = 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (openAge < OPEN_TICKS) openAge++;
    }

    private float openT(float delta) {
        float t = MathHelper.clamp((openAge + delta) / (float) OPEN_TICKS, 0f, 1f);
        float s = 1.70158f;
        float u = t - 1f;
        return 1f + (u * u * ((s + 1f) * u + s));
    }

    private int pick(int mouseX, int mouseY) {
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < INNER_R || dist > OUTER_R + 40) return -1;

        double ang = Math.atan2(dy, dx);
        double a = (ang + Math.PI / 2 + Math.PI * 2) % (Math.PI * 2);
        int idx = (int) Math.floor(a / (Math.PI * 2 / 4.0));
        return MathHelper.clamp(idx, 0, 3);
    }

    private static WizardElement elementByIndex(int idx) {
        return switch (idx) {
            case 0 -> WizardElement.FIRE;
            case 1 -> WizardElement.WATER;
            case 2 -> WizardElement.AIR;
            default -> WizardElement.EARTH;
        };
    }

    private static Identifier iconByIndex(int idx) {
        return switch (idx) {
            case 0 -> ICON_FIRE;
            case 1 -> ICON_WATER;
            case 2 -> ICON_AIR;
            default -> ICON_EARTH;
        };
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (selected != -1) {
            WizardElement e = elementByIndex(selected);
            var buf = PacketByteBufs.create();
            buf.writeInt(e.id);
            ClientPlayNetworking.send(WizardPower.C2S_SET_ELEMENT, buf);
        }
        this.close();
        return true;
    }

    private static void fillCircle(DrawContext ctx, int cx, int cy, int r, int color) {
        int rr = r * r;
        for (int y = -r; y <= r; y++) {
            int yy = y * y;
            int xSpan = (int) Math.sqrt(Math.max(0, rr - yy));
            ctx.fill(cx - xSpan, cy + y, cx + xSpan + 1, cy + y + 1, color);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float t = MathHelper.clamp((openAge + delta) / (float) OPEN_TICKS, 0f, 1f);
        float scale = openT(delta);

        int a = (int) (0x55 * MathHelper.clamp(t, 0f, 1f));
        int overlay = (a << 24);
        ctx.fill(0, 0, this.width, this.height, overlay);

        selected = pick(mouseX, mouseY);

        // ✅ wheel background scales from center
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, cy, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.getMatrices().translate(-WHEEL_SIZE / 2f, -WHEEL_SIZE / 2f, 0);
        ctx.drawTexture(WHEEL_BG, 0, 0, 0, 0, WHEEL_SIZE, WHEEL_SIZE, WHEEL_SIZE, WHEEL_SIZE);
        ctx.getMatrices().pop();

        // Icons: pop from center to positions
        float moveT = MathHelper.clamp(t, 0f, 1f);
        moveT = moveT * moveT * (3f - 2f * moveT);
        float iconBaseScale = 0.55f + 0.45f * moveT;

        double step = (Math.PI * 2.0) / 4.0;
        for (int i = 0; i < 4; i++) {
            double mid = -Math.PI / 2 + (i + 0.5) * step;

            int xFinal = (int) (cx + Math.cos(mid) * 70);
            int yFinal = (int) (cy + Math.sin(mid) * 70);

            int tx = (int) MathHelper.lerp(moveT, cx, xFinal);
            int ty = (int) MathHelper.lerp(moveT, cy, yFinal);

            boolean hov = (i == selected);
            int s = (int) ((hov ? (ICON_SIZE + 6) : ICON_SIZE) * iconBaseScale);

            if (hov) fillCircle(ctx, tx, ty, (int)(18 * iconBaseScale), 0x6600FFFF);

            Identifier icon = iconByIndex(i);
            ctx.drawTexture(icon, tx - s / 2, ty - s / 2, 0, 0, s, s, s, s);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override public boolean shouldPause() { return false; }
}