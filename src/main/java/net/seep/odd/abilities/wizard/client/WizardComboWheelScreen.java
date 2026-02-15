// FILE: src/main/java/net/seep/odd/abilities/wizard/client/WizardComboWheelScreen.java
package net.seep.odd.abilities.wizard.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import net.seep.odd.abilities.wizard.WizardCombo;

@Environment(EnvType.CLIENT)
public final class WizardComboWheelScreen extends Screen {

    private int cx, cy;
    private float radius = 110f;

    private WizardCombo hovered = null;

    public WizardComboWheelScreen() {
        super(Text.literal("Magic Combo"));
    }

    @Override
    protected void init() {
        cx = this.width / 2;
        cy = this.height / 2;
    }

    private WizardCombo computeHovered(int mouseX, int mouseY) {
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.sqrt(dx*dx + dy*dy);
        if (dist < 28) return null; // deadzone

        double ang = Math.atan2(dy, dx); // -pi..pi, 0 = right
        // rotate so 0 slice is "up"
        ang -= (-Math.PI / 2.0);

        while (ang < 0) ang += (Math.PI * 2.0);
        while (ang >= Math.PI * 2.0) ang -= (Math.PI * 2.0);

        WizardCombo[] arr = WizardCombo.values(); // 6
        int idx = (int)Math.floor((ang / (Math.PI * 2.0)) * arr.length);
        idx = MathHelper.clamp(idx, 0, arr.length - 1);
        return arr[idx];
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        WizardCombo pick = computeHovered((int)mouseX, (int)mouseY);
        if (pick != null) {
            WizardComboTargetClient.begin(pick);
            this.close();
            return true;
        }
        this.close();
        return true;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // transparent dark overlay
        ctx.fill(0, 0, this.width, this.height, 0x66000000);

        hovered = computeHovered(mouseX, mouseY);

        // title
        ctx.drawCenteredTextWithShadow(this.textRenderer, "Combo Wheel", cx, cy - 130, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "Drag + release", cx, cy - 116, 0xA0FFFFFF);

        WizardCombo[] arr = WizardCombo.values();
        for (int i = 0; i < arr.length; i++) {
            double ang = (Math.PI * 2.0) * (i / (double)arr.length) - (Math.PI / 2.0);
            int x = (int)(cx + Math.cos(ang) * radius);
            int y = (int)(cy + Math.sin(ang) * radius);

            WizardCombo c = arr[i];
            int col = (c == hovered) ? 0xFFFFFF55 : 0xFFFFFFFF;
            ctx.drawCenteredTextWithShadow(this.textRenderer, c.displayName, x, y, col);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override public boolean shouldPause() { return false; }
}
