package net.seep.odd.worldgen.structure;

import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class WitchlogRoomTable {
    /**
     * 0 means no socket is allowed to stay empty.
     */
    public static final int EMPTY_SOCKET_WEIGHT = 0;

    private final List<RoomDefinition> rooms;

    private WitchlogRoomTable(List<RoomDefinition> rooms) {
        this.rooms = List.copyOf(rooms);
    }

    /**
     * Uses the user's current hand-fixed anchors/rotations,
     * but allows repeats so every room socket can be populated.
     */
    public static WitchlogRoomTable createDefault() {
        List<RoomDefinition> rooms = new ArrayList<>();

        rooms.add(new RoomDefinition(
                "experiment_room",
                new Identifier("odd", "witchlog/experiment_room"),
                SocketType.MEDIUM,
                10,
                1,
                -1,
                true,
                new BlockPos(16, 2, 10),
                BlockRotation.COUNTERCLOCKWISE_90
        ));

        rooms.add(new RoomDefinition(
                "titan_room",
                new Identifier("odd", "witchlog/titan_room"),
                SocketType.MEDIUM,
                10,
                1,
                -1,
                true,
                new BlockPos(16, 2, 14),
                BlockRotation.COUNTERCLOCKWISE_90
        ));

        rooms.add(new RoomDefinition(
                "sleep_room",
                new Identifier("odd", "witchlog/sleep_room"),
                SocketType.MEDIUM,
                10,
                1,
                -1,
                true,
                new BlockPos(16, 0, 14),
                BlockRotation.COUNTERCLOCKWISE_90
        ));

        rooms.add(new RoomDefinition(
                "study_room",
                new Identifier("odd", "witchlog/study_room"),
                SocketType.MEDIUM,
                10,
                1,
                -1,
                true,
                new BlockPos(14, 0, 14),
                BlockRotation.COUNTERCLOCKWISE_90
        ));

        rooms.add(new RoomDefinition(
                "titan_room_2",
                new Identifier("odd", "witchlog/titan_room_2"),
                SocketType.MEDIUM,
                10,
                1,
                -1,
                true,
                new BlockPos(16, 2, 14),
                BlockRotation.COUNTERCLOCKWISE_90
        ));

        rooms.add(new RoomDefinition(
                "brew_room",
                new Identifier("odd", "witchlog/brew_room"),
                SocketType.MEDIUM,
                8,
                1,
                -1,
                true,
                new BlockPos(11, 1, -6),
                BlockRotation.CLOCKWISE_180
        ));

        return new WitchlogRoomTable(rooms);
    }

    public List<RoomDefinition> rooms() {
        return rooms;
    }

    public boolean canPlace(RoomDefinition room, Map<String, Integer> placedCounts) {
        if (!room.enabled) {
            return false;
        }

        int placed = placedCounts.getOrDefault(room.key, 0);
        return room.maxCopies < 0 || placed < room.maxCopies;
    }

    public RoomDefinition pickWeighted(SocketType socketType, Random random, Map<String, Integer> placedCounts) {
        int totalWeight = EMPTY_SOCKET_WEIGHT;

        for (RoomDefinition room : rooms) {
            if (room.socketType != socketType) continue;
            if (!canPlace(room, placedCounts)) continue;
            if (room.weight <= 0) continue;
            totalWeight += room.weight;
        }

        if (totalWeight <= 0) {
            return null;
        }

        int roll = random.nextInt(totalWeight);

        if (roll < EMPTY_SOCKET_WEIGHT) {
            return null;
        }
        roll -= EMPTY_SOCKET_WEIGHT;

        for (RoomDefinition room : rooms) {
            if (room.socketType != socketType) continue;
            if (!canPlace(room, placedCounts)) continue;
            if (room.weight <= 0) continue;

            if (roll < room.weight) {
                return room;
            }
            roll -= room.weight;
        }

        return null;
    }

    public enum SocketType {
        SMALL,
        MEDIUM,
        TALL
    }

    public static final class RoomDefinition {
        public final String key;
        public final Identifier templateId;
        public final SocketType socketType;
        public final int weight;
        public final int requiredCount;
        public final int maxCopies;
        public final boolean enabled;
        public final BlockPos doorwayAnchor;
        public final BlockRotation authoringRotationOffset;

        public RoomDefinition(
                String key,
                Identifier templateId,
                SocketType socketType,
                int weight,
                int requiredCount,
                int maxCopies,
                boolean enabled,
                BlockPos doorwayAnchor,
                BlockRotation authoringRotationOffset
        ) {
            this.key = key;
            this.templateId = templateId;
            this.socketType = socketType;
            this.weight = weight;
            this.requiredCount = requiredCount;
            this.maxCopies = maxCopies;
            this.enabled = enabled;
            this.doorwayAnchor = doorwayAnchor.toImmutable();
            this.authoringRotationOffset = authoringRotationOffset;
        }
    }
}
