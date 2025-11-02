package net.seep.odd.abilities.buddymorph;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.seep.odd.Oddities;

import java.util.*;

/**
 * Per-player store of befriended buddies with remaining transform charges.
 * Each newly befriended mob starts with 3 charges. When charges reach 0, it is removed.
 */
public final class BuddymorphData extends PersistentState {
    private static final String SAVE_NAME = Oddities.MOD_ID + "_buddymorph";
    private static final int DEFAULT_CHARGES = 3;

    // player -> (buddy id -> remaining charges)  (LinkedHashMap = stable order for UI)
    private final Map<UUID, LinkedHashMap<Identifier, Integer>> data = new HashMap<>();

    /* ---------- API ---------- */

    public boolean addBuddy(UUID player, Identifier id) {
        var map = data.computeIfAbsent(player, k -> new LinkedHashMap<>());
        if (map.containsKey(id)) return false;
        map.put(id, DEFAULT_CHARGES);
        markDirty();
        return true;
    }

    public boolean hasBuddy(UUID player, Identifier id) {
        var map = data.get(player);
        return map != null && map.containsKey(id);
    }

    /** Consume one charge; when it hits 0 remove the buddy. Returns true if consumed. */
    public boolean consumeCharge(UUID player, Identifier id) {
        var map = data.get(player);
        if (map == null) return false;
        Integer c = map.get(id);
        if (c == null || c <= 0) return false;
        c -= 1;
        if (c <= 0) map.remove(id);
        else map.put(id, c);
        markDirty();
        return true;
    }

    /** Remaining charges or 0 if not befriended. */
    public int getCharges(UUID player, Identifier id) {
        var map = data.get(player);
        Integer c = (map == null) ? null : map.get(id);
        return c == null ? 0 : c;
    }

    /** Ordered set of IDs (legacy use). */
    public Set<Identifier> getList(UUID player) {
        var map = data.get(player);
        return map == null ? Collections.emptySet() : new LinkedHashSet<>(map.keySet());
    }

    /** Ordered map of IDs -> remaining charges for networking/UI. */
    public LinkedHashMap<Identifier, Integer> getListWithCharges(UUID player) {
        var map = data.get(player);
        return map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map);
    }

    /* ---------- Persistence ---------- */

    public static BuddymorphData get(ServerWorld world) {
        PersistentStateManager m = world.getPersistentStateManager();
        return m.getOrCreate(BuddymorphData::fromNbt, BuddymorphData::new, SAVE_NAME);
    }

    private static BuddymorphData fromNbt(NbtCompound nbt) {
        BuddymorphData d = new BuddymorphData();
        NbtList players = nbt.getList("players", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < players.size(); i++) {
            NbtCompound pTag = players.getCompound(i);
            UUID u = pTag.getUuid("u");
            NbtList buddies = pTag.getList("b", NbtElement.COMPOUND_TYPE);
            LinkedHashMap<Identifier, Integer> map = new LinkedHashMap<>();
            for (int j = 0; j < buddies.size(); j++) {
                NbtCompound b = buddies.getCompound(j);
                Identifier id = new Identifier(b.getString("id"));
                int charges = b.getInt("c");
                if (charges > 0) map.put(id, charges);
            }
            if (!map.isEmpty()) d.data.put(u, map);
        }
        return d;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList players = new NbtList();
        for (var entry : data.entrySet()) {
            NbtCompound pTag = new NbtCompound();
            pTag.putUuid("u", entry.getKey());
            NbtList buddies = new NbtList();
            for (var e : entry.getValue().entrySet()) {
                NbtCompound b = new NbtCompound();
                b.putString("id", e.getKey().toString());
                b.putInt("c", e.getValue());
                buddies.add(b);
            }
            pTag.put("b", buddies);
            players.add(pTag);
        }
        nbt.put("players", players);
        return nbt;
    }
}
