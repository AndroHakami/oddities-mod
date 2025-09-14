package net.seep.odd.abilities.tamer;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.*;

/** Persistent server data: each player’s party + one active summon + mode. */
public final class TamerState extends PersistentState {
    public static final int MAX_PARTY = 6;

    public enum Mode { PASSIVE, FOLLOW, AGGRESSIVE }

    private final Map<UUID, List<PartyMember>> parties = new HashMap<>();

    public static final class Active {
        public int index;
        public UUID entity;
        public Mode mode;

        public Active(int index, UUID entity) {
            this(index, entity, Mode.FOLLOW);
        }
        public Active(int index, UUID entity, Mode mode) {
            this.index = index;
            this.entity = entity;
            this.mode = (mode == null ? Mode.FOLLOW : mode);
        }
    }

    private final Map<UUID, Active> actives = new HashMap<>();

    public static TamerState get(ServerWorld sw) {
        return sw.getServer().getOverworld().getPersistentStateManager().getOrCreate(
                TamerState::fromNbt, TamerState::new, "odd_tamer");
    }

    public static TamerState fromNbt(NbtCompound nbt) {
        TamerState s = new TamerState();

        // Parties
        NbtList owners = nbt.getList("owners", NbtCompound.COMPOUND_TYPE);
        for (int oi = 0; oi < owners.size(); oi++) {
            NbtCompound o = owners.getCompound(oi);
            UUID owner = o.getUuid("u");
            NbtList arr = o.getList("p", NbtCompound.COMPOUND_TYPE);
            List<PartyMember> list = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) list.add(PartyMember.fromNbt(arr.getCompound(i)));
            s.parties.put(owner, list);
        }

        // Actives
        NbtList al = nbt.getList("actives", NbtCompound.COMPOUND_TYPE);
        for (int i = 0; i < al.size(); i++) {
            NbtCompound x = al.getCompound(i);
            UUID owner = x.getUuid("u");
            int idx = x.getInt("i");
            UUID ent = x.getUuid("e");
            Mode mode = Mode.values()[Math.max(0, Math.min(Mode.values().length - 1, x.getInt("m")))];
            s.actives.put(owner, new Active(idx, ent, mode));
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
            for (PartyMember pm : e.getValue()) arr.add(pm.toNbt());
            o.put("p", arr);
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
            x.putInt("m", e.getValue().mode.ordinal());
            al.add(x);
        }
        nbt.put("actives", al);
        return nbt;
    }

    /* -------------------- API -------------------- */

    public List<PartyMember> partyOf(UUID owner) {
        return parties.computeIfAbsent(owner, k -> new ArrayList<>());
    }
    public void addMember(UUID player, PartyMember m) {
        var list = partyOf(player);
        if (list.size() >= MAX_PARTY) return;
        list.add(m);
    }

    public Set<Map.Entry<UUID, List<PartyMember>>> entries() { return parties.entrySet(); }
    public Set<UUID> owners() { return Collections.unmodifiableSet(parties.keySet()); }

    public Active getActive(UUID owner) { return actives.get(owner); }
    public void setActive(UUID owner, int index, UUID entity) { actives.put(owner, new Active(index, entity)); }
    public void setMode(UUID owner, Mode mode) { var a = actives.get(owner); if (a != null) a.mode = mode; }
    public void clearActive(UUID owner) { actives.remove(owner); }

    /** Find which player owns a currently-active pet entity UUID (used by leveling hooks). */
    public UUID findOwnerOfActiveEntity(UUID entityId) {
        for (var e : actives.entrySet()) {
            if (e.getValue().entity.equals(entityId)) return e.getKey();
        }
        return null;
    }
}
