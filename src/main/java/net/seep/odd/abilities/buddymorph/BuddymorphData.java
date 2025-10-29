package net.seep.odd.abilities.buddymorph;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.*;

/** Per-world storage for Buddymorph. Keeps unique, insertion-ordered ids. */
public final class BuddymorphData extends PersistentState {
    private static final String KEY = "odd_buddymorph_data_v1";

    // uuid -> ordered set of buddy ids
    private final Map<UUID, LinkedHashSet<Identifier>> buddies = new HashMap<>();

    /** Load (or create) this world's state. */
    public static BuddymorphData get(ServerWorld sw) {
        PersistentStateManager psm = sw.getServer().getOverworld().getPersistentStateManager();
        return psm.getOrCreate(BuddymorphData::fromNbt, BuddymorphData::new, KEY);
    }

    /* ---------- public API ---------- */

    /** Copy as list for networking/UI (preserves order). */
    public List<Identifier> getList(UUID player) {
        return new ArrayList<>(set(player));
    }

    public boolean hasBuddy(UUID player, Identifier id) {
        return set(player).contains(id);
    }

    /** @return true if newly added (never duplicates). */
    public boolean addBuddy(UUID player, Identifier id) {
        boolean added = set(player).add(id);
        if (added) setDirty(true);
        return added;
    }

    /* ---------- internal ---------- */

    private LinkedHashSet<Identifier> set(UUID u) {
        return buddies.computeIfAbsent(u, k -> new LinkedHashSet<>());
    }

    /* ---------- NBT ---------- */

    /** Static loader used by PersistentStateManager#getOrCreate. */
    public static BuddymorphData fromNbt(NbtCompound tag) {
        BuddymorphData d = new BuddymorphData();
        d.readInto(tag);
        return d;
    }

    /** Write persistent data. */
    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        NbtList players = new NbtList(); // each: { id: UUID, buddies: [ "namespace:type", ... ] }

        for (var e : buddies.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putUuid("id", e.getKey());

            NbtList ids = new NbtList();
            for (Identifier id : e.getValue()) {
                ids.add(NbtString.of(id.toString())); // write as plain strings
            }
            c.put("buddies", ids);

            players.add(c);
        }

        tag.put("players", players);
        return tag;
    }

    /** Helper that fills this instance from NBT. */
    private void readInto(NbtCompound tag) {
        buddies.clear();

        NbtList players = tag.getList("players", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < players.size(); i++) {
            NbtCompound c = players.getCompound(i);
            UUID uuid = c.getUuid("id");

            LinkedHashSet<Identifier> set = new LinkedHashSet<>();
            NbtList ids = c.getList("buddies", NbtElement.STRING_TYPE);
            for (int j = 0; j < ids.size(); j++) {
                set.add(new Identifier(ids.getString(j)));
            }
            buddies.put(uuid, set);
        }
    }
}
