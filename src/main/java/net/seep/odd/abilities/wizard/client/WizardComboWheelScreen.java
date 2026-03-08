// FILE: src/main/java/net/seep/odd/abilities/wizard/client/WizardComboWheelScreen.java
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
import net.seep.odd.abilities.wizard.WizardCombo;

@Environment(EnvType.CLIENT)
public final class WizardComboWheelScreen extends Screen {

    private int cx, cy;

    // ✅ bring icons slightly closer in (was 110f)
    private float radius = 98f;

    private WizardCombo hovered = null;

    // ✅ bigger combo icon textures (was 28)
    private static final int ICON_SIZE = 38;

    private static final Identifier ICON_STEAM  = new Identifier("odd", "textures/gui/wizard/combos/steam_cloud.png");
    private static final Identifier ICON_FIRE_T = new Identifier("odd", "textures/gui/wizard/combos/fire_tornado.png");
    private static final Identifier ICON_LIFE   = new Identifier("odd", "textures/gui/wizard/combos/life_restoration.png");
    private static final Identifier ICON_SONIC  = new Identifier("odd", "textures/gui/wizard/combos/sonic_screech.png");
    private static final Identifier ICON_SWAP   = new Identifier("odd", "textures/gui/wizard/combos/swapperino.png");
    private static final Identifier ICON_METEOR = new Identifier("odd", "textures/gui/wizard/combos/meteor_strike.png");

    // ✅ Wheel BG texture (your art)
    private static final Identifier WHEEL_BG = new Identifier("odd", "textures/gui/wizard/wheels/combo_wheel.png");
    private static final int WHEEL_BG_W = 256;
    private static final int WHEEL_BG_H = 256;

    // ✅ if we never picked, closing counts as "cancel"
    private boolean picked = false;

    public WizardComboWheelScreen() {
        super(net.minecraft.text.Text.empty());
    }

    @Override
    protected void init() {
        cx = this.width / 2;
        cy = this.height / 2;
        picked = false;
    }

    private WizardCombo computeHovered(int mouseX, int mouseY) {
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);

        // keep your deadzone logic for selection (does NOT draw any center circle)
        if (dist < 28) return null;

        double ang = Math.atan2(dy, dx);
        ang -= (-Math.PI / 2.0);

        while (ang < 0) ang += (Math.PI * 2.0);
        while (ang >= Math.PI * 2.0) ang -= (Math.PI * 2.0);

        WizardCombo[] arr = WizardCombo.values();
        int idx = (int) Math.floor((ang / (Math.PI * 2.0)) * arr.length);
        idx = MathHelper.clamp(idx, 0, arr.length - 1);
        return arr[idx];
    }

    private static Identifier iconFor(WizardCombo c) {
        return switch (c) {
            case STEAM_CLOUD      -> ICON_STEAM;
            case FIRE_TORNADO     -> ICON_FIRE_T;
            case LIFE_RESTORATION -> ICON_LIFE;
            case SONIC_SCREECH    -> ICON_SONIC;
            case SWAPPERINO       -> ICON_SWAP;
            case METEOR_STRIKE    -> ICON_METEOR;
        };
    }

    private static void sendCancel() {
        ClientPlayNetworking.send(WizardPower.C2S_CANCEL_COMBO, PacketByteBufs.create());
    }

    @Override
    public void removed() {
        // ✅ If the wheel closes without selecting a combo => cancel & clear cooldown to 0
        if (!picked) sendCancel();
        super.removed();
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        WizardCombo pick = computeHovered((int) mouseX, (int) mouseY);
        if (pick != null) {
            picked = true; // not a cancel
            WizardComboTargetClient.begin(pick);
            this.close();
            return true;
        }
        // picked remains false => removed() will cancel
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
        ctx.fill(0, 0, this.width, this.height, 0x66000000);

        hovered = computeHovered(mouseX, mouseY);

        // ✅ ONLY the wheel texture (NO cyan big circle, NO middle circle)
        int bgX = cx - (WHEEL_BG_W / 2);
        int bgY = cy - (WHEEL_BG_H / 2);
        ctx.drawTexture(WHEEL_BG, bgX, bgY, 0, 0, WHEEL_BG_W, WHEEL_BG_H, WHEEL_BG_W, WHEEL_BG_H);

        WizardCombo[] arr = WizardCombo.values();
        for (int i = 0; i < arr.length; i++) {
            double ang = (Math.PI * 2.0) * (i / (double) arr.length) - (Math.PI / 2.0);
            int x = (int) (cx + Math.cos(ang) * radius);
            int y = (int) (cy + Math.sin(ang) * radius);

            WizardCombo c = arr[i];
            boolean hov = (c == hovered);

            // ✅ keep select circles (behind icons, but above the wheel texture)
            if (hov) fillCircle(ctx, x, y, 18, 0x6600FFFF);

            // ✅ icons bigger + slightly closer in via radius
            int s = hov ? (ICON_SIZE + 6) : ICON_SIZE;
            Identifier icon = iconFor(c);
            ctx.drawTexture(icon, x - s / 2, y - s / 2, 0, 0, s, s, s, s);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}