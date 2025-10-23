package net.seep.odd.abilities.data;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.Map;
import java.util.UUID;

/**
 * Tracks per-player, per-lane charges with pipelined recharge.
 * Lane key format is up to the caller (Power id + "#" + slot in PowerAPI).
 */
public final class ChargeState extends PersistentState {
    private static final String SAVE_ID = "charges";
    private static final String PLAYERS_KEY = "players";

    // players -> (laneKey -> Lane)
    private final Map<UUID, Map<String, Lane>> data = new Object2ObjectOpenHashMap<>();

    public static ChargeState get(MinecraftServer server) {
        PersistentStateManager psm = server.getOverworld().getPersistentStateManager();
        return psm.getOrCreate(ChargeState::fromNbt, ChargeState::new, SAVE_ID);
    }

    private static ChargeState fromNbt(NbtCompound nbt) {
        ChargeState s = new ChargeState();
        if (nbt.contains(PLAYERS_KEY, NbtElement.COMPOUND_TYPE)) {
            NbtCompound players = nbt.getCompound(PLAYERS_KEY);
            for (String uuidStr : players.getKeys()) {
                try {
                    UUID id = UUID.fromString(uuidStr);
                    NbtCompound lanesN = players.getCompound(uuidStr);
                    Map<String, Lane> lanes = new Object2ObjectOpenHashMap<>();
                    for (String laneKey : lanesN.getKeys()) {
                        lanes.put(laneKey, Lane.fromNbt(lanesN.getCompound(laneKey)));
                    }
                    s.data.put(id, lanes);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return s;
    }

    public ChargeState() {}

    /* ---------------------- public API used by PowerAPI ---------------------- */

    /** Replenish any ready charges for this lane (lazy tick). */
    public void tick(String laneKey, UUID player, int max, long now) {
        Lane lane = lane(player, laneKey);
        if (lane.max != max) {
            lane.max = Math.max(1, max);
            if (lane.have > lane.max) lane.have = lane.max;
        }
        boolean changed = false;

        // If recharge is 0, lane is always full
        if (lane.recharge <= 0) {
            if (lane.have != lane.max) { lane.have = lane.max; changed = true; }
        } else {
            while (lane.have < lane.max && now >= lane.nextReady) {
                lane.have++;
                lane.nextReady += lane.recharge; // pipeline consecutive refills
                changed = true;
            }
        }

        if (changed) setDirty(true);
    }

    /** Current available charges (after a previous tick call). */
    public int get(String laneKey, UUID player, int max) {
        Lane lane = lane(player, laneKey);
        if (lane.max != max) {
            lane.max = Math.max(1, max);
            if (lane.have > lane.max) lane.have = lane.max;
        }
        return Math.min(lane.have, lane.max);
    }

    /** Consume one charge and schedule its recharge. */
    public void consume(String laneKey, UUID player, int max, long now, long rechargeTicks) {
        Lane lane = lane(player, laneKey);
        int newMax = Math.max(1, max);
        if (lane.max != newMax) {
            lane.max = newMax;
            if (lane.have > lane.max) lane.have = lane.max;
        }

        lane.recharge = Math.max(0L, rechargeTicks);

        if (lane.have > 0) {
            lane.have--;

            if (lane.recharge <= 0) {
                // instantaneous refill: always full
                lane.have = lane.max;
                lane.nextReady = now; // irrelevant
            } else {
                // Pipeline the next ready time: if none scheduled yet, start now+rt; otherwise push it.
                lane.nextReady = Math.max(lane.nextReady, now) + lane.recharge;
            }

            setDirty(true);
        }
    }

    /** Snapshot for S2C (have/max/recharge/nextReady + the server's "now"). */
    public Snapshot snapshot(String laneKey, UUID player, int max, long now) {
        tick(laneKey, player, max, now);
        Lane lane = lane(player, laneKey);
        return new Snapshot(Math.min(lane.have, lane.max), lane.max, lane.recharge, lane.nextReady, now);
    }

    /* ---------------------- internals + serialization ---------------------- */

    private Lane lane(UUID player, String laneKey) {
        Map<String, Lane> lanes = data.computeIfAbsent(player, u -> new Object2ObjectOpenHashMap<>());
        return lanes.computeIfAbsent(laneKey, k -> {
            Lane l = new Lane();
            l.max = 1;
            l.have = 1;
            l.recharge = 0;
            l.nextReady = 0;
            return l;
        });
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound players = new NbtCompound();
        for (var ePlayer : data.entrySet()) {
            NbtCompound lanesN = new NbtCompound();
            for (var eLane : ePlayer.getValue().entrySet()) {
                lanesN.put(eLane.getKey(), eLane.getValue().toNbt());
            }
            players.put(ePlayer.getKey().toString(), lanesN);
        }
        nbt.put(PLAYERS_KEY, players);
        return nbt;
    }

    /* A lane of charges for one slot */
    private static final class Lane {
        int  max;        // last-seen capacity
        int  have;       // current available charges
        long recharge;   // ticks per charge
        long nextReady;  // when the next charge becomes available (pipelines)

        NbtCompound toNbt() {
            NbtCompound n = new NbtCompound();
            n.putInt("max", max);
            n.putInt("have", have);
            n.putLong("recharge", recharge);
            n.putLong("nextReady", nextReady);
            return n;
        }
        static Lane fromNbt(NbtCompound n) {
            Lane l = new Lane();
            if (n.contains("max", NbtElement.INT_TYPE))        l.max = Math.max(1, n.getInt("max"));
            if (n.contains("have", NbtElement.INT_TYPE))       l.have = Math.max(0, n.getInt("have"));
            if (n.contains("recharge", NbtElement.LONG_TYPE))  l.recharge = Math.max(0L, n.getLong("recharge"));
            if (n.contains("nextReady", NbtElement.LONG_TYPE)) l.nextReady = n.getLong("nextReady");
            if (l.max <= 0) l.max = 1;
            if (l.have > l.max) l.have = l.max;
            return l;
        }
    }

    /** Immutable snapshot for network. */
    public static final class Snapshot {
        public final int have, max;
        public final long recharge, nextReady, now;
        public Snapshot(int have, int max, long recharge, long nextReady, long now) {
            this.have = have; this.max = max; this.recharge = recharge; this.nextReady = nextReady; this.now = now;
        }
    }
}
