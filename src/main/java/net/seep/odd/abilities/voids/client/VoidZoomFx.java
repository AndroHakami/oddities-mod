package net.seep.odd.abilities.voids.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/** Lightweight FOV scaler: ease in over duration, then ease back on end(). */
public final class VoidZoomFx {
    private VoidZoomFx() {}

    private static boolean active = false;
    private static int totalTicks, tick;
    private static float minScale = 0.70f; // 70% FOV (zoom in)
    private static float prevFov, baseFov;
    private static boolean installed = false;

    public static void begin(float seconds, float targetScale) {
        var mc = MinecraftClient.getInstance();
        if (mc == null) return;

        if (!installed) {
            installed = true;
            ClientTickEvents.END_CLIENT_TICK.register(c -> update());
        }

        baseFov = mc.options.getFov().getValue(); // current FOV
        prevFov = baseFov;
        minScale = targetScale;
        totalTicks = Math.max(1, Math.round(seconds * 20f));
        tick = 0;
        active = true;
    }

    public static void end() {
        // let update() ease back for ~8 ticks
        tick = Math.max(tick, totalTicks); // jump to end phase
    }

    private static void update() {
        var mc = MinecraftClient.getInstance();
        if (mc == null) return;
        if (!active) return;

        float t;
        if (tick <= totalTicks) {
            // ease in
            t = tick / (float) totalTicks; // 0..1
            float scale = lerp(1f, minScale, easeOutCubic(t));
            float fov = baseFov * scale;
            mc.options.getFov().setValue((int) fov);
        } else {
            // ease out (fixed 8 ticks)
            int back = tick - totalTicks;
            int backLen = 8;
            if (back >= backLen) {
                mc.options.getFov().setValue((int) baseFov);
                active = false;
                return;
            }
            t = back / (float) backLen;
            float scale = lerp(minScale, 1f, easeInCubic(t));
            float fov = baseFov * scale;
            mc.options.getFov().setValue((int) fov);
        }
        tick++;
    }

    private static float lerp(float a, float b, float t){ return a + (b - a) * t; }
    private static float easeOutCubic(float x){ return 1 - (float)Math.pow(1 - x, 3); }
    private static float easeInCubic(float x){ return x * x * x; }

}
