// FILE: src/main/java/net/seep/odd/abilities/wizard/client/WizardElementWheelScreen.java
package net.seep.odd.abilities.wizard.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import net.seep.odd.abilities.power.WizardPower;
import net.seep.odd.abilities.wizard.WizardElement;

@Environment(EnvType.CLIENT)
public final class WizardElementWheelScreen extends Screen {
    private int cx, cy;
    private int selected = -1;

    private static final int OUTER_R = 90;
    private static final int INNER_R = 18;

    public WizardElementWheelScreen() {
        super(Text.literal("Select Element"));
    }

    @Override
    protected void init() {
        cx = this.width / 2;
        cy = this.height / 2;
    }

    private int pick(int mouseX, int mouseY) {
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < INNER_R || dist > OUTER_R + 40) return -1;

        double ang = Math.atan2(dy, dx);                // 0 = right, + down
        double a = (ang + Math.PI / 2 + Math.PI * 2) % (Math.PI * 2); // 0 = up
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

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0x55000000);

        selected = pick(mouseX, mouseY);

        ctx.drawCenteredTextWithShadow(this.textRenderer, "Element Wheel", cx, cy - 110, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "Drag + release", cx, cy - 96, 0xFFB0B0B0);

        double step = (Math.PI * 2.0) / 4.0;
        for (int i = 0; i < 4; i++) {
            double mid = -Math.PI / 2 + (i + 0.5) * step;
            int tx = (int) (cx + Math.cos(mid) * 70);
            int ty = (int) (cy + Math.sin(mid) * 70);

            WizardElement e = elementByIndex(i);
            int col = (i == selected) ? 0xFFFFFF55 : 0xFFFFFFFF;
            ctx.drawCenteredTextWithShadow(this.textRenderer, e.displayName, tx, ty - 4, col);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override public boolean shouldPause() { return false; }
}
