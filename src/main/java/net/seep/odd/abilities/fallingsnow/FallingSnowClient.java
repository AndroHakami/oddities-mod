package net.seep.odd.abilities.fallingsnow;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

@Environment(EnvType.CLIENT)
public final class FallingSnowClient {
    private FallingSnowClient(){}

    public static void init() {
        HudRenderCallback.EVENT.register(FallingSnowHud::render);

        // Optional ping from server after a blink to force refresh (payload-less)
        ClientPlayNetworking.registerGlobalReceiver(FallingSnowNet.S2C_PING_CHARGES, (client, handler, buf, response) -> {
            // no-op; your normal charge receiver should call setPrimaryCharges anyway
        });
    }

    /* ===== simple 2-dot HUD under crosshair ===== */
    static final class FallingSnowHud {
        private static int have = 0, max = 0; // hidden until we receive snapshot

        /** Call this from your existing client charge receiver when (power=="fallingsnow" && slot=="primary"). */
        public static void setPrimaryCharges(int haveNow, int maxNow) {
            have = Math.max(0, haveNow);
            max  = Math.max(0, maxNow);
        }

        static void render(DrawContext ctx, float tickDelta) {
            if (max <= 0) return; // not active/assigned yet
            var mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return;

            int w = mc.getWindow().getScaledWidth();
            int h = mc.getWindow().getScaledHeight();
            int cx = w / 2, cy = h / 2;
            int y = cy + 12;
            int gap = 12;

            drawDot(ctx, cx - gap/2 - 6, y, have >= 1);
            drawDot(ctx, cx + gap/2 - 6, y, have >= 2);
        }

        private static void drawDot(DrawContext ctx, int x, int y, boolean on) {
            int outer = 10, inner = 6;
            int bg    = 0x55000000;
            int ring  = on ? 0xFF67C7FF : 0x9967C7FF;
            int core  = on ? 0xFF67C7FF : 0x3367C7FF;
            ctx.fill(x, y, x+outer, y+outer, bg);
            ctx.fill(x+1, y+1, x+outer-1, y+outer-1, ring);
            ctx.fill(x+(outer-inner)/2, y+(outer-inner)/2, x+(outer+inner)/2, y+(outer+inner)/2, core);
        }
    }
}
