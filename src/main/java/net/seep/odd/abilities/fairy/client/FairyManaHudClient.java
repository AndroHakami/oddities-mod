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

    private static float mana = 0f;
    private static float max  = 100f;

    // ✅ “heartbeat” gate: HUD is visible only if we recently got S2C mana sync
    private static int ticksSinceSync = 9999;
    private static final int SHOW_TIMEOUT_TICKS = 40; // ~2s (sync is every 5 ticks)

    /** Call once from client init. */
    public static void init() {
        if (inited) return;
        inited = true;

        // If you have the beam client file, init it here
        try { FairyBeamClient.init(); } catch (Throwable ignored) {}

        ClientPlayNetworking.registerGlobalReceiver(FairyPower.S2C_MANA_SYNC, (client, handler, buf, resp) -> {
            boolean hf = buf.readBoolean(); // keep reading (compat)
            float m  = buf.readFloat();
            float mx = buf.readFloat();
            client.execute(() -> {
                mana = m;
                max  = (mx <= 0f) ? 100f : mx;
                ticksSinceSync = 0;
            });
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());

        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;

            // ✅ count up locally
            ticksSinceSync++;

            // ✅ only show while receiving fairy mana sync packets
            if (ticksSinceSync > SHOW_TIMEOUT_TICKS) return;

            int sw = mc.getWindow().getScaledWidth();
            int sh = mc.getWindow().getScaledHeight();

            int barW = 120;
            int barH = 8;
            int x = (sw - barW) / 2;
            int y = sh - 49;

            float frac = (max <= 0f) ? 0f : MathHelper.clamp(mana / max, 0f, 1f);
            int fillW = (int) (barW * frac);

            ctx.fill(x - 1, y - 1, x + barW + 1, y + barH + 1, 0xAAFFFFFF);
            ctx.fill(x, y, x + barW, y + barH, 0x66000000);

            long t = (mc.world != null ? mc.world.getTime() : (System.currentTimeMillis() / 50L));
            float baseHue = (t % 240L) / 240f;

            for (int i = 0; i < fillW; i++) {
                float h = (baseHue + (i / (float) barW) * 0.35f) % 1f;
                int rgb = hsvToRgb(h, 0.80f, 1.0f);
                ctx.fill(x + i, y, x + i + 1, y + barH, 0xFF000000 | rgb);
            }
        });
    }

    public static void reset() {
        mana = 0f;
        max = 100f;
        ticksSinceSync = 9999;
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

        int r = MathHelper.clamp((int)((r1 + m) * 255f), 0, 255);
        int g = MathHelper.clamp((int)((g1 + m) * 255f), 0, 255);
        int b = MathHelper.clamp((int)((b1 + m) * 255f), 0, 255);

        return (r << 16) | (g << 8) | b;
    }
}