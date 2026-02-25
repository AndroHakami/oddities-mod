// src/main/java/net/seep/odd/abilities/forger/ForgerData.java
package net.seep.odd.abilities.forger;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ForgerData extends PersistentState {
    private static final String KEY = "odd_forger_data";

    public static final class CombinerRef {
        public final Identifier dimension;
        public final BlockPos pos;
        public CombinerRef(Identifier dimension, BlockPos pos) {
            this.dimension = dimension;
            this.pos = pos;
        }
    }

    private final Map<UUID, CombinerRef> byPlayer = new HashMap<>();

    public static ForgerData get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
                ForgerData::fromNbt,
                ForgerData::new,
                KEY
        );
    }

    public CombinerRef get(UUID player) { return byPlayer.get(player); }

    public void set(UUID player, Identifier dim, BlockPos pos) {
        byPlayer.put(player, new CombinerRef(dim, pos));
        markDirty();
    }

    public void clear(UUID player) {
        if (byPlayer.remove(player) != null) markDirty();
    }

    public static ForgerData fromNbt(NbtCompound nbt) {
        ForgerData d = new ForgerData();
        NbtList list = nbt.getList("Combiners", 10);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            UUID uuid = c.getUuid("Player");
            Identifier dim = Identifier.tryParse(c.getString("Dim"));
            BlockPos pos = new BlockPos(c.getInt("X"), c.getInt("Y"), c.getInt("Z"));
            if (dim != null) d.byPlayer.put(uuid, new CombinerRef(dim, pos));
        }
        return d;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (var e : byPlayer.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putUuid("Player", e.getKey());
            c.putString("Dim", e.getValue().dimension.toString());
            c.putInt("X", e.getValue().pos.getX());
            c.putInt("Y", e.getValue().pos.getY());
            c.putInt("Z", e.getValue().pos.getZ());
            list.add(c);
        }
        nbt.put("Combiners", list);
        return nbt;
    }
}