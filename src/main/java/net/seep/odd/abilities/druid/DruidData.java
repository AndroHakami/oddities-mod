package net.seep.odd.abilities.druid;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DruidData extends PersistentState {
    private static final String KEY = "odd_druid_data";

    private static final class PlayerEntry {
        String currentFormKey = DruidForm.HUMAN.key();
        long deathCooldownUntil = 0L;
        float storedHumanHealth = 20.0f;
    }

    private final Map<UUID, PlayerEntry> players = new HashMap<>();

    public static DruidData get(ServerWorld anyWorld) {
        MinecraftServer server = anyWorld.getServer();
        ServerWorld overworld = (server != null) ? server.getOverworld() : anyWorld;
        return overworld.getPersistentStateManager().getOrCreate(DruidData::fromNbt, DruidData::new, KEY);
    }

    private PlayerEntry entry(UUID id) { return players.computeIfAbsent(id, u -> new PlayerEntry()); }

    public DruidForm getCurrentForm(UUID playerId) { return DruidForm.byKey(entry(playerId).currentFormKey); }
    public void setCurrentForm(UUID playerId, DruidForm form) { entry(playerId).currentFormKey = form.key(); markDirty(); }

    public long getDeathCooldownUntil(UUID playerId) { return entry(playerId).deathCooldownUntil; }
    public void setDeathCooldownUntil(UUID playerId, long worldTimeTick) { entry(playerId).deathCooldownUntil = worldTimeTick; markDirty(); }

    public float getStoredHumanHealth(UUID playerId) { return entry(playerId).storedHumanHealth; }
    public void setStoredHumanHealth(UUID playerId, float hp) { entry(playerId).storedHumanHealth = hp; markDirty(); }

    public static DruidData fromNbt(NbtCompound nbt) {
        DruidData data = new DruidData();
        NbtList list = nbt.getList("players", NbtElement.COMPOUND_TYPE);

        for (int i = 0; i < list.size(); i++) {
            NbtCompound pe = list.getCompound(i);
            String uuidStr = pe.getString("uuid");
            if (uuidStr == null || uuidStr.isEmpty()) continue;

            UUID uuid;
            try { uuid = UUID.fromString(uuidStr); }
            catch (Exception ignored) { continue; }

            PlayerEntry e = new PlayerEntry();
            e.currentFormKey = pe.getString("cur");
            e.deathCooldownUntil = pe.getLong("cd");
            e.storedHumanHealth = pe.contains("human_hp") ? pe.getFloat("human_hp") : 20.0f;

            data.players.put(uuid, e);
        }
        return data;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();

        for (Map.Entry<UUID, PlayerEntry> en : players.entrySet()) {
            NbtCompound pe = new NbtCompound();
            PlayerEntry e = en.getValue();

            pe.putString("uuid", en.getKey().toString());
            pe.putString("cur", e.currentFormKey);
            pe.putLong("cd", e.deathCooldownUntil);
            pe.putFloat("human_hp", e.storedHumanHealth);

            list.add(pe);
        }

        nbt.put("players", list);
        return nbt;
    }
}
