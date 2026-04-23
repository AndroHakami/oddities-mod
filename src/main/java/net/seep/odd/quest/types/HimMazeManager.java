package net.seep.odd.quest.types;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class HimMazeManager {
    private static final int MAX_VARIANT_SCAN = 64;
    private static final int COPIES_PER_VARIANT = 4;
    private static final int BASE_X = 4096;
    private static final int BASE_Z = 4096;
    private static final int SPACING = 288;
    private static final int MAZE_Y = 248;
    private static final double MIN_PLAYER_START_DISTANCE = 4.0D;

    private HimMazeManager() {}

    static MazePlacement pickAndPrepare(ServerWorld world, net.minecraft.util.math.random.Random random, int avoidSlot) {
        List<MazePlacement> placements = buildPlacements(world);
        if (placements.isEmpty()) {
            return null;
        }

        List<MazePlacement> choices = new ArrayList<>();
        for (MazePlacement placement : placements) {
            if (placement.slotIndex() != avoidSlot) {
                choices.add(placement);
            }
        }
        if (choices.isEmpty()) {
            choices = placements;
        }

        MazePlacement placement = choices.get(random.nextInt(choices.size()));
        ensurePlaced(world, placement);
        return placement;
    }

    private static List<MazePlacement> buildPlacements(ServerWorld world) {
        List<Identifier> variants = discoverVariants(world);
        List<MazePlacement> out = new ArrayList<>();
        int slot = 0;

        for (int variantIndex = 0; variantIndex < variants.size(); variantIndex++) {
            Identifier id = variants.get(variantIndex);
            Optional<StructureTemplate> optional = world.getStructureTemplateManager().getTemplate(id);
            if (optional.isEmpty()) {
                continue;
            }

            StructureTemplate template = optional.get();
            StructurePlacementData placementData = createPlacementData();
            for (int copy = 0; copy < COPIES_PER_VARIANT; copy++) {
                BlockPos base = computeBase(variantIndex, copy);
                BlockPos startMarker = findMarker(template, placementData, base, Blocks.PURPUR_PILLAR);
                BlockPos exitMarker = findMarker(template, placementData, base, Blocks.RED_SANDSTONE);
                if (startMarker == null || exitMarker == null) {
                    continue;
                }
                ensurePlaced(world, new MazePlacement(id, slot, base, startMarker.up(), startMarker, exitMarker));
                BlockPos playerStart = computePlayerStart(world, startMarker, exitMarker);
                out.add(new MazePlacement(id, slot++, base, playerStart, startMarker, exitMarker));
            }
        }
        return out;
    }

    private static List<Identifier> discoverVariants(ServerWorld world) {
        List<Identifier> ids = new ArrayList<>();
        for (int i = 1; i <= MAX_VARIANT_SCAN; i++) {
            Identifier id = new Identifier("odd", "him_maze" + i);
            if (world.getStructureTemplateManager().getTemplate(id).isPresent()) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static BlockPos computeBase(int variantIndex, int copyIndex) {
        int gridIndex = variantIndex * COPIES_PER_VARIANT + copyIndex;
        int col = gridIndex % 4;
        int row = gridIndex / 4;
        return new BlockPos(BASE_X + col * SPACING, MAZE_Y, BASE_Z + row * SPACING);
    }

    private static StructurePlacementData createPlacementData() {
        return new StructurePlacementData()
                .setRotation(BlockRotation.NONE);
    }

    private static void ensurePlaced(ServerWorld world, MazePlacement placement) {
        Optional<StructureTemplate> optional = world.getStructureTemplateManager().getTemplate(placement.templateId());
        if (optional.isEmpty()) {
            return;
        }
        StructureTemplate template = optional.get();
        template.place(world, placement.basePos(), placement.basePos(), createPlacementData(), world.getRandom(), 3);
    }

    private static BlockPos findMarker(StructureTemplate template, StructurePlacementData placementData, BlockPos base, net.minecraft.block.Block block) {
        List<StructureTemplate.StructureBlockInfo> infos = template.getInfosForBlock(base, placementData, block);
        if (infos == null || infos.isEmpty()) {
            return null;
        }
        return infos.get(0).pos();
    }

    private static BlockPos computePlayerStart(ServerWorld world, BlockPos startMarker, BlockPos exitMarker) {
        Vec3d start = Vec3d.ofCenter(startMarker.up());
        Vec3d exit = Vec3d.ofCenter(exitMarker.up());
        Vec3d dir = exit.subtract(start);
        dir = new Vec3d(dir.x, 0.0D, dir.z);
        if (dir.lengthSquared() < 0.001D) {
            dir = new Vec3d(0.0D, 0.0D, 1.0D);
        } else {
            dir = dir.normalize();
        }

        BlockPos preferred = BlockPos.ofFloored(start.add(dir.multiply(-6.0D)));
        BlockPos best = findSafeStandPos(world, preferred, startMarker, 8, MIN_PLAYER_START_DISTANCE);
        if (best == null) {
            best = findSafeStandPos(world, startMarker.up(), startMarker, 8, MIN_PLAYER_START_DISTANCE);
        }
        if (best == null) {
            best = fallbackAdjacentStand(world, startMarker, dir);
        }
        return best != null ? best : startMarker.up();
    }

    private static BlockPos findSafeStandPos(ServerWorld world, BlockPos center, BlockPos startMarker, int radius, double minDistanceFromStart) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        int[] yOffsets = new int[] {0, 1, -1, 2, -2};

        for (int r = 0; r <= radius; r++) {
            for (int yOff : yOffsets) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (r > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }

                        BlockPos feet = center.add(dx, yOff, dz);
                        if (feet.getX() == startMarker.getX() && feet.getZ() == startMarker.getZ()) {
                            continue;
                        }
                        if (horizontalDistanceSq(feet, startMarker.up()) < minDistanceFromStart * minDistanceFromStart) {
                            continue;
                        }
                        if (!isSafeStandPos(world, feet)) {
                            continue;
                        }

                        double score = feet.getSquaredDistance(center) + Math.abs(yOff) * 4.0D;
                        if (score < bestScore) {
                            bestScore = score;
                            best = feet.toImmutable();
                        }
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private static BlockPos fallbackAdjacentStand(ServerWorld world, BlockPos startMarker, Vec3d awayFromExit) {
        Direction primaryX = awayFromExit.x >= 0.0D ? Direction.EAST : Direction.WEST;
        Direction primaryZ = awayFromExit.z >= 0.0D ? Direction.SOUTH : Direction.NORTH;
        Direction[] order = new Direction[] {
                primaryX,
                primaryZ,
                primaryX.getOpposite(),
                primaryZ.getOpposite()
        };

        for (int steps = 2; steps <= 6; steps++) {
            for (Direction dir : order) {
                BlockPos feet = startMarker.offset(dir, steps).up();
                if (horizontalDistanceSq(feet, startMarker.up()) < MIN_PLAYER_START_DISTANCE * MIN_PLAYER_START_DISTANCE) {
                    continue;
                }
                if (isSafeStandPos(world, feet)) {
                    return feet;
                }
            }
        }
        return null;
    }

    private static double horizontalDistanceSq(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private static boolean isSafeStandPos(ServerWorld world, BlockPos feet) {
        BlockPos floorPos = feet.down();
        BlockState floor = world.getBlockState(floorPos);
        if (floor.isAir()) {
            return false;
        }
        if (!world.getBlockState(feet).isAir()) {
            return false;
        }
        if (!world.getBlockState(feet.up()).isAir()) {
            return false;
        }
        return floor.isSideSolidFullSquare(world, floorPos, Direction.UP) || floor.blocksMovement();
    }

    record MazePlacement(Identifier templateId, int slotIndex, BlockPos basePos, BlockPos playerStart,
                         BlockPos himStartMarker, BlockPos himExitMarker) {
    }
}
