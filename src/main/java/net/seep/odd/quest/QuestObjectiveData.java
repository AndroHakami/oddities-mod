package net.seep.odd.quest;

import com.google.gson.annotations.SerializedName;

public final class QuestObjectiveData {
    public enum Type {
        @SerializedName("kill") KILL,
        @SerializedName("calm_return") CALM_RETURN,
        @SerializedName("race_return") RACE_RETURN,
        @SerializedName("lore_book_quiz") LORE_BOOK_QUIZ,
        @SerializedName("find_him") FIND_HIM,
        @SerializedName("scared_rascal") SCARED_RASCAL,
        @SerializedName("scared_rascal_2") SCARED_RASCAL_2
    }

    public Type type = Type.KILL;
    public String entity = "minecraft:pig";
    public int count = 1;
}
