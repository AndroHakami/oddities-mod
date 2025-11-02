// src/main/java/net/seep/odd/abilities/client/ClientCooldowns.java
package net.seep.odd.abilities.client;

import net.minecraft.client.MinecraftClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Client cooldowns driven by world time (20 tps) so the HUD exactly matches server cadence.
 * No per-tick decrement; we store start time + duration and compute remaining on demand.
 */
public final class ClientCooldowns {
    // slot -> duration (ticks) for the current window
    private static final Map<String, Integer> duration = new HashMap<>();
    // slot -> world time (ticks) when the window started
    private static final Map<String, Long>    startedAt = new HashMap<>();

    private ClientCooldowns() {}

    /** Start/refresh a cooldown window for a slot, using ticks from the server packet. */
    public static void set(String slot, int ticks) {
        int clamped = Math.max(0, ticks);
        duration.put(slot, clamped);

        long now = 0L;
        var mc = MinecraftClient.getInstance();
        if (mc != null && mc.world != null) now = mc.world.getTime();
        startedAt.put(slot, now);
    }

    /** Remaining ticks for the slot (0 means ready). */
    public static int get(String slot) {
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return 0;

        int max = duration.getOrDefault(slot, 0);
        if (max <= 0) return 0;

        long start = startedAt.getOrDefault(slot, 0L);
        long elapsed = mc.world.getTime() - start; // world time is 20 tps
        long remain = Math.max(0L, (long) max - elapsed);
        return (int) remain;
    }

    /** Max ticks for the current window (0 means unknown / not cooling). */
    public static int getMax(String slot) {
        return duration.getOrDefault(slot, 0);
    }

    public static boolean isCooling(String slot) {
        return get(slot) > 0;
    }

    /** Clear all client-side cooldown state (e.g., when power id changes). */
    public static void clear() {
        duration.clear();
        startedAt.clear();
    }

    /** No-op now; left for compatibility with existing init code. */
    public static void registerTicker() { /* time-based; nothing to tick */ }
}
