package net.seep.odd.quest.client;

import net.minecraft.util.math.BlockPos;
import net.seep.odd.quest.QuestDefinition;
import net.seep.odd.quest.QuestNetworking;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public final class QuestClientState {
    public static final QuestClientState INSTANCE = new QuestClientState();

    public enum ActiveStage {
        NONE,
        TRAVEL,
        ACTIVE,
        RETURN,
        CLAIM
    }

    private int librarianEntityId = -1;
    private int questXp = 0;
    private int level = 0;
    private int levelFloorXp = 0;
    private int levelCeilXp = 1;

    private String activeQuestId = "";
    private int activeProgress = 0;
    private boolean readyToClaim = false;

    private ActiveStage activeStage = ActiveStage.NONE;
    @Nullable
    private BlockPos activeTarget = null;
    private String objectiveHint = "";
    private boolean playMusic = false;

    private String requestedLoreId = "";
    private boolean requiredBookRead = false;

    private final Set<Integer> trackedEntityIds = new HashSet<>();
    private final Set<Integer> calmedTrackedEntityIds = new HashSet<>();

    private final Set<String> claimedQuestIds = new HashSet<>();
    private final Map<String, QuestDefinition> definitions = new LinkedHashMap<>();

    private QuestClientState() {
    }

    public void apply(QuestNetworking.DecodedSync sync) {
        this.librarianEntityId = sync.librarianEntityId;
        this.questXp = sync.questXp;
        this.level = sync.level;
        this.levelFloorXp = sync.levelFloorXp;
        this.levelCeilXp = sync.levelCeilXp;

        this.activeQuestId = sync.hasActiveQuest ? sync.activeQuestId : "";
        this.activeProgress = sync.hasActiveQuest ? sync.activeProgress : 0;
        this.readyToClaim = sync.hasActiveQuest && sync.readyToClaim;

        this.claimedQuestIds.clear();
        this.claimedQuestIds.addAll(sync.claimedQuestIds);

        this.definitions.clear();
        for (QuestDefinition def : sync.quests) {
            this.definitions.put(def.id, def);
        }

        if (!sync.hasActiveQuest) {
            this.activeStage = ActiveStage.NONE;
            this.activeTarget = null;
            this.objectiveHint = "";
            this.playMusic = false;
            this.requestedLoreId = "";
            this.requiredBookRead = false;
            this.trackedEntityIds.clear();
            this.calmedTrackedEntityIds.clear();
            return;
        }

        this.activeStage = readStage(sync);
        this.activeTarget = readTarget(sync);
        this.objectiveHint = readString(sync, "objectiveHint", "");
        this.playMusic = readBoolean(sync, "playMusic", false);

        this.requestedLoreId = readString(sync, "requestedLoreId", "");
        this.requiredBookRead = readBoolean(sync, "requiredBookRead", false);

        this.trackedEntityIds.clear();
        this.trackedEntityIds.addAll(readIntSet(sync, "trackedEntityIds"));

        this.calmedTrackedEntityIds.clear();
        this.calmedTrackedEntityIds.addAll(readIntSet(sync, "calmedTrackedEntityIds"));

        if (!hasField(sync, "playMusic")) {
            this.playMusic = computeFallbackPlayMusic();
        }
    }

    public int librarianEntityId() {
        return librarianEntityId;
    }

    public int questXp() {
        return questXp;
    }

    public int level() {
        return level;
    }

    public int levelFloorXp() {
        return levelFloorXp;
    }

    public int levelCeilXp() {
        return levelCeilXp;
    }

    public boolean hasActiveQuest() {
        return !activeQuestId.isBlank();
    }

    public String activeQuestId() {
        return activeQuestId;
    }

    public int activeProgress() {
        return activeProgress;
    }

    public boolean readyToClaim() {
        return readyToClaim;
    }

    public Collection<QuestDefinition> quests() {
        return definitions.values();
    }

    public List<QuestDefinition> sortedQuests() {
        return definitions.values().stream().sorted(QuestDefinition.SORTER).collect(Collectors.toList());
    }

    @Nullable
    public QuestDefinition quest(String id) {
        return definitions.get(id);
    }

    @Nullable
    public QuestDefinition activeQuestDefinition() {
        return hasActiveQuest() ? definitions.get(activeQuestId) : null;
    }

    public boolean isClaimed(String id) {
        return claimedQuestIds.contains(id);
    }

    public boolean isUnlocked(QuestDefinition def) {
        return level >= def.unlockLevel;
    }

    public boolean isActive(String questId) {
        return hasActiveQuest() && activeQuestId.equals(questId);
    }

    public int progressFor(QuestDefinition def) {
        return isActive(def.id) ? activeProgress : 0;
    }

    public boolean canClaim(QuestDefinition def) {
        return isActive(def.id) && readyToClaim;
    }

    public ActiveStage activeStage() {
        return activeStage;
    }

    public boolean isTravelStage() {
        return activeStage == ActiveStage.TRAVEL;
    }

    public boolean isObjectiveStage() {
        return activeStage == ActiveStage.ACTIVE;
    }

    public boolean isReturnStage() {
        return activeStage == ActiveStage.RETURN;
    }

    public boolean isClaimStage() {
        return activeStage == ActiveStage.CLAIM || readyToClaim;
    }

    @Nullable
    public BlockPos activeTarget() {
        return activeTarget;
    }

    public boolean hasActiveTarget() {
        return activeTarget != null;
    }

    public String objectiveHint() {
        if (!objectiveHint.isBlank()) {
            return objectiveHint;
        }

        if (!hasActiveQuest()) {
            return "";
        }

        if (isClaimStage()) {
            return "Return to the librarian to claim your reward";
        }

        if (isTravelStage()) {
            return "Go to the marked location";
        }

        if (isReturnStage()) {
            return "Bring them back to the librarian";
        }

        return "Quest in progress";
    }

    public boolean playMusic() {
        return playMusic;
    }

    public String requestedLoreId() {
        return requestedLoreId;
    }

    public boolean requiredBookRead() {
        return requiredBookRead;
    }

    public Set<Integer> trackedEntityIds() {
        return Collections.unmodifiableSet(trackedEntityIds);
    }

    public Set<Integer> calmedTrackedEntityIds() {
        return Collections.unmodifiableSet(calmedTrackedEntityIds);
    }

    public boolean isTrackedEntity(int entityId) {
        return trackedEntityIds.contains(entityId);
    }

    public boolean isCalmedTrackedEntity(int entityId) {
        return calmedTrackedEntityIds.contains(entityId);
    }

    private boolean computeFallbackPlayMusic() {
        QuestDefinition def = activeQuestDefinition();
        if (def == null || readyToClaim) {
            return false;
        }

        if (def.objective == null || def.objective.type == null) {
            return false;
        }

        return switch (def.objective.type) {
            case KILL -> true;
            case CALM_RETURN, RACE_RETURN -> activeStage == ActiveStage.ACTIVE;
            case FIND_HIM, LORE_BOOK_QUIZ -> false;
            case SCARED_RASCAL -> activeStage == ActiveStage.ACTIVE;
            case SCARED_RASCAL_2 -> activeStage == ActiveStage.ACTIVE;
            default -> false;
        };
    }

    private static ActiveStage readStage(Object sync) {
        Object raw = readField(sync, "activeStage");
        if (raw == null) raw = readField(sync, "activePhase");

        if (raw == null) return ActiveStage.NONE;
        if (raw instanceof ActiveStage stage) return stage;

        String name = raw.toString().trim();
        if (name.isEmpty()) return ActiveStage.NONE;

        return switch (name.toUpperCase(Locale.ROOT)) {
            case "TRAVEL", "GO_TO_AREA" -> ActiveStage.TRAVEL;
            case "ACTIVE", "ROUNDUP", "WAIT_MOUNT", "COUNTDOWN", "RACING", "CHASE", "TALK", "BATTLE" -> ActiveStage.ACTIVE;
            case "RETURN", "ESCORT" -> ActiveStage.RETURN;
            case "CLAIM" -> ActiveStage.CLAIM;
            default -> ActiveStage.NONE;
        };
    }

    @Nullable
    private static BlockPos readTarget(Object sync) {
        Object direct = readField(sync, "activeTarget");
        if (direct instanceof BlockPos pos) return pos;

        boolean hasTarget = readBoolean(sync, "hasTargetLocation", false) || readBoolean(sync, "hasTarget", false);
        if (!hasTarget) {
            Integer xLegacy = readIntObject(sync, "targetX");
            Integer yLegacy = readIntObject(sync, "targetY");
            Integer zLegacy = readIntObject(sync, "targetZ");
            if (xLegacy != null && yLegacy != null && zLegacy != null) {
                return new BlockPos(xLegacy, yLegacy, zLegacy);
            }
            return null;
        }

        Integer x = readIntObject(sync, "targetX");
        Integer y = readIntObject(sync, "targetY");
        Integer z = readIntObject(sync, "targetZ");
        if (x != null && y != null && z != null) {
            return new BlockPos(x, y, z);
        }

        Double dx = readDoubleObject(sync, "targetX");
        Double dy = readDoubleObject(sync, "targetY");
        Double dz = readDoubleObject(sync, "targetZ");
        if (dx != null && dy != null && dz != null) {
            return BlockPos.ofFloored(dx, dy, dz);
        }

        return null;
    }

    private static String readString(Object instance, String fieldName, String fallback) {
        Object value = readField(instance, fieldName);
        return value instanceof String s ? s : fallback;
    }

    private static boolean readBoolean(Object instance, String fieldName, boolean fallback) {
        Object value = readField(instance, fieldName);
        return value instanceof Boolean b ? b : fallback;
    }

    @Nullable
    private static Integer readIntObject(Object instance, String fieldName) {
        Object value = readField(instance, fieldName);
        if (value instanceof Integer i) return i;
        if (value instanceof Number n) return n.intValue();
        return null;
    }

    @Nullable
    private static Double readDoubleObject(Object instance, String fieldName) {
        Object value = readField(instance, fieldName);
        if (value instanceof Double d) return d;
        if (value instanceof Float f) return f.doubleValue();
        if (value instanceof Integer i) return i.doubleValue();
        if (value instanceof Long l) return l.doubleValue();
        if (value instanceof Number n) return n.doubleValue();
        return null;
    }

    private static Set<Integer> readIntSet(Object instance, String fieldName) {
        Object value = readField(instance, fieldName);
        if (!(value instanceof Collection<?> collection)) return Collections.emptySet();

        Set<Integer> out = new HashSet<>();
        for (Object element : collection) {
            if (element instanceof Integer i) out.add(i);
            else if (element instanceof Number n) out.add(n.intValue());
        }
        return out;
    }

    private static boolean hasField(Object instance, String fieldName) {
        try {
            instance.getClass().getDeclaredField(fieldName);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Nullable
    private static Object readField(Object instance, String fieldName) {
        try {
            Field field = instance.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(instance);
        } catch (Throwable ignored) {
            return null;
        }
    }
}