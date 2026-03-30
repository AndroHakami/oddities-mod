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
     * Increase this if you want more sockets to stay empty.
     * Lower it if you want rooms to appear more often.
     */
    public static final int EMPTY_SOCKET_WEIGHT = 4;

    private final List<RoomDefinition> rooms;

    private WitchlogRoomTable(List<RoomDefinition> rooms) {
        this.rooms = List.copyOf(rooms);
    }

    /**
     * Edit this method when you add future rooms.
     *
     * weight:
     * - higher = more common
     * - 0 = never picked randomly
     *
     * requiredCount:
     * - how many copies MUST appear if enough sockets exist
     *
     * maxCopies:
     * - -1 = unlimited
     * - otherwise caps how many copies of that room can be placed
     *
     * enabled:
     * - quick on/off toggle
     *
     * authoringRotationOffset:
     * - extra rotation applied because the room NBT was authored facing differently
     * - NONE = already authored in the expected default direction
     */
    public static WitchlogRoomTable createDefault() {
        List<RoomDefinition> rooms = new ArrayList<>();

        rooms.add(new RoomDefinition(
                "experiment_room",
                new Identifier("odd", "witchlog/experiment_room"),
                SocketType.MEDIUM,
                10,                         // weight
                1,                          // requiredCount
                2,                          // maxCopies
                true,                       // enabled
                new BlockPos(17, 0, 8),     // doorway anchor inside the template
                BlockRotation.COUNTERCLOCKWISE_90          // authoringRotationOffset
        ));
        rooms.add(new RoomDefinition(
                "titan_room",
                new Identifier("odd", "witchlog/titan_room"),
                SocketType.MEDIUM,
                10,                         // weight
                1,                          // requiredCount
                1,                          // maxCopies
                true,                       // enabled
                new BlockPos(17, 0, 8),     // doorway anchor inside the template
                BlockRotation.COUNTERCLOCKWISE_90          // authoringRotationOffset
        ));
        rooms.add(new RoomDefinition(
                "titan_room_2",
                new Identifier("odd", "witchlog/titan_room_2"),
                SocketType.MEDIUM,
                10,                         // weight
                1,                          // requiredCount
                2,                          // maxCopies
                true,                       // enabled
                new BlockPos(17, 0, 8),     // doorway anchor inside the template
                BlockRotation.COUNTERCLOCKWISE_90          // authoringRotationOffset
        ));



        rooms.add(new RoomDefinition(
                "brew_room",
                new Identifier("odd", "witchlog/brew_room"),
                SocketType.MEDIUM,
                8,                          // weight
                1,                          // requiredCount
                2,                          // maxCopies
                true,                       // enabled
                new BlockPos(10, 0, 14),     // doorway anchor inside the template
                BlockRotation.CLOCKWISE_180  // authoringRotationOffset
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