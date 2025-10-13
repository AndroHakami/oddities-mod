package net.seep.odd.abilities.data;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.UUID;

/** Per-player, per-slot charge tracking. */
public final class ChargeState {
    private final Map<UUID, Entry> map = new Object2ObjectOpenHashMap<>();
    private static final String K = "charges";

    public static ChargeState get(MinecraftServer server) {
        // piggyback CooldownState's singleton style without touching it:
        return server.getOverworld().getPersistentStateManager().getOrCreate(
                nbt -> new ChargeState(), // no disk persistence (stateless regen)
                ChargeState::new, K
        );
    }

    private static final class Entry {
        // key is "powerId#slot"
        final Map<String, SlotCharges> slots = new Object2ObjectOpenHashMap<>();
    }
    private static final class SlotCharges {
        int current;
        // we track 3 independent lane timers max; if power has fewer, only first N used
        long[] nextReady; // world time when this lane becomes available again
    }

    private Entry E(UUID id) { return map.computeIfAbsent(id, u -> new Entry()); }

    public int get(String powerKey, UUID player, int max) {
        var e = E(player);
        var sc = e.slots.get(powerKey);
        return (sc == null) ? max : sc.current;
    }

    public void ensure(String powerKey, UUID player, int max) {
        var e = E(player);
        var sc = e.slots.computeIfAbsent(powerKey, k -> {
            var s = new SlotCharges();
            s.current = max;
            s.nextReady = new long[Math.max(1, max)];
            return s;
        });
        if (sc.nextReady == null || sc.nextReady.length != Math.max(1, max)) {
            long[] nr = new long[Math.max(1, max)];
            if (sc.nextReady != null) System.arraycopy(sc.nextReady, 0, nr, 0, Math.min(nr.length, sc.nextReady.length));
            sc.nextReady = nr;
        }
        if (sc.current > max) sc.current = max;
    }

    /** Try to consume one charge and assign its lane cooldown. Returns lane index or -1. */
    public int consume(String powerKey, UUID player, int max, long now, long refillTicks) {
        ensure(powerKey, player, max);
        var sc = E(player).slots.get(powerKey);
        if (sc.current <= 0) return -1;

        // pick the earliest-ready lane that's <= now
        int lane = -1;
        long best = Long.MIN_VALUE;
        for (int i = 0; i < sc.nextReady.length; i++) {
            long r = sc.nextReady[i];
            if (r <= now && r >= best) { best = r; lane = i; }
        }
        if (lane < 0) { // all lanes recharging; still allow consume and assign the latest lane
            long latest = Long.MIN_VALUE; int idx = 0;
            for (int i = 0; i < sc.nextReady.length; i++) {
                if (sc.nextReady[i] > latest) { latest = sc.nextReady[i]; idx = i; }
            }
            lane = idx;
        }

        sc.current--;
        sc.nextReady[lane] = now + Math.max(0L, refillTicks);
        return lane;
    }

    /** Regenerate charges whose lane timers finished. */
    public void tick(String powerKey, UUID player, int max, long now) {
        ensure(powerKey, player, max);
        var sc = E(player).slots.get(powerKey);
        for (int i = 0; i < sc.nextReady.length; i++) {
            if (sc.nextReady[i] != 0 && sc.nextReady[i] <= now) {
                sc.nextReady[i] = 0;
                if (sc.current < max) sc.current++;
            }
        }
    }
}
