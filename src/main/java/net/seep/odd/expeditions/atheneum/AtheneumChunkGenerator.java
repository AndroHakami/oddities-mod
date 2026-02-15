// src/main/java/net/seep/odd/expeditions/atheneum/AtheneumChunkGenerator.java
package net.seep.odd.expeditions.atheneum;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PillarBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
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
 */
public final class AtheneumChunkGenerator extends ChunkGenerator {

    public static final MapCodec<AtheneumChunkGenerator> CONFIG_CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
                    Codec.LONG.optionalFieldOf("seed", 0L).forGetter(g -> g.seed)
            ).apply(instance, AtheneumChunkGenerator::new)
    );

    public static final Codec<AtheneumChunkGenerator> CODEC = CONFIG_CODEC.codec();

    private final long seed;

    // ======== constants ========
    private static final int BEDROCK_LAYERS = 3;
    private static final int WALL_HEIGHT = 20;
    private static final int BARRIER_LAYERS = 10;

    // Macro maze cell size
    private static final int CELL = 12;

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
    @Override public void buildSurface(ChunkRegion region, StructureAccessor sa, NoiseConfig nc, Chunk chunk) {}
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
        if ((rr & 8191L) == 0L) return ModBlocks.SUSPICIOUS_BOOKSHELF.getDefaultState();

        // semi-rare
        if ((rr & 511L) == 0L) return ModBlocks.DABLOON_BOOKSHELF.getDefaultState();

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
}
