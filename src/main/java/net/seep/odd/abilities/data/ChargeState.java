package net.seep.odd.abilities.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player charge lanes with queue-style regeneration.
 *
 * Key model:
 * - For each lane (string key) per player, we store:
 *   have (current charges), max, recharge (ticks between charges),
 *   nextReady (server world tick when the next +1 will be granted; 0 means no timer running).
 *
 * Semantics:
 * - tick(): while (have < max && nextReady > 0 && now >= nextReady) { have++; nextReady = (have < max ? nextReady + recharge : 0); }
 * - consume(): if have>0 then have--; if no timer running and recharge>0 -> start one at now+recharge; otherwise keep the existing timer.
 *   (This preserves an in-flight timer, so consuming does NOT push it back.)
 */
public class ChargeState extends PersistentState {
    private static final String STORAGE_NAME = "oddities_power_charges";

    /** players -> (laneKey -> lane) */
    private final Map<UUID, Map<String, Lane>> data = new HashMap<>();

    public static ChargeState get(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        return world.getPersistentStateManager().getOrCreate(ChargeState::fromNbt, ChargeState::new, STORAGE_NAME);
    }

    /* ---------------- lane model ---------------- */

    private static final class Lane {
        int  have     = 0;
        int  max      = 1;
        long recharge = 0L;
        long nextReady = 0L; // 0 => no timer running

        void ensureMax(int newMax) {
            if (newMax <= 0) newMax = 1;
            if (max != newMax) {
                // If we ever change max, clamp have to the new max.
                max = newMax;
                if (have > max) have = max;
            }
        }
    }

    private Lane lane(UUID uuid, String key) {
        Map<String, Lane> lanes = data.computeIfAbsent(uuid, u -> new HashMap<>());
        return lanes.computeIfAbsent(key, k -> new Lane());
    }

    /* ---------------- public API ---------------- */

    /** Progress the lane up to 'now'. Also ensures lane is initialized and clamped. */
    public void tick(String key, UUID uuid, int max, long now) {
        Lane ln = lane(uuid, key);

        // First-time lane => start full
        if (ln.max == 1 && ln.have == 0 && ln.nextReady == 0 && ln.recharge == 0L) {
            ln.max = Math.max(1, max);
            ln.have = ln.max;
        } else {
            ln.ensureMax(max);
        }

        // If recharge is zero or negative, lane is always full.
        if (ln.recharge <= 0L) {
            ln.have = ln.max;
            ln.nextReady = 0L;
            return;
        }

        // Grant queued charges while time has passed.
        if (ln.have < ln.max && ln.nextReady > 0L) {
            while (ln.have < ln.max && now >= ln.nextReady) {
                ln.have++;
                if (ln.have < ln.max) {
                    ln.nextReady += ln.recharge; // queue next
                } else {
                    ln.nextReady = 0L; // full -> no timer
                }
            }
        }

        markDirty();
    }

    /** Current charges (after a tick() you call just before). */
    public int get(String key, UUID uuid, int max) {
        Lane ln = lane(uuid, key);
        // If lane was never used, treat as full
        if (ln.max == 1 && ln.have == 0 && ln.nextReady == 0 && ln.recharge == 0L) {
            ln.max = Math.max(1, max);
            ln.have = ln.max;
            markDirty();
        } else {
            ln.ensureMax(max);
        }
        return ln.have;
    }

    /**
     * Consume one charge if available.
     * - Preserves an in-flight timer (does not reset or extend it).
     * - If lane was full (no timer) and we consume, we start the timer at now+recharge.
     */
    public boolean consume(String key, UUID uuid, int max, long now, long recharge) {
        Lane ln = lane(uuid, key);

        // Initialize lane if first use
        if (ln.max == 1 && ln.have == 0 && ln.nextReady == 0 && ln.recharge == 0L) {
            ln.max = Math.max(1, max);
            ln.have = ln.max;
        } else {
            ln.ensureMax(max);
        }

        // Update (tick) first so have/nextReady reflect any elapsed time
        if (ln.recharge != recharge) {
            ln.recharge = Math.max(0L, recharge);
        }
        tick(key, uuid, ln.max, now);

        if (ln.have <= 0) {
            return false;
        }

        // Consume one
        ln.have--;

        // Timer logic:
        // If we were full (no timer running) and just consumed, start the timer.
        // If a timer is already running (nextReady > 0), KEEP IT as-is (do not push it).
        if (ln.recharge > 0L && ln.nextReady == 0L) {
            ln.nextReady = now + ln.recharge;
        }

        markDirty();
        return true;
    }

    /** Snapshot after having called tick(). */
    public Snapshot snapshot(String key, UUID uuid, int max, long now) {
        Lane ln = lane(uuid, key);
        // Make sure the lane shape is consistent even if never used
        if (ln.max == 1 && ln.have == 0 && ln.nextReady == 0 && ln.recharge == 0L) {
            ln.max = Math.max(1, max);
            ln.have = ln.max;
        } else {
            ln.ensureMax(max);
        }
        return new Snapshot(ln.have, ln.max, ln.recharge, ln.nextReady, now);
    }

    /* ---------------- snapshot record ---------------- */

    public record Snapshot(int have, int max, long recharge, long nextReady, long now) {}

    /* ---------------- persistence ---------------- */

    public static ChargeState fromNbt(NbtCompound nbt) {
        ChargeState s = new ChargeState();
        NbtCompound players = nbt.getCompound("players");
        for (String uuidStr : players.getKeys()) {
            UUID id;
            try { id = UUID.fromString(uuidStr); } catch (IllegalArgumentException ex) { continue; }
            NbtCompound lanesNbt = players.getCompound(uuidStr);
            Map<String, Lane> lanes = new HashMap<>();
            for (String laneKey : lanesNbt.getKeys()) {
                NbtCompound lnNbt = lanesNbt.getCompound(laneKey);
                Lane ln = new Lane();
                ln.have      = lnNbt.getInt("have");
                ln.max       = Math.max(1, lnNbt.getInt("max"));
                ln.recharge  = Math.max(0L, lnNbt.getLong("recharge"));
                ln.nextReady = Math.max(0L, lnNbt.getLong("nextReady"));
                lanes.put(laneKey, ln);
            }
            s.data.put(id, lanes);
        }
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound players = new NbtCompound();
        for (var pe : data.entrySet()) {
            NbtCompound lanesNbt = new NbtCompound();
            for (var le : pe.getValue().entrySet()) {
                Lane ln = le.getValue();
                NbtCompound lnNbt = new NbtCompound();
                lnNbt.putInt("have", ln.have);
                lnNbt.putInt("max", ln.max);
                lnNbt.putLong("recharge", ln.recharge);
                lnNbt.putLong("nextReady", ln.nextReady);
                lanesNbt.put(le.getKey(), lnNbt);
            }
            players.put(pe.getKey().toString(), lanesNbt);
        }
        nbt.put("players", players);
        return nbt;
    }
}
