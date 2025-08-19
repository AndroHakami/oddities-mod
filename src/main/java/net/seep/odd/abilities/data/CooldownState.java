package net.seep.odd.abilities.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Stores last-use tick for each (player, powerId). */
public class CooldownState extends PersistentState {
    private static final String STORAGE_NAME = "oddities_power_cooldowns";

    // lastUseTicks.get(playerUUID).get(powerId) -> tick
    private final Map<UUID, Map<String, Long>> lastUseTicks = new HashMap<>();

    public static CooldownState get(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        return world.getPersistentStateManager().getOrCreate(CooldownState::fromNbt, CooldownState::new, STORAGE_NAME);
    }

    public long getLastUse(UUID uuid, String powerId) {
        Map<String, Long> map = lastUseTicks.get(uuid);
        return (map == null) ? 0L : map.getOrDefault(powerId, 0L);
    }

    public void setLastUse(UUID uuid, String powerId, long tick) {
        lastUseTicks.computeIfAbsent(uuid, k -> new HashMap<>()).put(powerId, tick);
        markDirty();
    }

    public static CooldownState fromNbt(NbtCompound nbt) {
        CooldownState s = new CooldownState();
        NbtCompound outer = nbt.getCompound("players");
        for (String uuidStr : outer.getKeys()) {
            UUID id;
            try { id = UUID.fromString(uuidStr); } catch (IllegalArgumentException e) { continue; }
            NbtCompound inner = outer.getCompound(uuidStr);
            Map<String, Long> map = new HashMap<>();
            for (String powerId : inner.getKeys()) {
                map.put(powerId, inner.getLong(powerId));
            }
            s.lastUseTicks.put(id, map);
        }
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound outer = new NbtCompound();
        for (var e : lastUseTicks.entrySet()) {
            NbtCompound inner = new NbtCompound();
            for (var p : e.getValue().entrySet()) {
                inner.putLong(p.getKey(), p.getValue());
            }
            outer.put(e.getKey().toString(), inner);
        }
        nbt.put("players", outer);
        return nbt;
    }
}