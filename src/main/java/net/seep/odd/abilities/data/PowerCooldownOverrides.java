// src/main/java/net/seep/odd/abilities/data/PowerCooldownOverrides.java
package net.seep.odd.abilities.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;

public final class PowerCooldownOverrides extends PersistentState {
    private static final String STORAGE = "odd_cd_overrides"; // powerId#slot -> ticks
    private final Map<String, Long> map = new HashMap<>();

    public static PowerCooldownOverrides get(MinecraftServer server) {
        ServerWorld w = server.getOverworld();
        return w.getPersistentStateManager().getOrCreate(PowerCooldownOverrides::fromNbt, PowerCooldownOverrides::new, STORAGE);
    }

    public long getOrDefault(String key, long def) { return map.getOrDefault(key, def); }
    public void set(String key, long ticks) { map.put(key, Math.max(0L, ticks)); markDirty(); }
    public void clear(String key) { map.remove(key); markDirty(); }

    public static PowerCooldownOverrides fromNbt(NbtCompound nbt) {
        PowerCooldownOverrides s = new PowerCooldownOverrides();
        NbtCompound m = nbt.getCompound("m");
        for (String k : m.getKeys()) s.map.put(k, m.getLong(k));
        return s;
    }
    @Override public NbtCompound writeNbt(NbtCompound out) {
        NbtCompound m = new NbtCompound();
        for (var e : map.entrySet()) m.putLong(e.getKey(), e.getValue());
        out.put("m", m); return out;
    }
}
