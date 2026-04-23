
package net.seep.odd.quest;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class QuestPersistentState extends PersistentState {
    private static final String SAVE_ID = "odd_player_quests";

    private final Map<UUID, PlayerQuestProfile> profiles = new HashMap<>();

    public static QuestPersistentState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(
                QuestPersistentState::fromNbt,
                QuestPersistentState::new,
                SAVE_ID
        );
    }

    public PlayerQuestProfile getProfile(UUID uuid) {
        return profiles.computeIfAbsent(uuid, id -> new PlayerQuestProfile());
    }

    public static QuestPersistentState fromNbt(NbtCompound nbt) {
        QuestPersistentState state = new QuestPersistentState();
        NbtCompound players = nbt.getCompound("Players");
        for (String key : players.getKeys()) {
            try {
                state.profiles.put(UUID.fromString(key), PlayerQuestProfile.fromNbt(players.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound players = new NbtCompound();
        for (Map.Entry<UUID, PlayerQuestProfile> entry : profiles.entrySet()) {
            players.put(entry.getKey().toString(), entry.getValue().writeNbt());
        }
        nbt.put("Players", players);
        return nbt;
    }
}
