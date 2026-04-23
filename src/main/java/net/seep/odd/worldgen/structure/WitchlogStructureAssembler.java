package net.seep.odd.worldgen.structure;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class WitchlogStructureAssembler {
    private WitchlogStructureAssembler() {}

    public static final int BAND_HEIGHT = 16;
    public static final int INTERIOR_RADIUS = 30;
    public static final int OUTER_RADIUS = 50;
    public static final int FULL_TOWER_BAND_COUNT = 12;
    public static final int ROOM_DOOR_Y_OFFSET = 2;

    /**
     * arenaOrigin means:
     * - center of arena platform in X/Z
     * - Y = top walking surface of the arena platform
     */
    private static final BlockPos ARENA_PLATFORM_OFFSET = new BlockPos(-30, -9, -30);
    private static final BlockPos ARENA_POISON_MOAT_OFFSET = new BlockPos(-50, -20, -50);
    private static final BlockPos ARENA_BOTTOM_CAP_OFFSET = new BlockPos(-50, -20, -50);
    private static final BlockPos ARENA_LOWER_SHELL_OFFSET = new BlockPos(0, -20, 0);
    private static final BlockPos ARENA_UPPER_ROOF_OFFSET = new BlockPos(0, 0, 0);

    /**
     * Your transition pack is two pieces:
     * - witchlog_transition_shell_q
     * - witchlog_transition_interior_band
     */
    private static final BlockPos TRANSITION_SHELL_OFFSET = new BlockPos(0, 31, 0);
    private static final BlockPos TRANSITION_INTERIOR_OFFSET = new BlockPos(-30, 31, -30);

    /**
     * Tower base begins above the arena.
     */
    private static final BlockPos TOWER_BASE_OFFSET = new BlockPos(0, 30, 0);

    private static final Identifier SHELL_BASE_Q = id("witchlog_shell_base_q");
    private static final Identifier SHELL_LOWER_PLAIN_Q = id("witchlog_shell_lower_plain_q");
    private static final Identifier SHELL_LOWER_SOCKET_Q = id("witchlog_shell_lower_socket_q");

    private static final Identifier INTERIOR_EMPTY = id("witchlog_interior_empty_band");
    private static final Identifier INTERIOR_EMPTY_WEST_SOCKET = id("witchlog_interior_empty_west_socket_band");

    private static final Identifier INTERIOR_DOUBLELEDGE = id("witchlog_interior_doubleledge_band");
    private static final Identifier INTERIOR_DOUBLELEDGE_SOUTH_SOCKET = id("witchlog_interior_doubleledge_south_socket_band");
    private static final Identifier INTERIOR_DOUBLELEDGE_EAST_SOCKET = id("witchlog_interior_doubleledge_east_socket_band");

    private static final Identifier INTERIOR_ROOMHUB = id("witchlog_interior_roomhub_band");
    private static final Identifier INTERIOR_ROOMHUB_EAST_SOCKET = id("witchlog_interior_roomhub_east_socket_band");
    private static final Identifier INTERIOR_ROOMHUB_NORTH_SOCKET = id("witchlog_interior_roomhub_north_socket_band");
    private static final Identifier INTERIOR_ROOMHUB_SOUTH_SOCKET = id("witchlog_interior_roomhub_south_socket_band");

    private static final Identifier ARENA_PLATFORM_BAND = id("witchlog_arena_platform_band");
    private static final Identifier ARENA_BOTTOM_CAP_BAND = id("witchlog_arena_bottom_cap_band");
    private static final Identifier ARENA_POISON_MOAT_BAND = id("witchlog_arena_poison_moat_band");
    private static final Identifier ARENA_SHELL_LOWER_Q = id("witchlog_arena_shell_lower_q");
    private static final Identifier ARENA_SHELL_UPPERROOF_Q = id("witchlog_arena_shell_upperroof_q");

    private static final Identifier TRANSITION_SHELL_Q = id("witchlog_transition_shell_q");
    private static final Identifier TRANSITION_INTERIOR_BAND = id("witchlog_transition_interior_band");

    private static final Identifier BOGGY_WEB_SPRAWL = id("witchlog_boggy_web_sprawl");
    private static final BlockPos BOGGY_WEB_SPRAWL_OFFSET = new BlockPos(-30, 0, -30);

    private static final BandPlan[] FULL_BAND_PLANS = new BandPlan[] {
            // 0
            band(SHELL_BASE_Q, INTERIOR_DOUBLELEDGE),

            // 1
            band(SHELL_LOWER_PLAIN_Q, INTERIOR_EMPTY),

            // 2 - socket on EAST wall
            new BandPlan(
                    SHELL_LOWER_PLAIN_Q,
                    SHELL_LOWER_SOCKET_Q,
                    SHELL_LOWER_PLAIN_Q,
                    SHELL_LOWER_PLAIN_Q,
                    INTERIOR_ROOMHUB_EAST_SOCKET
            ),

            // 3
            band(SHELL_LOWER_PLAIN_Q, INTERIOR_EMPTY),

            // 4 - socket on SOUTH wall
            new BandPlan(
                    SHELL_LOWER_SOCKET_Q,
                    SHELL_LOWER_PLAIN_Q,
                    SHELL_LOWER_PLAIN_Q,
                    SHELL_LOWER_PLAIN_Q,
                    INTERIOR_DOUBLELEDGE_SOUTH_SOCKET
            ),

            // 5
            band(SHELL_LOWER_PLAIN_Q, INTERIOR_ROOMHUB),

            // 6 - socket on WEST wall
            new BandPlan(
                    SHELL_LOWER_PLAIN_Q,
                    SHELL_LOWER_PLAIN_Q,
                    SHELL_LOWER_PLAIN_Q,
                    SHELL_LOWER_SOCKET_Q,
                    INTERIOR_EMPTY_WEST_SOCKET
            ),

            // 7
            band(SHELL_LOWER_PLAIN_Q, INTERIOR_DOUBLELEDGE),

            // 8 - socket on NORTH wall
            new BandPlan(
                    SHELL_LOWER_PLAIN_Q,
                    SHELL_LOWER_PLAIN_Q,
                    SHELL_LOWER_SOCKET_Q,
                    SHELL_LOWER_PLAIN_Q,
                    INTERIOR_ROOMHUB_NORTH_SOCKET
            ),

            // 9
            band(SHELL_LOWER_PLAIN_Q, INTERIOR_EMPTY),

            // 10 - socket on EAST wall
            new BandPlan(
                    SHELL_LOWER_PLAIN_Q,
                    SHELL_LOWER_SOCKET_Q,
                    SHELL_LOWER_PLAIN_Q,
                    SHELL_LOWER_PLAIN_Q,
                    INTERIOR_DOUBLELEDGE_EAST_SOCKET
            ),

            // 11 - socket on SOUTH wall
            new BandPlan(
                    SHELL_LOWER_SOCKET_Q,
                    SHELL_LOWER_PLAIN_Q,
                    SHELL_LOWER_PLAIN_Q,
                    SHELL_LOWER_PLAIN_Q,
                    INTERIOR_ROOMHUB_SOUTH_SOCKET
            )
    };

    public static int placeArena(ServerWorld world, BlockPos arenaOrigin) {
        clearArenaEnvelope(world, arenaOrigin);
        return placeArenaInternal(world, arenaOrigin);
    }

    public static int placeArenaTransition(ServerWorld world, BlockPos arenaOrigin) {
        clearTransitionEnvelope(world, arenaOrigin);
        return placeArenaTransitionInternal(world, arenaOrigin);
    }

    /**
     * centerPos here is the tower base center:
     * - center of the tower in X/Z
     * - Y = first tower band base
     */
    public static int placeFirstPack(ServerWorld world, BlockPos centerPos) {
        clearTowerEnvelope(world, centerPos, 3);
        return placeTowerInternal(world, centerPos, 3);
    }

    public static int placeArenaStack(ServerWorld world, BlockPos arenaOrigin) {
        clearArenaStackEnvelope(world, arenaOrigin);

        int placed = 0;
        placed += placeArenaInternal(world, arenaOrigin);
        placed += placeArenaTransitionInternal(world, arenaOrigin);
        placed += placeTowerInternal(world, arenaOrigin.add(TOWER_BASE_OFFSET), 3);
        return placed;
    }

    public static int placeFullTower(ServerWorld world, BlockPos towerBaseCenter) {
        clearTowerEnvelope(world, towerBaseCenter, FULL_TOWER_BAND_COUNT);

        int placed = 0;
        placed += placeTowerInternal(world, towerBaseCenter, FULL_TOWER_BAND_COUNT);
        placed += placeBoggyWebSprawl(world, towerBaseCenter);
        return placed;
    }

    public static int placeDefaultRooms(ServerWorld world, BlockPos arenaOrigin) {
        BlockPos towerBaseCenter = arenaOrigin.add(TOWER_BASE_OFFSET);
        return placeRooms(world, buildDefaultRoomSockets(towerBaseCenter), WitchlogRoomTable.createDefault());
    }

    public static int placeFullStructure(ServerWorld world, BlockPos arenaOrigin) {
        clearFullStructureEnvelope(world, arenaOrigin);

        int placed = 0;
        placed += placeArenaInternal(world, arenaOrigin);
        placed += placeArenaTransitionInternal(world, arenaOrigin);

        BlockPos towerBaseCenter = arenaOrigin.add(TOWER_BASE_OFFSET);
        placed += placeTowerInternal(world, towerBaseCenter, FULL_TOWER_BAND_COUNT);
        placed += placeBoggyWebSprawl(world, towerBaseCenter);

        // Place rooms after the web so room carving can cleanly cut openings through it if needed.
        placed += placeRooms(world, buildDefaultRoomSockets(towerBaseCenter), WitchlogRoomTable.createDefault());

        return placed;
    }

    public static int placeRooms(ServerWorld world, List<RoomSocket> sockets, WitchlogRoomTable roomTable) {
        int placed = 0;
        Random random = world.getRandom();

        boolean[] occupied = new boolean[sockets.size()];
        Map<String, Integer> placedCounts = new HashMap<>();

        for (WitchlogRoomTable.RoomDefinition room : roomTable.rooms()) {
            for (int i = 0; i < room.requiredCount; i++) {
                if (!roomTable.canPlace(room, placedCounts)) {
                    break;
                }

                int socketIndex = findFreeSocketIndex(sockets, occupied, room.socketType, random);
                if (socketIndex < 0) {
                    break;
                }

                int success = placeRoom(world, sockets.get(socketIndex), room);
                if (success > 0) {
                    occupied[socketIndex] = true;
                    placed += success;
                    placedCounts.merge(room.key, 1, Integer::sum);
                }
            }
        }

        for (int i = 0; i < sockets.size(); i++) {
            if (occupied[i]) {
                continue;
            }

            RoomSocket socket = sockets.get(i);
            WitchlogRoomTable.RoomDefinition picked = roomTable.pickWeighted(socket.socketType, random, placedCounts);
            if (picked == null) {
                continue;
            }

            int success = placeRoom(world, socket, picked);
            if (success > 0) {
                occupied[i] = true;
                placed += success;
                placedCounts.merge(picked.key, 1, Integer::sum);
            }
        }

        return placed;
    }

    public static int placeRoom(ServerWorld world, RoomSocket socket, WitchlogRoomTable.RoomDefinition room) {
        BlockRotation finalRotation = combineRotation(socket.facing.rotation, room.authoringRotationOffset);
        BlockPos rotatedAnchor = rotateOffset(room.doorwayAnchor, finalRotation);
        BlockPos templatePos = socket.thresholdCenter.subtract(rotatedAnchor);
        return place(world, room.templateId, templatePos, finalRotation, true);
    }

    public static int placeBand(ServerWorld world, BlockPos bandOrigin, BandPlan plan) {
        int placed = 0;

        placed += place(world, plan.shell0, bandOrigin, BlockRotation.NONE, false);
        placed += place(world, plan.shell90, bandOrigin, BlockRotation.CLOCKWISE_90, false);
        placed += place(world, plan.shell180, bandOrigin, BlockRotation.CLOCKWISE_180, false);
        placed += place(world, plan.shell270, bandOrigin, BlockRotation.COUNTERCLOCKWISE_90, false);

        placed += place(
                world,
                plan.interior,
                bandOrigin.add(-INTERIOR_RADIUS, 0, -INTERIOR_RADIUS),
                BlockRotation.NONE,
                false
        );

        return placed;
    }

    public static int placeShellQuarter4(ServerWorld world, Identifier templateId, BlockPos pos) {
        int placed = 0;
        placed += place(world, templateId, pos, BlockRotation.NONE, false);
        placed += place(world, templateId, pos, BlockRotation.CLOCKWISE_90, false);
        placed += place(world, templateId, pos, BlockRotation.CLOCKWISE_180, false);
        placed += place(world, templateId, pos, BlockRotation.COUNTERCLOCKWISE_90, false);
        return placed;
    }

    public static int place(ServerWorld world, Identifier templateId, BlockPos pos, BlockRotation rotation) {
        return place(world, templateId, pos, rotation, false);
    }

    public static int place(ServerWorld world, Identifier templateId, BlockPos pos, BlockRotation rotation, boolean clearAreaFirst) {
        StructureTemplateManager manager = world.getStructureTemplateManager();
        Optional<StructureTemplate> optional = manager.getTemplate(templateId);

        if (optional.isEmpty()) {
            System.out.println("[Witchlog] Missing template: " + templateId);
            return 0;
        }

        StructureTemplate template = optional.get();

        StructurePlacementData placementData = new StructurePlacementData()
                .setMirror(BlockMirror.NONE)
                .setRotation(rotation)
                .setIgnoreEntities(false);

        if (clearAreaFirst) {
            BlockBox box = template.calculateBoundingBox(placementData, pos);
            placementData.setBoundingBox(box);
            clearBoxToAir(world, box);
        }

        boolean success = template.place(
                world,
                pos,
                pos,
                placementData,
                world.getRandom(),
                Block.NOTIFY_LISTENERS
        );

        if (!success) {
            System.out.println("[Witchlog] Failed placing template: " + templateId + " at " + pos + " rot=" + rotation);
            return 0;
        }

        return 1;
    }

    private static int placeArenaInternal(ServerWorld world, BlockPos arenaOrigin) {
        int placed = 0;

        placed += place(world, ARENA_BOTTOM_CAP_BAND, arenaOrigin.add(ARENA_BOTTOM_CAP_OFFSET), BlockRotation.NONE, false);
        placed += place(world, ARENA_PLATFORM_BAND, arenaOrigin.add(ARENA_PLATFORM_OFFSET), BlockRotation.NONE, false);
        placed += place(world, ARENA_POISON_MOAT_BAND, arenaOrigin.add(ARENA_POISON_MOAT_OFFSET), BlockRotation.NONE, false);

        placed += placeShellQuarter4(world, ARENA_SHELL_LOWER_Q, arenaOrigin.add(ARENA_LOWER_SHELL_OFFSET));
        placed += placeShellQuarter4(world, ARENA_SHELL_UPPERROOF_Q, arenaOrigin.add(ARENA_UPPER_ROOF_OFFSET));

        return placed;
    }

    private static int placeArenaTransitionInternal(ServerWorld world, BlockPos arenaOrigin) {
        int placed = 0;
        placed += placeShellQuarter4(world, TRANSITION_SHELL_Q, arenaOrigin.add(TRANSITION_SHELL_OFFSET));
        placed += place(world, TRANSITION_INTERIOR_BAND, arenaOrigin.add(TRANSITION_INTERIOR_OFFSET), BlockRotation.NONE, false);
        return placed;
    }

    private static int placeTowerInternal(ServerWorld world, BlockPos towerBaseCenter, int bandCount) {
        int placed = 0;

        for (int i = 0; i < bandCount; i++) {
            BlockPos bandOrigin = towerBaseCenter.add(0, BAND_HEIGHT * i, 0);
            placed += placeBand(world, bandOrigin, FULL_BAND_PLANS[i]);
        }

        return placed;
    }


    private static int placeBoggyWebSprawl(ServerWorld world, BlockPos towerBaseCenter) {
        return place(world, BOGGY_WEB_SPRAWL, towerBaseCenter.add(BOGGY_WEB_SPRAWL_OFFSET), BlockRotation.NONE, false);
    }

    private static BlockRotation combineRotation(BlockRotation first, BlockRotation second) {
        int turns = rotationToQuarterTurns(first) + rotationToQuarterTurns(second);
        return quarterTurnsToRotation(turns);
    }

    private static int rotationToQuarterTurns(BlockRotation rotation) {
        return switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
        };
    }

    private static BlockRotation quarterTurnsToRotation(int turns) {
        int normalized = ((turns % 4) + 4) % 4;
        return switch (normalized) {
            case 0 -> BlockRotation.NONE;
            case 1 -> BlockRotation.CLOCKWISE_90;
            case 2 -> BlockRotation.CLOCKWISE_180;
            case 3 -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }

    private static void clearArenaEnvelope(ServerWorld world, BlockPos arenaOrigin) {
        clearBoxToAir(world,
                arenaOrigin.getX() - OUTER_RADIUS,
                arenaOrigin.getY() - 20,
                arenaOrigin.getZ() - OUTER_RADIUS,
                arenaOrigin.getX() + OUTER_RADIUS,
                arenaOrigin.getY() + 30,
                arenaOrigin.getZ() + OUTER_RADIUS
        );
    }

    private static void clearTransitionEnvelope(ServerWorld world, BlockPos arenaOrigin) {
        clearBoxToAir(world,
                arenaOrigin.getX() - OUTER_RADIUS,
                arenaOrigin.getY() + 31,
                arenaOrigin.getZ() - OUTER_RADIUS,
                arenaOrigin.getX() + OUTER_RADIUS,
                arenaOrigin.getY() + 31 + BAND_HEIGHT - 1,
                arenaOrigin.getZ() + OUTER_RADIUS
        );
    }

    private static void clearTowerEnvelope(ServerWorld world, BlockPos towerBaseCenter, int bandCount) {
        clearBoxToAir(world,
                towerBaseCenter.getX() - OUTER_RADIUS,
                towerBaseCenter.getY(),
                towerBaseCenter.getZ() - OUTER_RADIUS,
                towerBaseCenter.getX() + OUTER_RADIUS,
                towerBaseCenter.getY() + bandCount * BAND_HEIGHT - 1,
                towerBaseCenter.getZ() + OUTER_RADIUS
        );
    }

    private static void clearArenaStackEnvelope(ServerWorld world, BlockPos arenaOrigin) {
        BlockPos towerBaseCenter = arenaOrigin.add(TOWER_BASE_OFFSET);

        clearBoxToAir(world,
                arenaOrigin.getX() - OUTER_RADIUS,
                arenaOrigin.getY() - 20,
                arenaOrigin.getZ() - OUTER_RADIUS,
                arenaOrigin.getX() + OUTER_RADIUS,
                towerBaseCenter.getY() + (3 * BAND_HEIGHT) - 1,
                arenaOrigin.getZ() + OUTER_RADIUS
        );
    }

    private static void clearFullStructureEnvelope(ServerWorld world, BlockPos arenaOrigin) {
        BlockPos towerBaseCenter = arenaOrigin.add(TOWER_BASE_OFFSET);

        clearBoxToAir(world,
                arenaOrigin.getX() - OUTER_RADIUS,
                arenaOrigin.getY() - 20,
                arenaOrigin.getZ() - OUTER_RADIUS,
                arenaOrigin.getX() + OUTER_RADIUS,
                towerBaseCenter.getY() + (FULL_TOWER_BAND_COUNT * BAND_HEIGHT) - 1,
                arenaOrigin.getZ() + OUTER_RADIUS
        );
    }

    private static void clearBoxToAir(ServerWorld world, BlockBox box) {
        clearBoxToAir(world, box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ());
    }

    private static void clearBoxToAir(ServerWorld world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutable.set(x, y, z);
                    world.setBlockState(mutable, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
    }

    public static Identifier id(String path) {
        return new Identifier("odd", "witchlog/" + path);
    }

    private static BandPlan band(Identifier shellQuarter, Identifier interior) {
        return new BandPlan(shellQuarter, shellQuarter, shellQuarter, shellQuarter, interior);
    }

    private static List<RoomSocket> buildDefaultRoomSockets(BlockPos towerBaseCenter) {
        List<RoomSocket> sockets = new ArrayList<>();

        sockets.add(new RoomSocket(
                "band2_east",
                WitchlogRoomTable.SocketType.MEDIUM,
                towerBaseCenter.add(INTERIOR_RADIUS, BAND_HEIGHT * 2 + ROOM_DOOR_Y_OFFSET, 0),
                SocketFacing.EAST
        ));

        sockets.add(new RoomSocket(
                "band4_south",
                WitchlogRoomTable.SocketType.MEDIUM,
                towerBaseCenter.add(0, BAND_HEIGHT * 4 + ROOM_DOOR_Y_OFFSET, INTERIOR_RADIUS),
                SocketFacing.SOUTH
        ));

        sockets.add(new RoomSocket(
                "band6_west",
                WitchlogRoomTable.SocketType.MEDIUM,
                towerBaseCenter.add(-INTERIOR_RADIUS, BAND_HEIGHT * 6 + ROOM_DOOR_Y_OFFSET, 0),
                SocketFacing.WEST
        ));

        sockets.add(new RoomSocket(
                "band8_north",
                WitchlogRoomTable.SocketType.MEDIUM,
                towerBaseCenter.add(0, BAND_HEIGHT * 8 + ROOM_DOOR_Y_OFFSET, -INTERIOR_RADIUS),
                SocketFacing.NORTH
        ));

        sockets.add(new RoomSocket(
                "band10_east",
                WitchlogRoomTable.SocketType.MEDIUM,
                towerBaseCenter.add(INTERIOR_RADIUS, BAND_HEIGHT * 10 + ROOM_DOOR_Y_OFFSET, 0),
                SocketFacing.EAST
        ));

        sockets.add(new RoomSocket(
                "band11_south",
                WitchlogRoomTable.SocketType.MEDIUM,
                towerBaseCenter.add(0, BAND_HEIGHT * 11 + ROOM_DOOR_Y_OFFSET, INTERIOR_RADIUS),
                SocketFacing.SOUTH
        ));

        return sockets;
    }

    private static int findFreeSocketIndex(
            List<RoomSocket> sockets,
            boolean[] occupied,
            WitchlogRoomTable.SocketType socketType,
            Random random
    ) {
        List<Integer> matches = new ArrayList<>();

        for (int i = 0; i < sockets.size(); i++) {
            if (occupied[i]) continue;
            if (sockets.get(i).socketType != socketType) continue;
            matches.add(i);
        }

        if (matches.isEmpty()) {
            return -1;
        }

        return matches.get(random.nextInt(matches.size()));
    }

    private static BlockPos rotateOffset(BlockPos offset, BlockRotation rotation) {
        int x = offset.getX();
        int y = offset.getY();
        int z = offset.getZ();

        return switch (rotation) {
            case NONE -> new BlockPos(x, y, z);
            case CLOCKWISE_90 -> new BlockPos(-z, y, x);
            case CLOCKWISE_180 -> new BlockPos(-x, y, -z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(z, y, -x);
        };
    }

    private static final class BandPlan {
        final Identifier shell0;
        final Identifier shell90;
        final Identifier shell180;
        final Identifier shell270;
        final Identifier interior;

        private BandPlan(
                Identifier shell0,
                Identifier shell90,
                Identifier shell180,
                Identifier shell270,
                Identifier interior
        ) {
            this.shell0 = shell0;
            this.shell90 = shell90;
            this.shell180 = shell180;
            this.shell270 = shell270;
            this.interior = interior;
        }
    }

    public static final class RoomSocket {
        public final String key;
        public final WitchlogRoomTable.SocketType socketType;
        public final BlockPos thresholdCenter;
        public final SocketFacing facing;

        public RoomSocket(
                String key,
                WitchlogRoomTable.SocketType socketType,
                BlockPos thresholdCenter,
                SocketFacing facing
        ) {
            this.key = key;
            this.socketType = socketType;
            this.thresholdCenter = thresholdCenter.toImmutable();
            this.facing = facing;
        }
    }

    public enum SocketFacing {
        NORTH(BlockRotation.CLOCKWISE_180),
        EAST(BlockRotation.COUNTERCLOCKWISE_90),
        SOUTH(BlockRotation.NONE),
        WEST(BlockRotation.CLOCKWISE_90);

        public final BlockRotation rotation;

        SocketFacing(BlockRotation rotation) {
            this.rotation = rotation;
        }
    }
}
