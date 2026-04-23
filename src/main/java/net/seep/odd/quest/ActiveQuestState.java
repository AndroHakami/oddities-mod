package net.seep.odd.quest;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ActiveQuestState {
    public static final int STAGE_ACTIVE = 0;
    public static final int STAGE_GO_TO_AREA = 1;
    public static final int STAGE_ROUNDUP = 2;
    public static final int STAGE_WAIT_MOUNT = 3;
    public static final int STAGE_COUNTDOWN = 4;
    public static final int STAGE_RACING = 5;
    public static final int STAGE_FIND_HIM_FIELD = 6;
    public static final int STAGE_FIND_HIM_MAZE = 7;
    public static final int STAGE_SCARED_RASCAL_TALK_START = 8;
    public static final int STAGE_SCARED_RASCAL_CHASE = 9;
    public static final int STAGE_SCARED_RASCAL_TALK_END = 10;
    public static final int STAGE_SCARED_RASCAL_ESCORT = 11;
    public static final int STAGE_SCARED_RASCAL_2_TALK_START = 12;
    public static final int STAGE_SCARED_RASCAL_2_GO_TO_AMBUSH = 13;
    public static final int STAGE_SCARED_RASCAL_2_TALK_AMBUSH = 14;
    public static final int STAGE_SCARED_RASCAL_2_REVEAL = 15;
    public static final int STAGE_SCARED_RASCAL_2_FIGHT = 16;
    public static final int STAGE_SCARED_RASCAL_2_TALK_END = 17;
    public static final int STAGE_SCARED_RASCAL_2_ESCORT = 18;

    public String questId;
    public int progress;
    public boolean readyToClaim;
    public int stage = STAGE_ACTIVE;

    public boolean hasTarget;
    public int targetX;
    public int targetY;
    public int targetZ;

    public int countdownTicks;
    public int mazeCatchGraceTicks;

    public UUID playerRideId;
    public UUID rivalRideId;
    public UUID rivalRascalId;
    public UUID scaredRascalId;
    public UUID rakeId;
    public UUID scaredRascalFightId;
    public UUID roboRascalId;
    public final List<UUID> spawnedEntityIds = new ArrayList<>();

    // lore quiz state
    public String requestedLoreId = "";
    public boolean requiredBookRead = false;

    // find him return / maze state
    public boolean hasReturnPos;
    public int returnX;
    public int returnY;
    public int returnZ;
    public float returnYaw;
    public float returnPitch;

    public ActiveQuestState() {}

    public ActiveQuestState(String questId) {
        this.questId = questId;
    }

    public static ActiveQuestState forAreaQuest(String questId, int x, int y, int z) {
        ActiveQuestState state = new ActiveQuestState(questId);
        state.stage = STAGE_GO_TO_AREA;
        state.hasTarget = true;
        state.targetX = x;
        state.targetY = y;
        state.targetZ = z;
        return state;
    }

    public static ActiveQuestState forLoreQuiz(String questId, String requestedLoreId) {
        ActiveQuestState state = new ActiveQuestState(questId);
        state.requestedLoreId = requestedLoreId == null ? "" : requestedLoreId;
        state.requiredBookRead = false;
        return state;
    }

    public NbtCompound writeNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("QuestId", this.questId == null ? "" : this.questId);
        nbt.putInt("Progress", this.progress);
        nbt.putBoolean("ReadyToClaim", this.readyToClaim);
        nbt.putInt("Stage", this.stage);
        nbt.putBoolean("HasTarget", this.hasTarget);
        nbt.putInt("TargetX", this.targetX);
        nbt.putInt("TargetY", this.targetY);
        nbt.putInt("TargetZ", this.targetZ);
        nbt.putInt("CountdownTicks", this.countdownTicks);
        nbt.putInt("MazeCatchGraceTicks", this.mazeCatchGraceTicks);
        if (this.playerRideId != null) nbt.putUuid("PlayerRide", this.playerRideId);
        if (this.rivalRideId != null) nbt.putUuid("RivalRide", this.rivalRideId);
        if (this.rivalRascalId != null) nbt.putUuid("RivalRascal", this.rivalRascalId);
        if (this.scaredRascalId != null) nbt.putUuid("ScaredRascal", this.scaredRascalId);
        if (this.rakeId != null) nbt.putUuid("Rake", this.rakeId);
        if (this.scaredRascalFightId != null) nbt.putUuid("ScaredRascalFight", this.scaredRascalFightId);
        if (this.roboRascalId != null) nbt.putUuid("RoboRascal", this.roboRascalId);
        nbt.putString("RequestedLoreId", this.requestedLoreId == null ? "" : this.requestedLoreId);
        nbt.putBoolean("RequiredBookRead", this.requiredBookRead);
        nbt.putBoolean("HasReturnPos", this.hasReturnPos);
        nbt.putInt("ReturnX", this.returnX);
        nbt.putInt("ReturnY", this.returnY);
        nbt.putInt("ReturnZ", this.returnZ);
        nbt.putFloat("ReturnYaw", this.returnYaw);
        nbt.putFloat("ReturnPitch", this.returnPitch);

        NbtList list = new NbtList();
        for (UUID uuid : this.spawnedEntityIds) {
            NbtCompound entry = new NbtCompound();
            entry.putUuid("Id", uuid);
            list.add(entry);
        }
        nbt.put("Spawned", list);
        return nbt;
    }

    public static ActiveQuestState fromNbt(NbtCompound nbt) {
        ActiveQuestState state = new ActiveQuestState();
        state.questId = nbt.getString("QuestId");
        state.progress = nbt.getInt("Progress");
        state.readyToClaim = nbt.getBoolean("ReadyToClaim");
        state.stage = nbt.contains("Stage") ? nbt.getInt("Stage") : STAGE_ACTIVE;
        state.hasTarget = nbt.getBoolean("HasTarget");
        state.targetX = nbt.getInt("TargetX");
        state.targetY = nbt.getInt("TargetY");
        state.targetZ = nbt.getInt("TargetZ");
        state.countdownTicks = nbt.getInt("CountdownTicks");
        state.mazeCatchGraceTicks = nbt.contains("MazeCatchGraceTicks") ? nbt.getInt("MazeCatchGraceTicks") : 0;
        state.playerRideId = nbt.containsUuid("PlayerRide") ? nbt.getUuid("PlayerRide") : null;
        state.rivalRideId = nbt.containsUuid("RivalRide") ? nbt.getUuid("RivalRide") : null;
        state.rivalRascalId = nbt.containsUuid("RivalRascal") ? nbt.getUuid("RivalRascal") : null;
        state.scaredRascalId = nbt.containsUuid("ScaredRascal") ? nbt.getUuid("ScaredRascal") : null;
        state.rakeId = nbt.containsUuid("Rake") ? nbt.getUuid("Rake") : null;
        state.scaredRascalFightId = nbt.containsUuid("ScaredRascalFight") ? nbt.getUuid("ScaredRascalFight") : null;
        state.roboRascalId = nbt.containsUuid("RoboRascal") ? nbt.getUuid("RoboRascal") : null;
        state.requestedLoreId = nbt.contains("RequestedLoreId") ? nbt.getString("RequestedLoreId") : "";
        state.requiredBookRead = nbt.getBoolean("RequiredBookRead");
        state.hasReturnPos = nbt.getBoolean("HasReturnPos");
        state.returnX = nbt.getInt("ReturnX");
        state.returnY = nbt.getInt("ReturnY");
        state.returnZ = nbt.getInt("ReturnZ");
        state.returnYaw = nbt.contains("ReturnYaw") ? nbt.getFloat("ReturnYaw") : 0.0F;
        state.returnPitch = nbt.contains("ReturnPitch") ? nbt.getFloat("ReturnPitch") : 0.0F;

        if (nbt.contains("Spawned")) {
            NbtList list = nbt.getList("Spawned", 10);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound entry = list.getCompound(i);
                if (entry.containsUuid("Id")) {
                    state.spawnedEntityIds.add(entry.getUuid("Id"));
                }
            }
        }
        return state;
    }
}
