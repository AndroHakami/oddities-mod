package net.seep.odd.abilities.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PowerState extends PersistentState {
    private static final String STORAGE_NAME = "oddities_powers";
    private final Map<UUID, String> powers = new HashMap<>();

    public static PowerState get(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        return world.getPersistentStateManager().getOrCreate(PowerState::fromNbt, PowerState::new, STORAGE_NAME);
    }

    public String get(UUID uuid) { return powers.getOrDefault(uuid, ""); }
    public void set(UUID uuid, String id) { if (id == null || id.isEmpty()) powers.remove(uuid); else powers.put(uuid, id); markDirty(); }
    public void clear(UUID uuid) { powers.remove(uuid); markDirty(); }

    public static PowerState fromNbt(NbtCompound nbt) {
        PowerState s = new PowerState();
        NbtCompound map = nbt.getCompound("powers");
        for (String key : map.getKeys()) {
            try {
                s.powers.put(UUID.fromString(key), map.getString(key));
            } catch (IllegalArgumentException ignored) {}
        }
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound map = new NbtCompound();
        for (var e : powers.entrySet()) map.putString(e.getKey().toString(), e.getValue());
        nbt.put("powers", map);
        return nbt;
    }
}