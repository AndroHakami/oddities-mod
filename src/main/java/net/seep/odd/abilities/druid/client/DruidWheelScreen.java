package net.seep.odd.abilities.druid.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

import net.seep.odd.abilities.druid.DruidNet;

import java.util.List;

@Environment(EnvType.CLIENT)
public final class DruidWheelScreen extends Screen {

    public record Option(String key, String name, Identifier icon) {}

    private final long cooldownTicksRemaining;
    private final List<Option> options;

    private int selectedIndex = -1;

    public DruidWheelScreen(long cooldownTicksRemaining, List<Option> options) {
        super(Text.literal("Shapeshift"));
        this.cooldownTicksRemaining = cooldownTicksRemaining;
        this.options = options;
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // no dark background
        int cx = this.width / 2;
        int cy = this.height / 2;

        // pick selection by mouse direction
        selectedIndex = computeSelected(mouseX, mouseY, cx, cy);

        // draw icons in a circle
        int radius = 70;
        int iconSize = 32;

        for (int i = 0; i < options.size(); i++) {
            double ang = (i / (double) options.size()) * Math.PI * 2.0 - Math.PI / 2.0;
            int x = (int)(cx + Math.cos(ang) * radius) - iconSize / 2;
            int y = (int)(cy + Math.sin(ang) * radius) - iconSize / 2;

            // highlight
            if (i == selectedIndex) {
                ctx.fill(x - 3, y - 3, x + iconSize + 3, y + iconSize + 3, 0x55AAFFAA);
            }

            Identifier tex = options.get(i).icon();
            ctx.drawTexture(tex, x, y, 0, 0, iconSize, iconSize, iconSize, iconSize);
        }

        // label
        if (selectedIndex >= 0 && selectedIndex < options.size()) {
            String name = options.get(selectedIndex).name();
            ctx.drawCenteredTextWithShadow(this.textRenderer, name, cx, cy + radius + 20, 0xFFFFFF);
        }

        if (cooldownTicksRemaining > 0) {
            int sec = (int)Math.ceil(cooldownTicksRemaining / 20.0);
            ctx.drawCenteredTextWithShadow(this.textRenderer, "Locked: " + sec + "s", cx, cy - radius - 25, 0xFF7777);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    private int computeSelected(int mx, int my, int cx, int cy) {
        if (options.isEmpty()) return -1;
        double dx = mx - cx;
        double dy = my - cy;
        double dist2 = dx*dx + dy*dy;

        // require some distance from center so you don’t accidentally pick
        if (dist2 < 18*18) return -1;

        double ang = Math.atan2(dy, dx) + Math.PI / 2.0;
        if (ang < 0) ang += Math.PI * 2.0;

        int idx = (int)Math.floor((ang / (Math.PI * 2.0)) * options.size());
        if (idx < 0) idx = 0;
        if (idx >= options.size()) idx = options.size() - 1;
        return idx;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (cooldownTicksRemaining > 0) {
            close();
            return true;
        }

        if (selectedIndex >= 0 && selectedIndex < options.size()) {
            Option opt = options.get(selectedIndex);

            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(opt.key());
            ClientPlayNetworking.send(DruidNet.C2S_SELECT_FORM, buf);
        }

        close();
        return true;
    }

    public void close() {
        MinecraftClient.getInstance().setScreen(null);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC cancels
        if (keyCode == 256) { // GLFW_KEY_ESCAPE
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
