package net.seep.odd.abilities.spotted;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BuddyPersistentState extends PersistentState {
    public static final String KEY = "odd_spotted_buddies";

    public static final class Ref {
        public final UUID entityUuid;
        public final Identifier dimension;
        public final int generation;
        public Ref(UUID e, Identifier d, int g) { this.entityUuid = e; this.dimension = d; this.generation = g; }
    }

    private final Map<UUID, Ref> byOwner = new HashMap<>();   // ownerUuid -> ref
    private final Map<UUID, Integer> gens = new HashMap<>();  // ownerUuid -> gen counter

    public Ref get(UUID owner) { return byOwner.get(owner); }

    public void set(UUID owner, UUID entityUuid, Identifier dim, int generation) {
        byOwner.put(owner, new Ref(entityUuid, dim, generation));
        markDirty();
    }

    public void clear(UUID owner) {
        if (byOwner.remove(owner) != null) markDirty();
    }

    public void clearIf(UUID owner, UUID entityUuid) {
        Ref r = byOwner.get(owner);
        if (r != null && r.entityUuid.equals(entityUuid)) {
            byOwner.remove(owner);
            markDirty();
        }
    }

    public int nextGen(UUID owner) {
        int g = gens.getOrDefault(owner, 0) + 1;
        gens.put(owner, g);
        markDirty();
        return g;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList map = new NbtList();
        for (var e : byOwner.entrySet()) {
            NbtCompound t = new NbtCompound();
            t.putUuid("owner", e.getKey());
            t.putUuid("entity", e.getValue().entityUuid);
            t.putString("dim", e.getValue().dimension.toString());
            t.putInt("gen", e.getValue().generation);
            map.add(t);
        }
        nbt.put("map", map);

        NbtList genList = new NbtList();
        for (var e : gens.entrySet()) {
            NbtCompound t = new NbtCompound();
            t.putUuid("owner", e.getKey());
            t.putInt("gen", e.getValue());
            genList.add(t);
        }
        nbt.put("gens", genList);
        return nbt;
    }

    public static BuddyPersistentState readNbt(NbtCompound nbt) {
        BuddyPersistentState s = new BuddyPersistentState();
        if (nbt.contains("map", NbtElement.LIST_TYPE)) {
            NbtList list = nbt.getList("map", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound t = list.getCompound(i);
                UUID owner = t.getUuid("owner");
                UUID ent   = t.getUuid("entity");
                Identifier dim = new Identifier(t.getString("dim"));
                int gen = t.getInt("gen");
                s.byOwner.put(owner, new Ref(ent, dim, gen));
            }
        }
        if (nbt.contains("gens", NbtElement.LIST_TYPE)) {
            NbtList list = nbt.getList("gens", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound t = list.getCompound(i);
                s.gens.put(t.getUuid("owner"), t.getInt("gen"));
            }
        }
        return s;
    }

    public static BuddyPersistentState get(MinecraftServer server) {
        ServerWorld ow = server.getOverworld();
        return ow.getPersistentStateManager().getOrCreate(
                BuddyPersistentState::readNbt, BuddyPersistentState::new, KEY
        );
    }
}
