// src/main/java/net/seep/odd/abilities/fairy/client/FairyManaHudClient.java
package net.seep.odd.abilities.fairy.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

import net.seep.odd.abilities.power.FairyPower;

@Environment(EnvType.CLIENT)
public final class FairyManaHudClient {
    private FairyManaHudClient() {}

    private static boolean inited = false;

    private static boolean hasFairy = false;
    private static float mana = 0f;
    private static float max = 100f;

    /** Call once from client init. */
    public static void init() {
        if (inited) return;
        inited = true;

        // ✅ server -> client mana HUD sync
        ClientPlayNetworking.registerGlobalReceiver(FairyPower.S2C_MANA_SYNC, (client, handler, buf, resp) -> {
            boolean hf = buf.readBoolean();
            float m = buf.readFloat();
            float mx = buf.readFloat();
            client.execute(() -> {
                hasFairy = hf;                 // ✅ this is the "show/hide" gate
                mana = m;
                max = (mx <= 0f) ? 100f : mx;

                // ✅ hard-hide if server says no fairy
                if (!hasFairy) {
                    mana = 0f;
                    max = 100f;
                }
            });
        });

        // ✅ hard reset on disconnect / world unload (prevents “stuck HUD”)
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());

        // ✅ draw
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;

            // ✅ ONLY visible while fairy
            if (!hasFairy) return;

            int sw = mc.getWindow().getScaledWidth();
            int sh = mc.getWindow().getScaledHeight();

            int barW = 120;
            int barH = 8;
            int x = (sw - barW) / 2;
            int y = sh - 49; // a bit above hotbar

            float frac = (max <= 0f) ? 0f : MathHelper.clamp(mana / max, 0f, 1f);
            int fillW = (int) (barW * frac);

            // border + bg
            ctx.fill(x - 1, y - 1, x + barW + 1, y + barH + 1, 0xAAFFFFFF);
            ctx.fill(x, y, x + barW, y + barH, 0x66000000);

            // iridescent fill (animated)
            long t = (mc.world != null ? mc.world.getTime() : (System.currentTimeMillis() / 50L));
            float baseHue = (t % 240L) / 240f;

            for (int i = 0; i < fillW; i++) {
                float h = (baseHue + (i / (float) barW) * 0.35f) % 1f;
                int rgb = hsvToRgb(h, 0.80f, 1.0f);
                ctx.fill(x + i, y, x + i + 1, y + barH, 0xFF000000 | rgb);
            }
        });
    }

    /** Force-hide the HUD locally. */
    public static void reset() {
        hasFairy = false;
        mana = 0f;
        max = 100f;
    }

    private static int hsvToRgb(float h, float s, float v) {
        h = (h % 1f + 1f) % 1f;
        s = MathHelper.clamp(s, 0f, 1f);
        v = MathHelper.clamp(v, 0f, 1f);

        float c = v * s;
        float x = c * (1f - Math.abs((h * 6f) % 2f - 1f));
        float m = v - c;

        float r1, g1, b1;
        float hh = h * 6f;
        if (hh < 1f)      { r1 = c; g1 = x; b1 = 0; }
        else if (hh < 2f) { r1 = x; g1 = c; b1 = 0; }
        else if (hh < 3f) { r1 = 0; g1 = c; b1 = x; }
        else if (hh < 4f) { r1 = 0; g1 = x; b1 = c; }
        else if (hh < 5f) { r1 = x; g1 = 0; b1 = c; }
        else              { r1 = c; g1 = 0; b1 = x; }

        int r = (int) ((r1 + m) * 255f);
        int g = (int) ((g1 + m) * 255f);
        int b = (int) ((b1 + m) * 255f);

        r = MathHelper.clamp(r, 0, 255);
        g = MathHelper.clamp(g, 0, 255);
        b = MathHelper.clamp(b, 0, 255);

        return (r << 16) | (g << 8) | b;
    }
}
