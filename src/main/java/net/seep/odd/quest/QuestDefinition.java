package net.seep.odd.quest;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class QuestDefinition {
    public static final Comparator<QuestDefinition> SORTER =
            Comparator.comparingInt((QuestDefinition q) -> q.unlockLevel)
                    .thenComparing(q -> q.title, String.CASE_INSENSITIVE_ORDER);

    public String id = "";
    public String title = "Untitled Quest";
    public String description = "";
    public String icon = "odd:textures/gui/quest_icons/default.png";
    public int unlockLevel = 0;
    public int questXp = 1;
    public String music = "";
    public QuestObjectiveData objective = new QuestObjectiveData();
    public List<QuestRewardData> rewards = new ArrayList<>();

    public Identifier iconId() {
        return new Identifier(icon);
    }

    public Identifier musicId() {
        return music == null || music.isBlank() ? null : new Identifier(music);
    }

    public int goal() {
        return Math.max(1, objective.count);
    }

    public static void write(PacketByteBuf buf, QuestDefinition def) {
        buf.writeString(def.id);
        buf.writeString(def.title);
        buf.writeString(def.description);
        buf.writeString(def.icon);
        buf.writeInt(def.unlockLevel);
        buf.writeInt(def.questXp);
        buf.writeString(def.music == null ? "" : def.music);

        buf.writeEnumConstant(def.objective.type);
        buf.writeString(def.objective.entity);
        buf.writeInt(def.objective.count);

        buf.writeInt(def.rewards.size());
        for (QuestRewardData reward : def.rewards) {
            buf.writeEnumConstant(reward.type);
            buf.writeString(reward.item);
            buf.writeInt(reward.count);
        }
    }

    public static QuestDefinition read(PacketByteBuf buf) {
        QuestDefinition def = new QuestDefinition();
        def.id = buf.readString();
        def.title = buf.readString();
        def.description = buf.readString();
        def.icon = buf.readString();
        def.unlockLevel = buf.readInt();
        def.questXp = buf.readInt();
        def.music = buf.readString();

        def.objective = new QuestObjectiveData();
        def.objective.type = buf.readEnumConstant(QuestObjectiveData.Type.class);
        def.objective.entity = buf.readString();
        def.objective.count = buf.readInt();

        int rewardCount = buf.readInt();
        def.rewards = new ArrayList<>(rewardCount);
        for (int i = 0; i < rewardCount; i++) {
            QuestRewardData reward = new QuestRewardData();
            reward.type = buf.readEnumConstant(QuestRewardData.Type.class);
            reward.item = buf.readString();
            reward.count = buf.readInt();
            def.rewards.add(reward);
        }

        return def;
    }
}
