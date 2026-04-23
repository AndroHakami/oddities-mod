package net.seep.odd.quest;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.entity.him.HimEntity;
import net.seep.odd.entity.rascal.RascalEntity;
import net.seep.odd.lore.AtheneumLoreBooks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class QuestNetworking {
    public static final Identifier S2C_SYNC = new Identifier(Oddities.MOD_ID, "quest_sync");
    public static final Identifier S2C_OPEN_LORE_QUIZ = new Identifier(Oddities.MOD_ID, "open_lore_quiz");
    public static final Identifier S2C_OPEN_SCARED_RASCAL_DIALOG = new Identifier(Oddities.MOD_ID, "open_scared_rascal_dialog");
    public static final Identifier S2C_FORCE_QUEST_MUSIC = new Identifier(Oddities.MOD_ID, "force_quest_music");
    public static final Identifier S2C_STOP_QUEST_MUSIC = new Identifier(Oddities.MOD_ID, "stop_quest_music");
    public static final Identifier C2S_ACCEPT = new Identifier(Oddities.MOD_ID, "quest_accept");
    public static final Identifier C2S_ABANDON = new Identifier(Oddities.MOD_ID, "quest_abandon");
    public static final Identifier C2S_CLAIM = new Identifier(Oddities.MOD_ID, "quest_claim");
    public static final Identifier C2S_SUBMIT_LORE_QUIZ = new Identifier(Oddities.MOD_ID, "submit_lore_quiz");
    public static final Identifier C2S_CONTINUE_SCARED_RASCAL_DIALOG = new Identifier(Oddities.MOD_ID, "continue_scared_rascal_dialog");

    private QuestNetworking() {}

    public static void sendOpenLoreQuiz(ServerPlayerEntity player, int librarianEntityId, ActiveQuestState active) {
        AtheneumLoreBooks.Volume volume = AtheneumLoreBooks.get(active.requestedLoreId);
        if (volume == null) return;

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(librarianEntityId);
        buf.writeString(active.questId);
        buf.writeString(volume.id());
        buf.writeString(volume.title());
        buf.writeString(volume.question());
        buf.writeInt(volume.answers().length);
        for (String answer : volume.answers()) {
            buf.writeString(answer);
        }
        ServerPlayNetworking.send(player, S2C_OPEN_LORE_QUIZ, buf);
    }

    public static void sendOpenScaredRascalDialog(ServerPlayerEntity player, int rascalEntityId, String line) {
        sendOpenScaredRascalDialog(player, rascalEntityId, "Scared Rascal", line, "continue");
    }

    public static void sendOpenScaredRascalDialog(ServerPlayerEntity player, int rascalEntityId, String title, String line, String buttonText) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(rascalEntityId);
        buf.writeString(title == null ? "" : title);
        buf.writeString(line == null ? "" : line);
        buf.writeString(buttonText == null || buttonText.isBlank() ? "continue" : buttonText);
        ServerPlayNetworking.send(player, S2C_OPEN_SCARED_RASCAL_DIALOG, buf);
    }

    public static void sendForceQuestMusic(ServerPlayerEntity player, String musicId) {
        if (musicId == null || musicId.isBlank()) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(musicId);
        ServerPlayNetworking.send(player, S2C_FORCE_QUEST_MUSIC, buf);
    }

    public static void sendStopQuestMusic(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, S2C_STOP_QUEST_MUSIC, PacketByteBufs.empty());
    }

    public static void sendSync(ServerPlayerEntity player, boolean openScreen, int librarianEntityId) {
        PlayerQuestProfile profile = QuestManager.profile(player);
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeBoolean(openScreen);
        buf.writeInt(librarianEntityId);

        QuestLevelConfig levels = QuestLevelRegistry.get();
        buf.writeInt(profile.questXp);
        buf.writeInt(levels.currentLevel(profile.questXp));
        buf.writeInt(levels.currentFloorXp(profile.questXp));
        buf.writeInt(levels.nextCeilXp(profile.questXp));

        boolean hasActive = profile.activeQuest != null;
        buf.writeBoolean(hasActive);
        if (hasActive) {
            ActiveQuestState active = profile.activeQuest;
            QuestDefinition def = QuestRegistry.get(active.questId);

            buf.writeString(active.questId);
            buf.writeInt(active.progress);
            buf.writeBoolean(active.readyToClaim);

            buf.writeString(encodeStage(active));
            buf.writeString(buildObjectiveHint(player, def, active));
            buf.writeBoolean(shouldPlayMusic(def, active));

            buf.writeBoolean(active.hasTarget);
            if (active.hasTarget) {
                buf.writeInt(active.targetX);
                buf.writeInt(active.targetY);
                buf.writeInt(active.targetZ);
            }

            buf.writeString(active.requestedLoreId == null ? "" : active.requestedLoreId);
            buf.writeBoolean(active.requiredBookRead);

            List<Integer> tracked = new ArrayList<>();
            List<Integer> calmed = new ArrayList<>();
            collectTrackedEntities(player, active, tracked, calmed);

            buf.writeInt(tracked.size());
            for (int id : tracked) buf.writeInt(id);

            buf.writeInt(calmed.size());
            for (int id : calmed) buf.writeInt(id);
        }

        buf.writeInt(profile.claimedQuestIds.size());
        for (String questId : profile.claimedQuestIds) {
            buf.writeString(questId);
        }

        List<QuestDefinition> quests = QuestRegistry.sortedList();
        buf.writeInt(quests.size());
        for (QuestDefinition def : quests) {
            QuestDefinition.write(buf, def);
        }

        ServerPlayNetworking.send(player, S2C_SYNC, buf);
    }

    private static boolean shouldPlayMusic(QuestDefinition def, ActiveQuestState active) {
        if (def == null || active == null || active.readyToClaim) return false;

        return switch (def.objective.type) {
            case KILL -> true;
            case CALM_RETURN -> active.stage != ActiveQuestState.STAGE_GO_TO_AREA;
            case RACE_RETURN -> active.stage == ActiveQuestState.STAGE_RACING;
            case FIND_HIM -> active.stage == ActiveQuestState.STAGE_FIND_HIM_MAZE;
            case LORE_BOOK_QUIZ -> false;
            case SCARED_RASCAL -> active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_CHASE;
            case SCARED_RASCAL_2 -> active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_2_FIGHT;
            default -> false;
        };
    }

    private static void collectTrackedEntities(ServerPlayerEntity player, ActiveQuestState active, List<Integer> tracked, List<Integer> calmed) {
        if (active == null || active.spawnedEntityIds.isEmpty()) return;
        for (UUID uuid : active.spawnedEntityIds) {
            var entity = QuestManager.findEntity(player, uuid);
            if (entity instanceof RascalEntity rascal && !rascal.isRemoved()) {
                tracked.add(rascal.getId());
                if (rascal.isCalmed()) calmed.add(rascal.getId());
            } else if (entity instanceof HimEntity him && !him.isRemoved()) {
                tracked.add(him.getId());
            } else if (entity instanceof net.seep.odd.entity.scared_rascal.ScaredRascalEntity scaredRascal && !scaredRascal.isRemoved()) {
                tracked.add(scaredRascal.getId());
            } else if (entity instanceof net.seep.odd.entity.rake.RakeEntity rake && !rake.isRemoved()) {
                tracked.add(rake.getId());
            } else if (entity instanceof net.seep.odd.entity.scared_rascal_fight.ScaredRascalFightEntity scaredRascalFight && !scaredRascalFight.isRemoved()) {
                tracked.add(scaredRascalFight.getId());
            } else if (entity instanceof net.seep.odd.entity.robo_rascal.RoboRascalEntity roboRascal && !roboRascal.isRemoved()) {
                tracked.add(roboRascal.getId());
            }
        }
    }

    private static String encodeStage(ActiveQuestState active) {
        if (active == null) return "NONE";
        if (active.readyToClaim) return "CLAIM";
        return switch (active.stage) {
            case ActiveQuestState.STAGE_GO_TO_AREA, ActiveQuestState.STAGE_SCARED_RASCAL_2_GO_TO_AMBUSH -> "TRAVEL";
            case ActiveQuestState.STAGE_SCARED_RASCAL_ESCORT, ActiveQuestState.STAGE_SCARED_RASCAL_2_ESCORT -> "ESCORT";
            case ActiveQuestState.STAGE_SCARED_RASCAL_CHASE -> "CHASE";
            case ActiveQuestState.STAGE_SCARED_RASCAL_2_FIGHT -> "BATTLE";
            case ActiveQuestState.STAGE_SCARED_RASCAL_TALK_START, ActiveQuestState.STAGE_SCARED_RASCAL_TALK_END,
                 ActiveQuestState.STAGE_SCARED_RASCAL_2_TALK_START, ActiveQuestState.STAGE_SCARED_RASCAL_2_TALK_AMBUSH,
                 ActiveQuestState.STAGE_SCARED_RASCAL_2_REVEAL, ActiveQuestState.STAGE_SCARED_RASCAL_2_TALK_END -> "TALK";
            default -> "ACTIVE";
        };
    }

    private static String buildObjectiveHint(ServerPlayerEntity player, QuestDefinition def, ActiveQuestState active) {
        if (def == null || active == null) return "";
        if (active.readyToClaim) return "Return to the librarian to claim your reward";

        return switch (def.objective.type) {
            case CALM_RETURN -> active.stage == ActiveQuestState.STAGE_GO_TO_AREA
                    ? "Go to the marked location"
                    : "Calm the rascals and bring them back to the librarian";
            case RACE_RETURN -> switch (active.stage) {
                case ActiveQuestState.STAGE_GO_TO_AREA -> "Go to the marked location";
                case ActiveQuestState.STAGE_WAIT_MOUNT -> "Mount the waiting star ride";
                case ActiveQuestState.STAGE_COUNTDOWN -> "Get ready...";
                case ActiveQuestState.STAGE_RACING -> "Beat the rascal back to the librarian";
                default -> "Race back to the librarian";
            };
            case LORE_BOOK_QUIZ -> net.seep.odd.quest.types.BookQuizQuestLogic.buildHint(player, active);
            case FIND_HIM -> active.stage == ActiveQuestState.STAGE_FIND_HIM_MAZE
                    ? (active.hasTarget
                        ? "Follow the glow before time runs out (" + Math.max(1, (active.countdownTicks + 19) / 20) + "s)"
                        : "Catch Him before he escapes (" + Math.max(1, (active.countdownTicks + 19) / 20) + "s)")
                    : "Track Him down and get close";
            case SCARED_RASCAL -> switch (active.stage) {
                case ActiveQuestState.STAGE_GO_TO_AREA -> "Go to the marked location";
                case ActiveQuestState.STAGE_SCARED_RASCAL_TALK_START -> "Talk to the scared rascal";
                case ActiveQuestState.STAGE_SCARED_RASCAL_CHASE -> "Survive for " + Math.max(1, (active.countdownTicks + 19) / 20) + "s";
                case ActiveQuestState.STAGE_SCARED_RASCAL_TALK_END -> "Talk to the scared rascal";
                case ActiveQuestState.STAGE_SCARED_RASCAL_ESCORT -> "Escort the scared rascal back to the librarian";
                default -> "Help the scared rascal";
            };
            case SCARED_RASCAL_2 -> switch (active.stage) {
                case ActiveQuestState.STAGE_SCARED_RASCAL_2_TALK_START -> "Talk to the scared rascal";
                case ActiveQuestState.STAGE_SCARED_RASCAL_2_GO_TO_AMBUSH -> "Go to the marked location with the scared rascal";
                case ActiveQuestState.STAGE_SCARED_RASCAL_2_TALK_AMBUSH -> "Talk to the scared rascal";
                case ActiveQuestState.STAGE_SCARED_RASCAL_2_REVEAL -> active.countdownTicks > 40 ? "Wait..." : "Something is coming...";
                case ActiveQuestState.STAGE_SCARED_RASCAL_2_FIGHT -> "Defeat the Robo Rascal before it kills the scared rascal";
                case ActiveQuestState.STAGE_SCARED_RASCAL_2_TALK_END -> "Talk to the scared rascal";
                case ActiveQuestState.STAGE_SCARED_RASCAL_2_ESCORT -> "Escort the scared rascal back to the librarian";
                default -> "Help the scared rascal";
            };
            default -> "Quest in progress";
        };
    }

    public static DecodedSync decodeClientSync(PacketByteBuf buf) {
        DecodedSync sync = new DecodedSync();
        sync.openScreen = buf.readBoolean();
        sync.librarianEntityId = buf.readInt();
        sync.questXp = buf.readInt();
        sync.level = buf.readInt();
        sync.levelFloorXp = buf.readInt();
        sync.levelCeilXp = buf.readInt();

        sync.hasActiveQuest = buf.readBoolean();
        if (sync.hasActiveQuest) {
            sync.activeQuestId = buf.readString();
            sync.activeProgress = buf.readInt();
            sync.readyToClaim = buf.readBoolean();

            sync.activeStage = buf.readString();
            sync.objectiveHint = buf.readString();
            sync.playMusic = buf.readBoolean();

            sync.hasTargetLocation = buf.readBoolean();
            if (sync.hasTargetLocation) {
                sync.targetX = buf.readInt();
                sync.targetY = buf.readInt();
                sync.targetZ = buf.readInt();
            }

            sync.requestedLoreId = buf.readString();
            sync.requiredBookRead = buf.readBoolean();

            int trackedCount = buf.readInt();
            sync.trackedEntityIds = new ArrayList<>(trackedCount);
            for (int i = 0; i < trackedCount; i++) sync.trackedEntityIds.add(buf.readInt());

            int calmedCount = buf.readInt();
            sync.calmedTrackedEntityIds = new ArrayList<>(calmedCount);
            for (int i = 0; i < calmedCount; i++) sync.calmedTrackedEntityIds.add(buf.readInt());
        }

        int claimedCount = buf.readInt();
        sync.claimedQuestIds = new HashSet<>();
        for (int i = 0; i < claimedCount; i++) sync.claimedQuestIds.add(buf.readString());

        int questCount = buf.readInt();
        sync.quests = new ArrayList<>(questCount);
        for (int i = 0; i < questCount; i++) sync.quests.add(QuestDefinition.read(buf));
        return sync;
    }

    public static final class DecodedSync {
        public boolean openScreen;
        public int librarianEntityId;
        public int questXp;
        public int level;
        public int levelFloorXp;
        public int levelCeilXp;

        public boolean hasActiveQuest;
        public String activeQuestId = "";
        public int activeProgress;
        public boolean readyToClaim;

        public String activeStage = "NONE";
        public String objectiveHint = "";
        public boolean playMusic;

        public boolean hasTargetLocation;
        public int targetX;
        public int targetY;
        public int targetZ;

        public String requestedLoreId = "";
        public boolean requiredBookRead;

        public List<Integer> trackedEntityIds = new ArrayList<>();
        public List<Integer> calmedTrackedEntityIds = new ArrayList<>();
        public Set<String> claimedQuestIds = new HashSet<>();
        public List<QuestDefinition> quests = new ArrayList<>();
    }
}
