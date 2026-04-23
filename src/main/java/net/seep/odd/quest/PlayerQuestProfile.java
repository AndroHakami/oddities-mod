package net.seep.odd.quest;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.HashSet;
import java.util.Set;

public final class PlayerQuestProfile {
    public int questXp = 0;
    public final Set<String> claimedQuestIds = new HashSet<>();
    public ActiveQuestState activeQuest;

    public NbtCompound writeNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("QuestXp", questXp);

        NbtList claimed = new NbtList();
        for (String id : claimedQuestIds) {
            claimed.add(NbtString.of(id));
        }
        nbt.put("Claimed", claimed);

        if (activeQuest != null) {
            nbt.put("ActiveQuest", activeQuest.writeNbt());
        }
        return nbt;
    }

    public static PlayerQuestProfile fromNbt(NbtCompound nbt) {
        PlayerQuestProfile profile = new PlayerQuestProfile();
        profile.questXp = nbt.getInt("QuestXp");

        NbtList claimed = nbt.getList("Claimed", NbtElement.STRING_TYPE);
        for (int i = 0; i < claimed.size(); i++) {
            profile.claimedQuestIds.add(claimed.getString(i));
        }

        if (nbt.contains("ActiveQuest", NbtElement.COMPOUND_TYPE)) {
            profile.activeQuest = ActiveQuestState.fromNbt(nbt.getCompound("ActiveQuest"));
        }
        return profile;
    }
}
