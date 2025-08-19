package net.seep.odd.abilities.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShadowMeterState extends PersistentState {
    private static final String STORAGE = "odd_shadow_meter";
    private final Map<UUID, Integer> energy = new HashMap<>(); // 0..100

    public static ShadowMeterState get(MinecraftServer server) {
        ServerWorld w = server.getOverworld();
        return w.getPersistentStateManager().getOrCreate(ShadowMeterState::fromNbt, ShadowMeterState::new, STORAGE);
    }

    public int get(UUID id) { return energy.getOrDefault(id, 100); }
    public void set(UUID id, int value) { energy.put(id, Math.max(0, Math.min(100, value))); markDirty(); }

    public static ShadowMeterState fromNbt(NbtCompound nbt) {
        ShadowMeterState s = new ShadowMeterState();
        NbtCompound m = nbt.getCompound("m");
        for (String k : m.getKeys()) s.energy.put(UUID.fromString(k), m.getInt(k));
        return s;
    }

    @Override public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound m = new NbtCompound();
        for (var e : energy.entrySet()) m.putInt(e.getKey().toString(), e.getValue());
        nbt.put("m", m);
        return nbt;
    }
}