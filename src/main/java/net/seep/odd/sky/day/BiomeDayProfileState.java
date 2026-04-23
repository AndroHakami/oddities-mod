package net.seep.odd.sky.day;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class BiomeDayProfileState extends PersistentState {
    public static final String ID = "odd_biome_day_profiles";

    private final Map<Identifier, BiomeDayProfile> profiles = new HashMap<>();

    public static BiomeDayProfileState get(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        if (overworld == null) {
            return new BiomeDayProfileState();
        }

        PersistentStateManager manager = overworld.getPersistentStateManager();
        return manager.getOrCreate(BiomeDayProfileState::fromNbt, BiomeDayProfileState::new, ID);
    }

    public Map<Identifier, BiomeDayProfile> getProfiles() {
        return Collections.unmodifiableMap(profiles);
    }

    public BiomeDayProfile get(Identifier biomeId) {
        return profiles.get(biomeId);
    }

    public void put(Identifier biomeId, BiomeDayProfile profile) {
        profiles.put(biomeId, profile);
        markDirty();
    }

    public boolean remove(Identifier biomeId) {
        BiomeDayProfile removed = profiles.remove(biomeId);
        if (removed != null) {
            markDirty();
            return true;
        }
        return false;
    }

    public static BiomeDayProfileState fromNbt(NbtCompound nbt) {
        BiomeDayProfileState state = new BiomeDayProfileState();

        NbtList list = nbt.getList("Profiles", 10);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i);

            Identifier biomeId = new Identifier(entry.getString("Biome"));
            int sky = entry.getInt("Sky");
            int fog = entry.getInt("Fog");
            int horizon = entry.contains("Horizon") ? entry.getInt("Horizon") : sky;
            int cloud = entry.contains("Cloud") ? entry.getInt("Cloud") : fog;

            state.profiles.put(biomeId, new BiomeDayProfile(sky, fog, horizon, cloud));
        }

        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();

        for (Map.Entry<Identifier, BiomeDayProfile> entry : profiles.entrySet()) {
            NbtCompound tag = new NbtCompound();
            tag.putString("Biome", entry.getKey().toString());
            tag.putInt("Sky", entry.getValue().skyColor());
            tag.putInt("Fog", entry.getValue().fogColor());
            tag.putInt("Horizon", entry.getValue().horizonColor());
            tag.putInt("Cloud", entry.getValue().cloudColor());
            list.add(tag);
        }

        nbt.put("Profiles", list);
        return nbt;
    }
}