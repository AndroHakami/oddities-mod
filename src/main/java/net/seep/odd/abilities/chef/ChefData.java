package net.seep.odd.abilities.chef;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChefData extends PersistentState {
    private static final String KEY = "odd_chef_data";

    public static final class CookerRef {
        public final Identifier dimension;
        public final BlockPos pos;
        public CookerRef(Identifier dimension, BlockPos pos) {
            this.dimension = dimension;
            this.pos = pos;
        }
    }

    private final Map<UUID, CookerRef> cookerByPlayer = new HashMap<>();

    public static ChefData get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
                ChefData::fromNbt,
                ChefData::new,
                KEY
        );
    }

    public CookerRef getCooker(UUID player) {
        return cookerByPlayer.get(player);
    }

    public void setCooker(UUID player, Identifier dim, BlockPos pos) {
        cookerByPlayer.put(player, new CookerRef(dim, pos));
        markDirty();
    }

    public void clearCooker(UUID player) {
        if (cookerByPlayer.remove(player) != null) markDirty();
    }

    public static ChefData fromNbt(NbtCompound nbt) {
        ChefData d = new ChefData();
        NbtList list = nbt.getList("Cookers", 10);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            UUID uuid = c.getUuid("Player");
            Identifier dim = Identifier.tryParse(c.getString("Dim"));
            BlockPos pos = new BlockPos(c.getInt("X"), c.getInt("Y"), c.getInt("Z"));
            if (dim != null) d.cookerByPlayer.put(uuid, new CookerRef(dim, pos));
        }
        return d;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (var e : cookerByPlayer.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putUuid("Player", e.getKey());
            c.putString("Dim", e.getValue().dimension.toString());
            c.putInt("X", e.getValue().pos.getX());
            c.putInt("Y", e.getValue().pos.getY());
            c.putInt("Z", e.getValue().pos.getZ());
            list.add(c);
        }
        nbt.put("Cookers", list);
        return nbt;
    }
}
