package net.seep.odd.abilities.tamer;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.*;

/** Persistent server data: each player’s party + one active summon. */
public final class TamerState extends PersistentState {
    public static final int MAX_PARTY = 6;

    private final Map<UUID, List<PartyMember>> parties = new HashMap<>();

    public static final class Active {
        public int index;
        public UUID entity;
        public Active(int index, UUID entity) { this.index = index; this.entity = entity; }
    }
    private final Map<UUID, Active> actives = new HashMap<>();

    public static TamerState get(ServerWorld sw) {
        return sw.getServer().getOverworld().getPersistentStateManager().getOrCreate(
                TamerState::fromNbt,
                TamerState::new,
                "odd_tamer"
        );
    }

    public static TamerState fromNbt(NbtCompound nbt) {
        TamerState s = new TamerState();

        // Parties
        NbtList owners = nbt.getList("owners", NbtCompound.COMPOUND_TYPE);
        for (int i = 0; i < owners.size(); i++) {
            NbtCompound o = owners.getCompound(i);
            UUID u = o.getUuid("u");
            NbtList arr = o.getList("party", NbtCompound.COMPOUND_TYPE);
            List<PartyMember> list = new ArrayList<>(arr.size());
            for (int j = 0; j < arr.size(); j++) list.add(PartyMember.fromNbt(arr.getCompound(j)));
            s.parties.put(u, list);
        }

        // Actives
        NbtList al = nbt.getList("actives", NbtCompound.COMPOUND_TYPE);
        for (int i = 0; i < al.size(); i++) {
            NbtCompound x = al.getCompound(i);
            s.actives.put(x.getUuid("u"), new Active(x.getInt("i"), x.getUuid("e")));
        }
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        // Parties
        NbtList owners = new NbtList();
        for (var e : parties.entrySet()) {
            NbtCompound o = new NbtCompound();
            o.putUuid("u", e.getKey());
            NbtList arr = new NbtList();
            for (PartyMember m : e.getValue()) arr.add(m.toNbt());
            o.put("party", arr);
            owners.add(o);
        }
        nbt.put("owners", owners);

        // Actives
        NbtList al = new NbtList();
        for (var e : actives.entrySet()) {
            NbtCompound x = new NbtCompound();
            x.putUuid("u", e.getKey());
            x.putInt("i", e.getValue().index);
            x.putUuid("e", e.getValue().entity);
            al.add(x);
        }
        nbt.put("actives", al);

        return nbt;
    }

    /* ===== API ===== */
    public List<PartyMember> partyOf(UUID player) {
        return parties.computeIfAbsent(player, k -> new ArrayList<>(MAX_PARTY));
    }

    public void addMember(UUID player, PartyMember m) {
        var list = partyOf(player);
        if (list.size() >= MAX_PARTY) return;
        list.add(m);
    }

    public void renameMember(UUID player, int index, String newName) {
        var list = partyOf(player);
        if (index < 0 || index >= list.size()) return;
        list.get(index).nickname = newName == null ? "" : newName;
    }
    // Add inside net/seep/odd/abilities/tamer/TamerState.java
    /** Iterate actives map without exposing it publicly (used by leveling). */
    java.util.Set<java.util.Map.Entry<java.util.UUID, Active>> activesEntrySet() {
        return actives.entrySet();
    }
    public java.util.Set<java.util.UUID> owners() {
        // read-only view is fine; we only iterate it
        return java.util.Collections.unmodifiableSet(parties.keySet());
    }


    public Active getActive(UUID owner) { return actives.get(owner); }
    public void setActive(UUID owner, int index, UUID entity) { actives.put(owner, new Active(index, entity)); }
    public void clearActive(UUID owner) { actives.remove(owner); }

    /** Find which player owns a currently-active pet entity UUID (used by leveling hooks). */
    public UUID findOwnerOfActiveEntity(UUID entityId) {
        for (var e : actives.entrySet()) {
            if (e.getValue().entity.equals(entityId)) return e.getKey();
        }
        return null;
    }
}
