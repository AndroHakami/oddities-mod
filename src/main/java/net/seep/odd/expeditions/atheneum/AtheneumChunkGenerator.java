// src/main/java/net/seep/odd/expeditions/atheneum/AtheneumChunkGenerator.java
package net.seep.odd.expeditions.atheneum;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PillarBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;

import net.seep.odd.block.ModBlocks;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Atheneum (warm framed-library gen, infinite maze):
 * - solid "library mass" carved into corridors/rooms
 * - 3 bedrock layers under floor
 * - 20 block tall walls
 * - 10 layers of barrier above walls (prevents climbing, still see sky)
 *
 * Shelves:
 * - Uses ModBlocks.DREAM_BOOKSHELF as the default shelf block.
 * - Rare single-block replacements (only when placing a shelf):
 *   - quartz (semi-rare)
 *   - diamond (very rare)
 *
 * Librarian alcoves:
 * - Template id: odd:librarian_area
 * - Dropped into shelf walls, flush with the facade.
 * - Tuning is intentionally exposed in the LIBRARIAN_* constants below.
 */
public final class AtheneumChunkGenerator extends ChunkGenerator {

    public static final MapCodec<AtheneumChunkGenerator> CONFIG_CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
                    Codec.LONG.optionalFieldOf("seed", 0L).forGetter(g -> g.seed)
            ).apply(instance, AtheneumChunkGenerator::new)
    );

    public static final Codec<AtheneumChunkGenerator> CODEC = CONFIG_CODEC.codec();

    private static final Identifier LIBRARIAN_AREA_ID = new Identifier("odd", "librarian_area");
    private static final long LIBRARIAN_AREA_SALT = 0x4C49425241524941L; // "LIBRARIA"

    private final long seed;

    // ======== constants ========
    private static final int BEDROCK_LAYERS = 3;
    private static final int WALL_HEIGHT = 20;
    private static final int BARRIER_LAYERS = 10;

    // Macro maze cell size
    private static final int CELL = 12;

    /* ===================== librarian area tuning =====================
     * Make these bigger/smaller to control how often the structure appears.
     *
     * SPACING_CHUNKS  = minimum broad spacing between spawn cells.
     * CHANCE          = 1 in N spawn cells will actually try to place one.
     * SEARCH_ATTEMPTS = how hard a chosen cell searches for a valid shelf wall.
     */
    private static final int LIBRARIAN_AREA_SPACING_CHUNKS = 7;
    private static final int LIBRARIAN_AREA_CHANCE = 2; // 1 in 2 cells
    private static final int LIBRARIAN_AREA_CELL_MARGIN_BLOCKS = 16;
    private static final int LIBRARIAN_AREA_SEARCH_RADIUS = 18;
    private static final int LIBRARIAN_AREA_SEARCH_ATTEMPTS = 96;
    private static final int LIBRARIAN_AREA_FRONT_CLEARANCE = 3;

    private static final int LIBRARIAN_AREA_WIDTH = 7;
    private static final int LIBRARIAN_AREA_DEPTH = 5;
    private static final int LIBRARIAN_AREA_HEIGHT = 5;
    private static final int LIBRARIAN_AREA_HALF_WIDTH = LIBRARIAN_AREA_WIDTH / 2;

    public AtheneumChunkGenerator(BiomeSource biomeSource, long seed) {
        super(biomeSource);
        this.seed = seed;
    }

    @Override
    protected Codec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }


    public ChunkGenerator withSeed(long seed) {
        return new AtheneumChunkGenerator(this.biomeSource, seed);
    }

    @Override
    public void carve(ChunkRegion region, long seed, NoiseConfig noiseConfig, BiomeAccess biomeAccess,
                      StructureAccessor structureAccessor, Chunk chunk, GenerationStep.Carver carverStep) {
        // no vanilla carving
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Executor executor, Blender blender, NoiseConfig noiseConfig,
                                                  StructureAccessor structures, Chunk chunk) {
        paint(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    @Override public int getSeaLevel() { return 0; }

    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor sa, NoiseConfig nc, Chunk chunk) {
        placeLibrarianAreas(region, chunk);
    }

    @Override public void populateEntities(ChunkRegion region) {}

    @Override public int getMinimumY() { return -64; }
    @Override public int getWorldHeight() { return 384; }
    @Override public int getSpawnHeight(HeightLimitView world) { return 120; }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
        int bottom = world.getBottomY();
        int height = world.getHeight();
        BlockState[] states = new BlockState[height];
        for (int i = 0; i < height; i++) states[i] = Blocks.AIR.getDefaultState();
        for (int i = 0; i < Math.min(BEDROCK_LAYERS, height); i++) states[i] = Blocks.BEDROCK.getDefaultState();
        if (BEDROCK_LAYERS < height) states[BEDROCK_LAYERS] = Blocks.DARK_OAK_PLANKS.getDefaultState();
        return new VerticalBlockSample(bottom, states);
    }

    @Override public void getDebugHudText(List<String> text, NoiseConfig nc, BlockPos pos) {}
    @Override public int getHeight(int x, int z, Heightmap.Type type, HeightLimitView limits, NoiseConfig noiseConfig) { return 0; }

    /* ===================== painting ===================== */

    private static final class OpenInfo {
        final boolean open;
        final boolean inRoom;
        final boolean inCorridor;
        OpenInfo(boolean open, boolean inRoom, boolean inCorridor) {
            this.open = open;
            this.inRoom = inRoom;
            this.inCorridor = inCorridor;
        }
    }

    private static final class LibrarianPlacement {
        final BlockPos origin;
        final BlockRotation rotation;
        final BlockBox boundingBox;

        LibrarianPlacement(BlockPos origin, BlockRotation rotation, BlockBox boundingBox) {
            this.origin = origin;
            this.rotation = rotation;
            this.boundingBox = boundingBox;
        }
    }

    private void paint(Chunk chunk) {
        final int bottomY = chunk.getBottomY();
        final int floorY = bottomY + BEDROCK_LAYERS;
        final int wallTopY = floorY + WALL_HEIGHT;
        final int barrierTopY = wallTopY + BARRIER_LAYERS - 1;

        final int x0 = chunk.getPos().getStartX();
        final int z0 = chunk.getPos().getStartZ();

        final BlockState air = Blocks.AIR.getDefaultState();
        final BlockState bedrock = Blocks.BEDROCK.getDefaultState();
        final BlockState barrier = Blocks.BARRIER.getDefaultState();

        // Precompute open map for local chunk + 1-block border (18x18)
        OpenInfo[][] info = new OpenInfo[18][18];
        for (int dx = -1; dx <= 16; dx++) for (int dz = -1; dz <= 16; dz++) {
            int wx = x0 + dx;
            int wz = z0 + dz;
            info[dx + 1][dz + 1] = computeOpenInfo(wx, wz);
        }

        BlockPos.Mutable m = new BlockPos.Mutable();

        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int wx = x0 + dx;
            int wz = z0 + dz;

            OpenInfo me = info[dx + 1][dz + 1];

            // 1) bedrock layers
            for (int i = 0; i < BEDROCK_LAYERS; i++) {
                m.set(wx, bottomY + i, wz);
                chunk.setBlockState(m, bedrock, false);
            }

            // 2) floor
            BlockState floor = pickFloor(wx, wz, me);
            m.set(wx, floorY, wz);
            chunk.setBlockState(m, floor, false);

            // neighbor openness for facades
            boolean openE = info[dx + 2][dz + 1].open;
            boolean openW = info[dx + 0][dz + 1].open;
            boolean openS = info[dx + 1][dz + 2].open;
            boolean openN = info[dx + 1][dz + 0].open;

            // 3) walls / air
            for (int y = floorY + 1; y < wallTopY; y++) {
                m.set(wx, y, wz);

                if (me.open) {
                    chunk.setBlockState(m, air, false);
                } else {
                    boolean exposed = openE || openW || openS || openN;
                    if (exposed) {
                        Direction face = pickFace(openE, openW, openS, openN);
                        chunk.setBlockState(m, pickFacadeBlock(wx, y, wz, floorY, wallTopY, me.inRoom, face), false);
                    } else {
                        // inner mass (unseen): cheap fill
                        chunk.setBlockState(m, Blocks.DARK_OAK_PLANKS.getDefaultState(), false);
                    }
                }
            }

            // 4) barrier ceiling
            for (int y = wallTopY; y <= barrierTopY; y++) {
                m.set(wx, y, wz);
                chunk.setBlockState(m, barrier, false);
            }
        }
    }

    private static Direction pickFace(boolean openE, boolean openW, boolean openS, boolean openN) {
        if (openE) return Direction.EAST;
        if (openW) return Direction.WEST;
        if (openS) return Direction.SOUTH;
        return Direction.NORTH;
    }

    /* ===================== librarian area placement ===================== */

    private void placeLibrarianAreas(ChunkRegion region, Chunk chunk) {
        ServerWorld serverWorld = region.toServerWorld();
        StructureTemplateManager manager = serverWorld.getServer().getStructureTemplateManager();
        Optional<StructureTemplate> optional = manager.getTemplate(LIBRARIAN_AREA_ID);
        if (optional.isEmpty()) return;

        StructureTemplate template = optional.get();
        ChunkPos chunkPos = chunk.getPos();
        int bottomY = chunk.getBottomY();

        BlockBox chunkBox = new BlockBox(
                chunkPos.getStartX(),
                bottomY,
                chunkPos.getStartZ(),
                chunkPos.getEndX(),
                bottomY + getWorldHeight() - 1,
                chunkPos.getEndZ()
        );

        int cellX0 = Math.floorDiv(chunkPos.x, LIBRARIAN_AREA_SPACING_CHUNKS) - 1;
        int cellX1 = Math.floorDiv(chunkPos.x, LIBRARIAN_AREA_SPACING_CHUNKS) + 1;
        int cellZ0 = Math.floorDiv(chunkPos.z, LIBRARIAN_AREA_SPACING_CHUNKS) - 1;
        int cellZ1 = Math.floorDiv(chunkPos.z, LIBRARIAN_AREA_SPACING_CHUNKS) + 1;

        for (int cellX = cellX0; cellX <= cellX1; cellX++) {
            for (int cellZ = cellZ0; cellZ <= cellZ1; cellZ++) {
                LibrarianPlacement placement = findLibrarianPlacementForCell(cellX, cellZ, bottomY);
                if (placement == null || !placement.boundingBox.intersects(chunkBox)) {
                    continue;
                }

                StructurePlacementData data = new StructurePlacementData()
                        .setRotation(placement.rotation)
                        .setMirror(BlockMirror.NONE)
                        .setIgnoreEntities(false)
                        .setUpdateNeighbors(false)
                        .setBoundingBox(chunkBox);

                template.place(region, placement.origin, BlockPos.ORIGIN, data,
                        serverWorld.getRandom(), Block.NOTIFY_LISTENERS);
            }
        }
    }

    private LibrarianPlacement findLibrarianPlacementForCell(int cellX, int cellZ, int bottomY) {
        long cellHash = scrambleId(seed ^ LIBRARIAN_AREA_SALT, cellX * 73471 + cellZ * 193513);
        if (LIBRARIAN_AREA_CHANCE > 1 && Math.floorMod(cellHash, LIBRARIAN_AREA_CHANCE) != 0L) {
            return null;
        }

        int cellSize = LIBRARIAN_AREA_SPACING_CHUNKS * 16;
        int baseX = cellX * cellSize;
        int baseZ = cellZ * cellSize;
        int usable = Math.max(1, cellSize - (LIBRARIAN_AREA_CELL_MARGIN_BLOCKS * 2));

        int centerX = baseX + LIBRARIAN_AREA_CELL_MARGIN_BLOCKS + Math.floorMod((int) (cellHash >>> 11), usable);
        int centerZ = baseZ + LIBRARIAN_AREA_CELL_MARGIN_BLOCKS + Math.floorMod((int) (cellHash >>> 27), usable);

        int directionOffset = Math.floorMod((int) (cellHash >>> 42), 4);
        Direction[] order = new Direction[] {
                horizontalByIndex(directionOffset),
                horizontalByIndex(directionOffset + 1),
                horizontalByIndex(directionOffset + 2),
                horizontalByIndex(directionOffset + 3)
        };

        for (int attempt = 0; attempt < LIBRARIAN_AREA_SEARCH_ATTEMPTS; attempt++) {
            long attemptHash = scrambleId(cellHash, 1013 + attempt * 31);
            int dx = Math.floorMod((int) (attemptHash >>> 7), LIBRARIAN_AREA_SEARCH_RADIUS * 2 + 1) - LIBRARIAN_AREA_SEARCH_RADIUS;
            int dz = Math.floorMod((int) (attemptHash >>> 21), LIBRARIAN_AREA_SEARCH_RADIUS * 2 + 1) - LIBRARIAN_AREA_SEARCH_RADIUS;

            int x = centerX + dx;
            int z = centerZ + dz;

            for (Direction face : order) {
                if (isValidLibrarianFacade(x, z, face)) {
                    return createLibrarianPlacement(x, z, face, bottomY + BEDROCK_LAYERS);
                }
            }
        }

        return null;
    }

    private LibrarianPlacement createLibrarianPlacement(int facadeCenterX, int facadeCenterZ, Direction face, int floorY) {
        BlockRotation rotation = rotationForFront(face);
        Vec3i rotatedFrontCenter = rotateLocalPos(3, 4, rotation, LIBRARIAN_AREA_WIDTH, LIBRARIAN_AREA_DEPTH);
        Vec3i rotatedSize = rotatedSize(LIBRARIAN_AREA_WIDTH, LIBRARIAN_AREA_HEIGHT, LIBRARIAN_AREA_DEPTH, rotation);

        // Nudge the alcove 1 block upward and 1 block outward so it sits better in the shelf wall.
        BlockPos origin = new BlockPos(
                facadeCenterX - rotatedFrontCenter.getX() + face.getOffsetX(),
                floorY + 1,
                facadeCenterZ - rotatedFrontCenter.getZ() + face.getOffsetZ()
        );

        BlockBox box = new BlockBox(
                origin.getX(),
                origin.getY(),
                origin.getZ(),
                origin.getX() + rotatedSize.getX() - 1,
                origin.getY() + rotatedSize.getY() - 1,
                origin.getZ() + rotatedSize.getZ() - 1
        );

        return new LibrarianPlacement(origin, rotation, box);
    }

    private boolean isValidLibrarianFacade(int centerX, int centerZ, Direction face) {
        Direction inward = face.getOpposite();
        Direction right = face.rotateYClockwise();

        // Need a full 7-wide shelf wall strip to cut into.
        for (int w = -LIBRARIAN_AREA_HALF_WIDTH; w <= LIBRARIAN_AREA_HALF_WIDTH; w++) {
            int wallX = centerX + right.getOffsetX() * w;
            int wallZ = centerZ + right.getOffsetZ() * w;
            if (isOpen(wallX, wallZ)) {
                return false;
            }
        }

        // Need the whole 5-deep body to be embedded inside the wall mass.
        for (int d = 1; d < LIBRARIAN_AREA_DEPTH; d++) {
            for (int w = -LIBRARIAN_AREA_HALF_WIDTH; w <= LIBRARIAN_AREA_HALF_WIDTH; w++) {
                int bodyX = centerX + right.getOffsetX() * w + inward.getOffsetX() * d;
                int bodyZ = centerZ + right.getOffsetZ() * w + inward.getOffsetZ() * d;
                if (isOpen(bodyX, bodyZ)) {
                    return false;
                }
            }
        }

        // Need open walk space in front so the alcove actually opens into the library.
        for (int d = 1; d <= LIBRARIAN_AREA_FRONT_CLEARANCE; d++) {
            for (int w = -2; w <= 2; w++) {
                int frontX = centerX + right.getOffsetX() * w + face.getOffsetX() * d;
                int frontZ = centerZ + right.getOffsetZ() * w + face.getOffsetZ() * d;
                if (!isOpen(frontX, frontZ)) {
                    return false;
                }
            }
        }

        return true;
    }

    private static Direction horizontalByIndex(int index) {
        return switch (Math.floorMod(index, 4)) {
            case 0 -> Direction.NORTH;
            case 1 -> Direction.EAST;
            case 2 -> Direction.SOUTH;
            default -> Direction.WEST;
        };
    }

    private static BlockRotation rotationForFront(Direction face) {
        return switch (face) {
            case SOUTH -> BlockRotation.NONE;
            case WEST -> BlockRotation.CLOCKWISE_90;
            case NORTH -> BlockRotation.CLOCKWISE_180;
            case EAST -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }

    private static Vec3i rotateLocalPos(int x, int z, BlockRotation rotation, int sizeX, int sizeZ) {
        return switch (rotation) {
            case NONE -> new Vec3i(x, 0, z);
            case CLOCKWISE_90 -> new Vec3i(sizeZ - 1 - z, 0, x);
            case CLOCKWISE_180 -> new Vec3i(sizeX - 1 - x, 0, sizeZ - 1 - z);
            case COUNTERCLOCKWISE_90 -> new Vec3i(z, 0, sizeX - 1 - x);
        };
    }

    private static Vec3i rotatedSize(int sizeX, int sizeY, int sizeZ, BlockRotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> new Vec3i(sizeZ, sizeY, sizeX);
            default -> new Vec3i(sizeX, sizeY, sizeZ);
        };
    }

    /* ===================== layout / maze ===================== */

    private OpenInfo computeOpenInfo(int x, int z) {
        int cx = Math.floorDiv(x, CELL);
        int cz = Math.floorDiv(z, CELL);

        boolean inRoom = false;
        for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) {
            if (inRoom(cx + ox, cz + oz, x, z)) { inRoom = true; break; }
        }

        boolean inCorr =
                inCorridorFromCell(cx, cz, x, z) ||
                        inCorridorFromCell(cx - 1, cz, x, z) ||
                        inCorridorFromCell(cx, cz + 1, x, z);

        boolean open = inRoom || inCorr;
        return new OpenInfo(open, inRoom, inCorr);
    }

    private boolean inRoom(int cx, int cz, int x, int z) {
        long h = cellHash(cx, cz);

        // more room variety
        int r = (int)((h >>> 12) & 255);
        boolean big = (r == 0);           // 1/256 big
        boolean room = big || (r < 28);   // ~11% normal rooms
        if (!room) return false;

        int centerX = cx * CELL + CELL / 2;
        int centerZ = cz * CELL + CELL / 2;

        // offset to reduce grid feel
        int offX = (int)((h >>> 20) & 7) - 3;
        int offZ = (int)((h >>> 23) & 7) - 3;

        int halfX = big ? 14 + (int)((h >>> 28) & 7) : 5 + (int)((h >>> 28) & 7);
        int halfZ = big ? 14 + (int)((h >>> 32) & 7) : 5 + (int)((h >>> 32) & 7);

        int rx0 = centerX + offX - halfX;
        int rx1 = centerX + offX + halfX;
        int rz0 = centerZ + offZ - halfZ;
        int rz1 = centerZ + offZ + halfZ;

        return (x >= rx0 && x <= rx1 && z >= rz0 && z <= rz1);
    }

    private boolean inCorridorFromCell(int cx, int cz, int x, int z) {
        long h = cellHash(cx, cz);

        // connect N or E, plus occasional loop extra
        boolean primaryNorth = ((h & 1L) == 0L);
        boolean extra = (((h >>> 4) & 7L) == 0L); // ~1/8

        boolean openN = primaryNorth || extra;
        boolean openE = (!primaryNorth) || extra;

        int centerX = cx * CELL + CELL / 2;
        int centerZ = cz * CELL + CELL / 2;

        // corridor width variety 2..5
        int wHere = 2 + (int)((h >>> 1) & 3);

        if (openN) {
            int nx = centerX;
            int nz = centerZ - CELL;
            int wN = edgeWidth(cx, cz, cx, cz - 1, wHere);
            if (inRectCorridor(x, z, centerX, centerZ, nx, nz, wN)) return true;
        }

        if (openE) {
            int ex = centerX + CELL;
            int ez = centerZ;
            int wE = edgeWidth(cx, cz, cx + 1, cz, wHere);
            if (inRectCorridor(x, z, centerX, centerZ, ex, ez, wE)) return true;
        }

        return false;
    }

    private int edgeWidth(int ax, int az, int bx, int bz, int wA) {
        int wB = 2 + (int)((cellHash(bx, bz) >>> 1) & 3);
        return Math.max(wA, wB);
    }

    private static boolean inRectCorridor(int x, int z, int x0, int z0, int x1, int z1, int w) {
        int half = w / 2;

        int minX = Math.min(x0, x1) - half;
        int maxX = Math.max(x0, x1) + half;
        int minZ = Math.min(z0, z1) - half;
        int maxZ = Math.max(z0, z1) + half;

        boolean vertical = (x0 == x1);
        if (vertical) {
            minX = x0 - half;
            maxX = x0 + half;
        } else {
            minZ = z0 - half;
            maxZ = z0 + half;
        }

        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /* ===================== materials ===================== */

    private BlockState pickFloor(int x, int z, OpenInfo me) {
        BlockState dark = Blocks.DARK_OAK_PLANKS.getDefaultState();
        BlockState spruce = Blocks.SPRUCE_PLANKS.getDefaultState();

        if (!me.open) return dark;

        // subtle structural beams
        if (Math.floorMod(x, 11) == 0) return Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.X);
        if (Math.floorMod(z, 11) == 0) return Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Z);

        if (me.inRoom) {
            int a = Math.floorMod(x, 4) < 2 ? 1 : 0;
            int b = Math.floorMod(z, 4) < 2 ? 1 : 0;
            return (a ^ b) == 1 ? spruce : dark;
        }

        // corridor accents
        if (Math.floorMod(x + z, 7) == 0) return spruce;
        return dark;
    }

    private BlockState pickFacadeBlock(int x, int y, int z, int floorY, int wallTopY, boolean inRoom, Direction face) {
        // corner strength
        boolean openE = isOpen(x + 1, z);
        boolean openW = isOpen(x - 1, z);
        boolean openS = isOpen(x, z + 1);
        boolean openN = isOpen(x, z - 1);
        int openCount = (openE ? 1 : 0) + (openW ? 1 : 0) + (openS ? 1 : 0) + (openN ? 1 : 0);
        if (openCount >= 2) {
            return Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Y);
        }

        // bay rhythm
        int period = inRoom ? 9 : 7;
        int along = (face == Direction.EAST || face == Direction.WEST) ? z : x;
        int bay = Math.floorMod(along, period);
        boolean pillar = (bay == 0) || (bay == period - 1);

        BlockState pillarState = Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Y);
        BlockState darkPlanks  = Blocks.DARK_OAK_PLANKS.getDefaultState();
        BlockState sprucePlanks= Blocks.SPRUCE_PLANKS.getDefaultState();

        int yRel = y - (floorY + 1); // 0..(WALL_HEIGHT-2)

        // base + top trim
        if (yRel == 0) return darkPlanks;
        if (y == wallTopY - 1) return darkPlanks;

        // pillars
        if (pillar) return pillarState;

        // frame beams (outside shelves)
        if (yRel == 3) return darkPlanks;

        // shelves: lowered + extended up (start low, extend 4 blocks up)
        int shelfMin = 1;   // starts near the bottom
        int shelfMax = 15;  // extended upward
        int beamAboveShelves = shelfMax + 1; // keep a trim line above shelves

        if (yRel == beamAboveShelves) return darkPlanks;

        if (yRel >= shelfMin && yRel <= shelfMax) {
            // ✅ ALWAYS shelves here (no random wood in the middle)
            return rareShelf(x, y, z);
        }

        // panels above/below shelves
        long r2 = scrambleId(seed, (int)(x * 73L + z * 97L + y * 193L));
        return ((r2 >>> 60) & 1L) == 0L ? darkPlanks : sprucePlanks;
    }

    private boolean isOpen(int x, int z) {
        return computeOpenInfo(x, z).open;
    }

    private BlockState rareShelf(int x, int y, int z) {
        // Only replaces ONE shelf occasionally
        long rr = scrambleId(seed, (int)(x * 911L ^ z * 3571L ^ y * 101L));

        // very rare
        if ((rr & 3091L) == 0L) return ModBlocks.SUSPICIOUS_BOOKSHELF.getDefaultState();

        // semi-rare
        if ((rr & 511L) == 0L) return ModBlocks.DABLOON_BOOKSHELF.getDefaultState();
        if ((rr & 911L) == 0L) return ModBlocks.STARRY_BOOKSHELF.getDefaultState();

        // default shelf = DREAM_BOOKSHELF
        return ModBlocks.DREAM_BOOKSHELF.getDefaultState();
    }

    /* ===================== hashing ===================== */

    private long cellHash(int cx, int cz) {
        int salt = (int)(cx * 7349L ^ cz * 9157L);
        return scrambleId(seed, salt);
    }

    private static long scrambleId(long seed, int salt) {
        long h = seed ^ (salt * 0x9E3779B97F4A7C15L);
        h ^= (h >>> 30); h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 27); h *= 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    private static long scrambleId(long seed, long salt) {
        long h = seed ^ (salt * 0x9E3779B97F4A7C15L);
        h ^= (h >>> 30); h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 27); h *= 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
}
