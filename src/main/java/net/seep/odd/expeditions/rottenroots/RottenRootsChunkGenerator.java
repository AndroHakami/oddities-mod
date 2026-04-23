package net.seep.odd.expeditions.rottenroots;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PillarBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Void terrain + bedrock ceiling, with interconnected “Rotten Roots” webs.
 */
public final class RottenRootsChunkGenerator extends ChunkGenerator {

    public static final MapCodec<RottenRootsChunkGenerator> CONFIG_CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
                    Codec.LONG.optionalFieldOf("seed", 0L).forGetter(g -> g.seed),
                    Codec.FLOAT.optionalFieldOf("density", 1.0f).forGetter(g -> g.density)
            ).apply(instance, RottenRootsChunkGenerator::new)
    );

    public static final Codec<RottenRootsChunkGenerator> CODEC = CONFIG_CODEC.codec();

    private static final float DENSITY_FACTOR = 1.35f;

    private static final int NORMAL_MAX_THICK = 8;
    private static final int SUPPORT_MAX_THICK = 9;

    private static final int GLOW_SAP_CLUSTER_RARE_CHANCE = 4800;
    private static final int GLOW_SAP_CLUSTER_MIN = 3;
    private static final int GLOW_SAP_CLUSTER_MAX = 6;
    private static final int GLOW_SAP_CLUSTER_ATTEMPTS = 60;

    private static final float FROGLIGHT_TIP_CHANCE = 0.18f;
    private static final int FROGLIGHT_TIP_MIN_DIST = 18;
    private static final int FROGLIGHT_TIP_MAX_DIST = 44;
    private static final int FROGLIGHT_CLUSTER_MIN_RADIUS = 2;
    private static final int FROGLIGHT_CLUSTER_MAX_RADIUS = 3;

    private final long seed;
    private final float density;

    public RottenRootsChunkGenerator(BiomeSource biomeSource, long seed, float density) {
        super(biomeSource);
        this.seed = seed;
        this.density = Math.max(0.05f, density);
    }

    @Override
    protected Codec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    public ChunkGenerator withSeed(long seed) {
        return new RottenRootsChunkGenerator(this.biomeSource, seed, this.density);
    }

    @Override
    public void carve(ChunkRegion region, long seed, NoiseConfig noiseConfig, BiomeAccess biomeAccess,
                      StructureAccessor structureAccessor, Chunk chunk, GenerationStep.Carver carverStep) {
        // no carving
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Executor executor, Blender blender, NoiseConfig noiseConfig,
                                                  StructureAccessor structures, Chunk chunk) {
        paintBedrockCeiling(chunk);
        paintRoots(chunk);
        pruneDisconnectedBits(chunk);
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
        return new VerticalBlockSample(bottom, states);
    }

    @Override public void getDebugHudText(List<String> text, NoiseConfig nc, BlockPos pos) {}
    @Override public int getHeight(int x, int z, Heightmap.Type type, HeightLimitView limits, NoiseConfig noiseConfig) { return 0; }

    /* -------------------- painting -------------------- */

    private void paintBedrockCeiling(Chunk chunk) {
        final int topExclusive = chunk.getBottomY() + chunk.getHeight();
        final int yTop = topExclusive - 1;
        final int yTop2 = topExclusive - 2;

        final int x0 = chunk.getPos().getStartX();
        final int z0 = chunk.getPos().getStartZ();
        final BlockState bedrock = Blocks.BEDROCK.getDefaultState();

        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int x = x0 + dx;
            int z = z0 + dz;

            m.set(x, yTop, z);
            chunk.setBlockState(m, bedrock, false);

            m.set(x, yTop2, z);
            chunk.setBlockState(m, bedrock, false);
        }
    }

    private void paintRoots(Chunk chunk) {
        final int minX = chunk.getPos().getStartX();
        final int minZ = chunk.getPos().getStartZ();
        final int maxX = minX + 15;
        final int maxZ = minZ + 15;
        final int bottomY = chunk.getBottomY();
        final int topY = bottomY + chunk.getHeight() - 3;

        final int CELL = 40;
        final float densityMul = DENSITY_FACTOR * this.density;

        int cx0 = Math.floorDiv(minX, CELL) - 1;
        int cz0 = Math.floorDiv(minZ, CELL) - 1;
        int cx1 = Math.floorDiv(maxX, CELL) + 1;
        int cz1 = Math.floorDiv(maxZ, CELL) + 1;

        BlockPos.Mutable m = new BlockPos.Mutable();

        Map<Long, List<Anchor>> anchorMap = new HashMap<>();
        Map<Long, Anchor> repMap = new HashMap<>();

        // Pass 1: anchors
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                int cellId = (cx * 7349) ^ (cz * 9157);
                Random r = Random.create(scrambleId(seed, cellId));

                int baseAnchors = 4 + r.nextBetween(0, 2);
                int anchorsHere = Math.max(3, Math.round(baseAnchors * densityMul));

                List<Anchor> anchors = new ArrayList<>(anchorsHere);
                for (int i = 0; i < anchorsHere; i++) {
                    int ax = cx * CELL + r.nextBetween(0, CELL - 1);
                    int az = cz * CELL + r.nextBetween(0, CELL - 1);
                    int ay = r.nextBetween(26, 318);
                    anchors.add(new Anchor(ax, ay, az, cx, cz, i));
                }

                long key = cellKey(cx, cz);
                anchorMap.put(key, anchors);
                repMap.put(key, anchors.get(r.nextInt(anchors.size())));
            }
        }

        // Pass 2: local webs + forward bridgey roots
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                long key = cellKey(cx, cz);
                List<Anchor> anchors = anchorMap.get(key);
                if (anchors == null || anchors.isEmpty()) continue;

                int cellId = (cx * 7349) ^ (cz * 9157);
                Random r = Random.create(scrambleId(seed, cellId ^ 0x5F3759DF));

                for (Anchor a : anchors) {
                    Anchor nearest = findNearestAnchor(a, anchors, null);
                    if (nearest != null) {
                        connectAnchors(
                                chunk, m, a, nearest,
                                Math.min(SUPPORT_MAX_THICK, pickThickness(r) + 1),
                                pickLog(r),
                                minX, minZ, maxX, maxZ, bottomY, topY, r,
                                0.012, 0.010, 0.18
                        );
                    }

                    if (r.nextFloat() < 0.72f) {
                        Anchor secondNearest = findNearestAnchor(a, anchors, nearest);
                        if (secondNearest != null) {
                            connectAnchors(
                                    chunk, m, a, secondNearest,
                                    Math.min(SUPPORT_MAX_THICK, pickThickness(r)),
                                    pickLog(r),
                                    minX, minZ, maxX, maxZ, bottomY, topY, r,
                                    0.015, 0.012, 0.16
                            );
                        }
                    }

                    Anchor neighbor = findNearestNeighborAnchor(a, anchorMap, cx, cz);
                    if (neighbor != null) {
                        connectAnchors(
                                chunk, m, a, neighbor,
                                Math.min(SUPPORT_MAX_THICK, pickThickness(r) + 1),
                                pickLog(r),
                                minX, minZ, maxX, maxZ, bottomY, topY, r,
                                0.014, 0.012, 0.17
                        );
                    }

                    int forwardSpans = 1 + r.nextBetween(0, 1);
                    for (int i = 0; i < forwardSpans; i++) {
                        Anchor forward = findForwardAnchor(a, anchorMap, r);
                        if (forward != null) {
                            connectAnchors(
                                    chunk, m, a, forward,
                                    Math.min(SUPPORT_MAX_THICK, pickThickness(r) + 1),
                                    pickLog(r),
                                    minX, minZ, maxX, maxZ, bottomY, topY, r,
                                    0.016, 0.012, 0.14
                            );
                        }
                    }

                    // Extra horizontal-ish roots: flatter and angled variants.
                    int horizontalSpans = 1 + r.nextBetween(0, 1) + (r.nextFloat() < 0.45f ? 1 : 0);
                    for (int i = 0; i < horizontalSpans; i++) {
                        boolean preferFlat = r.nextFloat() < 0.55f;
                        Anchor horizontal = findHorizontalAnchor(a, anchorMap, r, preferFlat);
                        if (horizontal != null) {
                            int extraThickness = preferFlat
                                    ? r.nextBetween(0, 1)
                                    : r.nextBetween(0, 2);

                            connectAnchors(
                                    chunk, m, a, horizontal,
                                    Math.min(SUPPORT_MAX_THICK, pickThickness(r) + extraThickness),
                                    pickLog(r),
                                    minX, minZ, maxX, maxZ, bottomY, topY, r,
                                    preferFlat ? 0.010 : 0.013,
                                    preferFlat ? 0.006 : 0.010,
                                    preferFlat ? 0.20 : 0.16
                            );
                        }
                    }

                    // Rare brace accents only, but always tilted.
                    if (r.nextFloat() < 0.14f) {
                        boolean towardCeiling = r.nextFloat() < 0.55f;

                        int tx = a.x + signedBetween(r, 7, 18);
                        int tz = a.z + signedBetween(r, 6, 16);
                        int ty = towardCeiling
                                ? topY - r.nextBetween(2, 20)
                                : bottomY + 2 + r.nextBetween(0, 20);

                        int len = (int) Math.max(28, new Vec3d(tx - a.x, ty - a.y, tz - a.z).length()) + 18;

                        carveTargetedStrand(
                                chunk, m,
                                a.x, a.y, a.z,
                                tx, ty, tz,
                                len,
                                Math.min(SUPPORT_MAX_THICK, pickThickness(r) + 1),
                                pickLog(r),
                                minX, minZ, maxX, maxZ, bottomY, topY,
                                r,
                                0.010, 0.010, 0.22
                        );
                    }

                    if (r.nextFloat() < FROGLIGHT_TIP_CHANCE) {
                        carveFroglightTipStrand(
                                chunk, m, a,
                                Math.min(SUPPORT_MAX_THICK, pickThickness(r) + 1),
                                minX, minZ, maxX, maxZ, bottomY, topY,
                                r
                        );
                    }
                }
            }
        }

        // Pass 3: bigger rep-to-rep webs
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                Anchor a = repMap.get(cellKey(cx, cz));
                if (a == null) continue;

                int cellId = (cx * 7349) ^ (cz * 9157);
                Random r = Random.create(scrambleId(seed, cellId ^ 0x1234ABCD));

                connectRepresentativePair(chunk, m, a, repMap.get(cellKey(cx + 1, cz)), r, minX, minZ, maxX, maxZ, bottomY, topY);
                connectRepresentativePair(chunk, m, a, repMap.get(cellKey(cx, cz + 1)), r, minX, minZ, maxX, maxZ, bottomY, topY);

                if (r.nextFloat() < 0.75f) {
                    connectRepresentativePair(chunk, m, a, repMap.get(cellKey(cx + 1, cz + 1)), r, minX, minZ, maxX, maxZ, bottomY, topY);
                }
                if (r.nextFloat() < 0.55f) {
                    connectRepresentativePair(chunk, m, a, repMap.get(cellKey(cx - 1, cz + 1)), r, minX, minZ, maxX, maxZ, bottomY, topY);
                }

                // Macro flatter bridges too.
                if (r.nextFloat() < 0.70f) {
                    connectRepresentativePair(chunk, m, a, repMap.get(cellKey(cx + 2, cz)), r, minX, minZ, maxX, maxZ, bottomY, topY);
                }
                if (r.nextFloat() < 0.70f) {
                    connectRepresentativePair(chunk, m, a, repMap.get(cellKey(cx, cz + 2)), r, minX, minZ, maxX, maxZ, bottomY, topY);
                }
            }
        }
    }

    /* -------------------- cleanup -------------------- */

    private void pruneDisconnectedBits(Chunk chunk) {
        final int bottomY = chunk.getBottomY();
        final int height = chunk.getHeight();
        final int topY = bottomY + height - 1;

        boolean[] visited = new boolean[16 * height * 16];
        List<Component> components = new ArrayList<>();
        BlockPos.Mutable m = new BlockPos.Mutable();

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                for (int y = bottomY; y <= topY; y++) {
                    int index = packIndex(dx, y - bottomY, dz);
                    if (visited[index]) continue;

                    m.set(chunk.getPos().getStartX() + dx, y, chunk.getPos().getStartZ() + dz);
                    if (!isRootMaterial(chunk.getBlockState(m))) continue;

                    Component comp = floodComponent(chunk, dx, y - bottomY, dz, bottomY, height, visited);
                    components.add(comp);
                }
            }
        }

        if (components.isEmpty()) {
            return;
        }

        int fallbackKeep = -1;
        int bestSize = -1;

        for (int i = 0; i < components.size(); i++) {
            Component c = components.get(i);
            if (c.blocks.size() > bestSize) {
                bestSize = c.blocks.size();
                fallbackKeep = i;
            }
        }

        boolean anyStrongKeep = false;
        for (Component c : components) {
            if (shouldStrongKeep(c)) {
                anyStrongKeep = true;
                break;
            }
        }

        for (int i = 0; i < components.size(); i++) {
            Component comp = components.get(i);

            boolean keep = shouldStrongKeep(comp);
            if (!anyStrongKeep && i == fallbackKeep) {
                keep = true;
            }

            if (keep) continue;

            for (int packed : comp.blocks) {
                int dx = unpackDx(packed);
                int dy = unpackDy(packed);
                int dz = unpackDz(packed);

                m.set(chunk.getPos().getStartX() + dx, bottomY + dy, chunk.getPos().getStartZ() + dz);
                chunk.setBlockState(m, Blocks.AIR.getDefaultState(), false);
            }
        }
    }

    private boolean shouldStrongKeep(Component c) {
        int borderFaces = Integer.bitCount(c.borderMask);
        int rangeX = c.maxDx - c.minDx + 1;
        int rangeY = c.maxDy - c.minDy + 1;
        int rangeZ = c.maxDz - c.minDz + 1;

        boolean sliceLike =
                (rangeX <= 4 && rangeY >= 8 && rangeZ >= 8) ||
                        (rangeZ <= 4 && rangeY >= 8 && rangeX >= 8) ||
                        (rangeY <= 4 && (rangeX >= 8 || rangeZ >= 8));

        if (c.touchesCeiling) return true;
        if (borderFaces >= 2) return true;

        if (borderFaces == 1 && sliceLike) return false;
        if (borderFaces == 1 && c.blocks.size() < 260) return false;

        if (borderFaces == 0) {
            return c.blocks.size() >= 420 && !sliceLike;
        }

        return c.blocks.size() >= 700 && !sliceLike;
    }

    private Component floodComponent(Chunk chunk, int startDx, int startDy, int startDz, int bottomY, int height, boolean[] visited) {
        int[] queue = new int[16 * height * 16];
        int qHead = 0;
        int qTail = 0;

        Component comp = new Component();
        BlockPos.Mutable m = new BlockPos.Mutable();

        int start = packIndex(startDx, startDy, startDz);
        visited[start] = true;
        queue[qTail++] = start;

        while (qHead < qTail) {
            int cur = queue[qHead++];
            comp.blocks.add(cur);

            int dx = unpackDx(cur);
            int dy = unpackDy(cur);
            int dz = unpackDz(cur);

            comp.minDx = Math.min(comp.minDx, dx);
            comp.maxDx = Math.max(comp.maxDx, dx);
            comp.minDy = Math.min(comp.minDy, dy);
            comp.maxDy = Math.max(comp.maxDy, dy);
            comp.minDz = Math.min(comp.minDz, dz);
            comp.maxDz = Math.max(comp.maxDz, dz);

            if (dx == 0)  comp.borderMask |= 1;
            if (dx == 15) comp.borderMask |= 2;
            if (dz == 0)  comp.borderMask |= 4;
            if (dz == 15) comp.borderMask |= 8;

            if (bottomY + dy >= bottomY + height - 4) {
                comp.touchesCeiling = true;
            }

            for (int dir = 0; dir < 6; dir++) {
                int nx = dx;
                int ny = dy;
                int nz = dz;

                switch (dir) {
                    case 0 -> nx++;
                    case 1 -> nx--;
                    case 2 -> ny++;
                    case 3 -> ny--;
                    case 4 -> nz++;
                    case 5 -> nz--;
                }

                if (nx < 0 || nx >= 16 || nz < 0 || nz >= 16 || ny < 0 || ny >= height) continue;

                int ni = packIndex(nx, ny, nz);
                if (visited[ni]) continue;

                m.set(chunk.getPos().getStartX() + nx, bottomY + ny, chunk.getPos().getStartZ() + nz);
                if (!isRootMaterial(chunk.getBlockState(m))) continue;

                visited[ni] = true;
                queue[qTail++] = ni;
            }
        }

        return comp;
    }

    private static boolean isRootMaterial(BlockState state) {
        return state.isOf(ModBlocks.BOGGY_LOG)
                || state.isOf(ModBlocks.BOGGY_WOOD)
                || state.isOf(ModBlocks.GLOW_SAP)
                || state.isOf(Blocks.OCHRE_FROGLIGHT);
    }

    private static int packIndex(int dx, int dy, int dz) {
        return dx + (dz * 16) + (dy * 16 * 16);
    }

    private static int unpackDx(int packed) {
        return packed & 15;
    }

    private static int unpackDz(int packed) {
        return (packed >> 4) & 15;
    }

    private static int unpackDy(int packed) {
        return packed >> 8;
    }

    private static final class Component {
        final List<Integer> blocks = new ArrayList<>();
        boolean touchesCeiling = false;
        int borderMask = 0;

        int minDx = Integer.MAX_VALUE;
        int maxDx = Integer.MIN_VALUE;
        int minDy = Integer.MAX_VALUE;
        int maxDy = Integer.MIN_VALUE;
        int minDz = Integer.MAX_VALUE;
        int maxDz = Integer.MIN_VALUE;
    }

    /* -------------------- helpers -------------------- */

    private static long scrambleId(long seed, int salt) {
        long h = seed ^ (salt * 0x9E3779B97F4A7C15L);
        h ^= (h >>> 30); h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 27); h *= 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    private static long cellKey(int cx, int cz) {
        return (((long) cx) << 32) ^ (cz & 0xffffffffL);
    }

    private static class Anchor {
        final int x, y, z, cellX, cellZ, id;
        Anchor(int x, int y, int z, int cellX, int cellZ, int id) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.cellX = cellX;
            this.cellZ = cellZ;
            this.id = id;
        }
    }

    private static Anchor findNearestAnchor(Anchor from, List<Anchor> list, Anchor exclude) {
        Anchor best = null;
        double bestDist = Double.MAX_VALUE;

        for (Anchor a : list) {
            if (a == from) continue;
            if (a == exclude) continue;

            double dx = a.x - from.x;
            double dy = a.y - from.y;
            double dz = a.z - from.z;
            double d2 = dx * dx + dy * dy + dz * dz;

            if (d2 < bestDist) {
                bestDist = d2;
                best = a;
            }
        }

        return best;
    }

    private static Anchor findNearestNeighborAnchor(Anchor from, Map<Long, List<Anchor>> anchorMap, int cx, int cz) {
        Anchor best = null;
        double bestDist = Double.MAX_VALUE;

        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                if (ox == 0 && oz == 0) continue;

                List<Anchor> list = anchorMap.get(cellKey(cx + ox, cz + oz));
                if (list == null) continue;

                for (Anchor a : list) {
                    double dx = a.x - from.x;
                    double dy = a.y - from.y;
                    double dz = a.z - from.z;
                    double d2 = dx * dx + dy * dy + dz * dz;

                    if (d2 < bestDist) {
                        bestDist = d2;
                        best = a;
                    }
                }
            }
        }

        return best;
    }

    private static Anchor findForwardAnchor(Anchor from, Map<Long, List<Anchor>> anchorMap, Random r) {
        double yaw = r.nextDouble() * Math.PI * 2.0;
        double fx = Math.cos(yaw);
        double fz = Math.sin(yaw);

        Anchor best = null;
        double bestScore = -1_000_000.0;

        for (int ox = -2; ox <= 2; ox++) {
            for (int oz = -2; oz <= 2; oz++) {
                List<Anchor> list = anchorMap.get(cellKey(from.cellX + ox, from.cellZ + oz));
                if (list == null) continue;

                for (Anchor a : list) {
                    if (a == from) continue;

                    double dx = a.x - from.x;
                    double dy = a.y - from.y;
                    double dz = a.z - from.z;

                    double horiz = Math.sqrt(dx * dx + dz * dz);
                    if (horiz < 18.0 || horiz > 110.0) continue;

                    double forwardness = (dx * fx + dz * fz) / horiz;
                    if (forwardness < 0.35) continue;

                    double score = (forwardness * 50.0)
                            - Math.abs(horiz - 48.0) * 0.35
                            - Math.abs(dy) * 0.28;

                    if (score > bestScore) {
                        bestScore = score;
                        best = a;
                    }
                }
            }
        }

        return best;
    }

    private static Anchor findHorizontalAnchor(Anchor from, Map<Long, List<Anchor>> anchorMap, Random r, boolean preferFlat) {
        double yaw = r.nextDouble() * Math.PI * 2.0;
        double fx = Math.cos(yaw);
        double fz = Math.sin(yaw);

        double preferredDist = preferFlat
                ? 58.0 + r.nextDouble() * 32.0
                : 44.0 + r.nextDouble() * 42.0;

        double targetSlope = preferFlat
                ? 0.02 + r.nextDouble() * 0.07
                : 0.10 + r.nextDouble() * 0.22;

        double minSlope = preferFlat ? 0.0 : 0.04;
        double maxSlope = preferFlat ? 0.14 : 0.40;
        double minForwardness = preferFlat ? 0.20 : 0.08;

        Anchor best = null;
        double bestScore = -1_000_000.0;

        for (int ox = -3; ox <= 3; ox++) {
            for (int oz = -3; oz <= 3; oz++) {
                List<Anchor> list = anchorMap.get(cellKey(from.cellX + ox, from.cellZ + oz));
                if (list == null) continue;

                for (Anchor a : list) {
                    if (a == from) continue;

                    double dx = a.x - from.x;
                    double dy = a.y - from.y;
                    double dz = a.z - from.z;

                    double horiz = Math.sqrt(dx * dx + dz * dz);
                    if (horiz < 20.0 || horiz > 150.0) continue;

                    double absDy = Math.abs(dy);
                    double slope = absDy / horiz;
                    if (slope < minSlope || slope > maxSlope) continue;

                    double forwardness = (dx * fx + dz * fz) / horiz;
                    if (forwardness < minForwardness) continue;

                    double score = (forwardness * 42.0)
                            - Math.abs(horiz - preferredDist) * 0.22
                            - Math.abs(slope - targetSlope) * 90.0
                            - absDy * (preferFlat ? 0.45 : 0.18);

                    if (score > bestScore) {
                        bestScore = score;
                        best = a;
                    }
                }
            }
        }

        return best;
    }

    private static void connectRepresentativePair(
            Chunk chunk, BlockPos.Mutable m,
            Anchor a, Anchor b,
            Random r,
            int minX, int minZ, int maxX, int maxZ, int bottomY, int topY
    ) {
        if (a == null || b == null) return;

        double dist = new Vec3d(b.x - a.x, b.y - a.y, b.z - a.z).length();
        if (dist < 18.0) return;

        connectAnchors(
                chunk, m, a, b,
                Math.min(SUPPORT_MAX_THICK, pickThickness(r) + 1),
                pickLog(r),
                minX, minZ, maxX, maxZ, bottomY, topY,
                r,
                0.015, 0.012, 0.15
        );
    }

    private static void connectAnchors(
            Chunk chunk, BlockPos.Mutable m,
            Anchor a, Anchor b,
            int thick, BlockState log,
            int minX, int minZ, int maxX, int maxZ, int bottomY, int topY,
            Random r,
            double wobbleXZ,
            double wobbleY,
            double spring
    ) {
        int dx = b.x - a.x;
        int dz = b.z - a.z;

        // Near-vertical links get a forced bend so they stop looking like towers.
        if (dx * dx + dz * dz < 64) {
            int midX = (a.x + b.x) / 2 + signedBetween(r, 6, 14);
            int midY = (a.y + b.y) / 2;
            int midZ = (a.z + b.z) / 2 + signedBetween(r, 5, 12);

            int len1 = (int) Math.max(18, new Vec3d(midX - a.x, midY - a.y, midZ - a.z).length()) + 12;
            int len2 = (int) Math.max(18, new Vec3d(b.x - midX, b.y - midY, b.z - midZ).length()) + 12;

            carveTargetedStrand(
                    chunk, m,
                    a.x, a.y, a.z,
                    midX, midY, midZ,
                    len1, thick, log,
                    minX, minZ, maxX, maxZ, bottomY, topY,
                    r, wobbleXZ, wobbleY, spring
            );

            carveTargetedStrand(
                    chunk, m,
                    midX, midY, midZ,
                    b.x, b.y, b.z,
                    len2, thick, log,
                    minX, minZ, maxX, maxZ, bottomY, topY,
                    r, wobbleXZ, wobbleY, spring
            );
            return;
        }

        int len = (int) Math.max(26, new Vec3d(dx, b.y - a.y, dz).length()) + 18;

        carveTargetedStrand(
                chunk, m,
                a.x, a.y, a.z,
                b.x, b.y, b.z,
                len, thick, log,
                minX, minZ, maxX, maxZ, bottomY, topY,
                r,
                wobbleXZ, wobbleY, spring
        );
    }

    private static int signedBetween(Random r, int min, int max) {
        int v = r.nextBetween(min, max);
        return r.nextBoolean() ? v : -v;
    }

    private static BlockState pickLog(Random r) {
        return ModBlocks.BOGGY_LOG.getDefaultState();
    }

    private static int pickThickness(Random r) {
        float f = r.nextFloat();
        if (f < 0.05f) return 2;
        if (f < 0.22f) return 3;
        if (f < 0.62f) return 4;
        return 5;
    }

    private static Direction.Axis dominantAxis(Vec3d v) {
        double ax = Math.abs(v.x), ay = Math.abs(v.y), az = Math.abs(v.z);
        if (ax >= ay && ax >= az) return Direction.Axis.X;
        if (az >= ax && az >= ay) return Direction.Axis.Z;
        return Direction.Axis.Y;
    }

    private static void carveTargetedStrand(
            Chunk chunk, BlockPos.Mutable m,
            int sx, int sy, int sz,
            int tx, int ty, int tz,
            int len, int thick, BlockState log,
            int minX, int minZ, int maxX, int maxZ, int bottomY, int topY,
            Random r,
            double wobbleXZ,
            double wobbleY,
            double spring
    ) {
        carveTargetedStrand(
                chunk, m,
                sx, sy, sz,
                tx, ty, tz,
                len, thick, log,
                minX, minZ, maxX, maxZ, bottomY, topY,
                r,
                wobbleXZ, wobbleY, spring,
                false
        );
    }

    private static void carveTargetedStrand(
            Chunk chunk, BlockPos.Mutable m,
            int sx, int sy, int sz,
            int tx, int ty, int tz,
            int len, int thick, BlockState log,
            int minX, int minZ, int maxX, int maxZ, int bottomY, int topY,
            Random r,
            double wobbleXZ,
            double wobbleY,
            double spring,
            boolean decorateTipWithFroglight
    ) {
        Vec3d p = new Vec3d(sx + 0.5, sy + 0.5, sz + 0.5);
        Vec3d target = new Vec3d(tx + 0.5, ty + 0.5, tz + 0.5);
        Vec3d d = target.subtract(p).normalize();

        boolean placedAny = false;
        int lastX = sx;
        int lastY = sy;
        int lastZ = sz;
        Vec3d lastDir = d;

        for (int i = 0; i < len; i++) {
            Vec3d toTarget = target.subtract(p);
            double remaining = toTarget.length();
            if (remaining < 2.0) break;

            Vec3d targetDir = toTarget.normalize();
            d = d.multiply(1.0 - spring).add(targetDir.multiply(spring));
            d = d.add(
                    (r.nextDouble() - 0.5) * wobbleXZ,
                    (r.nextDouble() - 0.5) * wobbleY,
                    (r.nextDouble() - 0.5) * wobbleXZ
            ).normalize();

            p = p.add(d.multiply(1.10));

            int x = MathHelper.floor(p.x);
            int y = MathHelper.floor(p.y);
            int z = MathHelper.floor(p.z);

            if (y < bottomY + 1 || y >= topY) continue;
            if (x < minX - thick || x > maxX + thick || z < minZ - thick || z > maxZ + thick) continue;

            int effectiveThickness = computeTaperedThickness(
                    i, len, remaining, thick, x, z, minX, minZ, maxX, maxZ
            );

            if (effectiveThickness <= 0) continue;

            paintTubeAt(chunk, m, x, y, z, d, effectiveThickness, log, minX, minZ, maxX, maxZ, r);
            placedAny = true;
            lastX = x;
            lastY = y;
            lastZ = z;
            lastDir = d;
        }

        if (decorateTipWithFroglight && placedAny) {
            placeFroglightTipCluster(
                    chunk, m,
                    lastX, lastY, lastZ,
                    lastDir,
                    Math.max(2, thick),
                    minX, minZ, maxX, maxZ, bottomY, topY,
                    r
            );
        }
    }

    private static int computeTaperedThickness(
            int step, int totalSteps, double remainingDistance, int baseThickness,
            int x, int z, int minX, int minZ, int maxX, int maxZ
    ) {
        int tipSteps = Math.max(4, baseThickness * 3 + 3);

        double startScale = MathHelper.clamp((step + 1) / (double) tipSteps, 0.0, 1.0);
        double endScale = MathHelper.clamp(remainingDistance / (baseThickness * 2.5 + 3.0), 0.0, 1.0);

        startScale = Math.sqrt(startScale);
        endScale = Math.sqrt(endScale);

        double scale = Math.min(startScale, endScale);

        int borderDist = Math.min(
                Math.min(x - minX, maxX - x),
                Math.min(z - minZ, maxZ - z)
        );

        int borderTip = Math.max(3, baseThickness * 2 + 2);
        if (borderDist < borderTip) {
            double borderScale = 0.45 + 0.55 * MathHelper.clamp(borderDist / (double) borderTip, 0.0, 1.0);
            scale = Math.min(scale, borderScale);
        }

        int effective = Math.max(1, MathHelper.floor(baseThickness * scale));
        return Math.min(effective, baseThickness);
    }

    private static void carveFroglightTipStrand(
            Chunk chunk, BlockPos.Mutable m, Anchor start,
            int thick,
            int minX, int minZ, int maxX, int maxZ, int bottomY, int topY,
            Random r
    ) {
        double yaw = r.nextDouble() * Math.PI * 2.0;
        int distance = r.nextBetween(FROGLIGHT_TIP_MIN_DIST, FROGLIGHT_TIP_MAX_DIST);

        int tx = start.x + MathHelper.floor(Math.cos(yaw) * distance);
        int tz = start.z + MathHelper.floor(Math.sin(yaw) * distance);
        int ty = MathHelper.clamp(start.y + signedBetween(r, 4, 18), bottomY + 6, topY - 6);

        int len = (int) Math.max(20, new Vec3d(tx - start.x, ty - start.y, tz - start.z).length()) + 12;

        carveTargetedStrand(
                chunk, m,
                start.x, start.y, start.z,
                tx, ty, tz,
                len,
                thick,
                pickLog(r),
                minX, minZ, maxX, maxZ, bottomY, topY,
                r,
                0.012, 0.010, 0.16,
                true
        );
    }

    private static void placeFroglightTipCluster(
            Chunk chunk, BlockPos.Mutable m,
            int tipX, int tipY, int tipZ,
            Vec3d dir,
            int thick,
            int minX, int minZ, int maxX, int maxZ, int bottomY, int topY,
            Random r
    ) {
        Vec3d axis = dir.lengthSquared() > 1.0E-6 ? dir.normalize() : new Vec3d(0.0, 1.0, 0.0);
        Vec3d tangentA = choosePerpendicular(axis);
        Vec3d tangentB = axis.crossProduct(tangentA).normalize();

        int radius = MathHelper.clamp(1 + (thick / 2) + r.nextBetween(0, 1), FROGLIGHT_CLUSTER_MIN_RADIUS, FROGLIGHT_CLUSTER_MAX_RADIUS);
        double axialRadius = radius + 0.70 + r.nextDouble() * 0.70;

        Vec3d tip = new Vec3d(tipX + 0.5, tipY + 0.5, tipZ + 0.5);
        Vec3d center = tip.add(axis.multiply(0.65 + radius * 0.55));

        Direction.Axis dominant = dominantAxis(axis);
        BlockState coreOriented = ModBlocks.BOGGY_LOG.getDefaultState().with(PillarBlock.AXIS, dominant);
        BlockState shellOriented = ModBlocks.BOGGY_WOOD.getDefaultState().with(PillarBlock.AXIS, dominant);
        BlockState froglight = Blocks.OCHRE_FROGLIGHT.getDefaultState();

        int ix0 = Math.max(minX, MathHelper.floor(center.x - radius - 2));
        int ix1 = Math.min(maxX, MathHelper.floor(center.x + radius + 2));
        int iy0 = Math.max(bottomY + 1, MathHelper.floor(center.y - axialRadius - 2));
        int iy1 = Math.min(topY - 1, MathHelper.floor(center.y + axialRadius + 2));
        int iz0 = Math.max(minZ, MathHelper.floor(center.z - radius - 2));
        int iz1 = Math.min(maxZ, MathHelper.floor(center.z + radius + 2));

        for (int px = ix0; px <= ix1; px++) {
            for (int py = iy0; py <= iy1; py++) {
                for (int pz = iz0; pz <= iz1; pz++) {
                    Vec3d rel = new Vec3d(px + 0.5 - center.x, py + 0.5 - center.y, pz + 0.5 - center.z);
                    double axial = rel.dotProduct(axis);
                    Vec3d radialVec = rel.subtract(axis.multiply(axial));
                    double radial = radialVec.length();
                    double radialNorm = (radial * radial) / (radius * radius);
                    double axialNorm = (axial * axial) / (axialRadius * axialRadius);
                    double shape = radialNorm + axialNorm;

                    if (shape > 1.0) continue;
                    if (shape > 0.86 && r.nextFloat() < 0.28f) continue;

                    m.set(px, py, pz);
                    BlockState existing = chunk.getBlockState(m);
                    if (!canReplaceForFroglightTip(existing)) continue;

                    double frontness = MathHelper.clamp((axial / axialRadius + 1.0) * 0.5, 0.0, 1.0);
                    double ribness = 0.0;
                    if (radial > 1.0E-6) {
                        Vec3d radialDir = radialVec.multiply(1.0 / radial);
                        ribness = Math.max(
                                Math.abs(radialDir.dotProduct(tangentA)),
                                Math.abs(radialDir.dotProduct(tangentB))
                        );
                    }

                    boolean backShell = shape > 0.58 && frontness < 0.76;
                    boolean clampRib = shape > 0.48 && frontness > 0.30 && ribness > 0.80;
                    boolean woodyCore = shape < 0.24 && frontness < 0.44;

                    if (woodyCore || backShell || clampRib) {
                        chunk.setBlockState(m, shape < 0.20 ? coreOriented : shellOriented, false);
                        continue;
                    }

                    boolean frontGlow = frontness > 0.42;
                    if (shape < 0.40) {
                        chunk.setBlockState(m, frontGlow ? froglight : shellOriented, false);
                    } else {
                        double froglightChance = 0.20 + (frontness * 0.62);
                        chunk.setBlockState(m, r.nextDouble() < froglightChance ? froglight : shellOriented, false);
                    }
                }
            }
        }

        Vec3d stemBack = tip.subtract(axis.multiply(Math.max(0.8, thick * 0.28)));
        Vec3d stemFront = center.subtract(axis.multiply(axialRadius * 0.42));
        placeWoodLine(
                chunk, m,
                stemBack, stemFront,
                Math.max(1, thick - 2),
                Math.max(1, radius - 1),
                dominant,
                minX, minZ, maxX, maxZ, bottomY, topY
        );

        placeWoodBlob(
                chunk, m,
                center.subtract(axis.multiply(axialRadius * 0.48)),
                Math.max(1, radius - 1),
                dominant,
                minX, minZ, maxX, maxZ, bottomY, topY
        );

        Vec3d[] ribs = new Vec3d[] {
                tangentA,
                tangentA.multiply(-1.0),
                tangentB,
                tangentB.multiply(-1.0)
        };

        for (Vec3d ribDir : ribs) {
            Vec3d ribStart = center.subtract(axis.multiply(axialRadius * 0.28)).add(ribDir.multiply(radius * 0.15));
            Vec3d ribMid = center.add(ribDir.multiply(radius * 0.48)).add(axis.multiply(axialRadius * 0.02));
            Vec3d ribEnd = center.add(ribDir.multiply(radius * 0.92)).add(axis.multiply(axialRadius * (0.10 + r.nextDouble() * 0.10)));

            placeWoodLine(
                    chunk, m,
                    ribStart, ribMid,
                    Math.max(1, radius - 1),
                    1,
                    dominant,
                    minX, minZ, maxX, maxZ, bottomY, topY
            );
            placeWoodLine(
                    chunk, m,
                    ribMid, ribEnd,
                    1,
                    1,
                    dominant,
                    minX, minZ, maxX, maxZ, bottomY, topY
            );
        }
    }

    private static Vec3d choosePerpendicular(Vec3d axis) {
        Vec3d ref = Math.abs(axis.y) < 0.85
                ? new Vec3d(0.0, 1.0, 0.0)
                : new Vec3d(1.0, 0.0, 0.0);

        Vec3d perpendicular = axis.crossProduct(ref);
        if (perpendicular.lengthSquared() < 1.0E-6) {
            perpendicular = new Vec3d(0.0, 0.0, 1.0);
        }
        return perpendicular.normalize();
    }

    private static void placeWoodLine(
            Chunk chunk, BlockPos.Mutable m,
            Vec3d from, Vec3d to,
            int startRadius, int endRadius,
            Direction.Axis axis,
            int minX, int minZ, int maxX, int maxZ, int bottomY, int topY
    ) {
        double distance = from.distanceTo(to);
        int steps = Math.max(2, MathHelper.ceil(distance * 2.0));

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3d pos = from.lerp(to, t);
            int radius = Math.max(1, MathHelper.floor(MathHelper.lerp((float) t, startRadius, endRadius)));
            placeWoodBlob(chunk, m, pos, radius, axis, minX, minZ, maxX, maxZ, bottomY, topY);
        }
    }

    private static void placeWoodBlob(
            Chunk chunk, BlockPos.Mutable m,
            Vec3d center,
            int radius,
            Direction.Axis axis,
            int minX, int minZ, int maxX, int maxZ, int bottomY, int topY
    ) {
        int outerR2 = radius * radius;
        int coreR = Math.max(1, radius - 1);
        int coreR2 = coreR * coreR;

        BlockState coreOriented = ModBlocks.BOGGY_LOG.getDefaultState().with(PillarBlock.AXIS, axis);
        BlockState shellOriented = ModBlocks.BOGGY_WOOD.getDefaultState().with(PillarBlock.AXIS, axis);

        int cx = MathHelper.floor(center.x);
        int cy = MathHelper.floor(center.y);
        int cz = MathHelper.floor(center.z);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int dist2 = dx * dx + dy * dy + dz * dz;
                    if (dist2 > outerR2) continue;

                    int px = cx + dx;
                    int py = cy + dy;
                    int pz = cz + dz;

                    if (px < minX || px > maxX || pz < minZ || pz > maxZ) continue;
                    if (py < bottomY + 1 || py >= topY) continue;

                    m.set(px, py, pz);
                    BlockState existing = chunk.getBlockState(m);
                    if (!canReplaceForFroglightTip(existing)) continue;

                    chunk.setBlockState(m, dist2 <= coreR2 ? coreOriented : shellOriented, false);
                }
            }
        }
    }

    private static boolean canReplaceForFroglightTip(BlockState state) {
        return state.isAir()
                || isRootMaterial(state)
                || state.isOf(Blocks.OCHRE_FROGLIGHT);
    }


    private static void paintTubeAt(
            Chunk chunk, BlockPos.Mutable m,
            int x, int y, int z, Vec3d dir, int thick, BlockState log,
            int minX, int minZ, int maxX, int maxZ, Random r
    ) {
        Direction.Axis axis = dominantAxis(dir);

        BlockState coreOriented = log.with(PillarBlock.AXIS, axis);
        BlockState shellOriented = ModBlocks.BOGGY_WOOD.getDefaultState().with(PillarBlock.AXIS, axis);

        int outerR2 = thick * thick;
        int coreR = Math.max(1, thick - 1);
        int coreR2 = coreR * coreR;

        for (int dx = -thick; dx <= thick; dx++) {
            for (int dy = -thick; dy <= thick; dy++) {
                for (int dz = -thick; dz <= thick; dz++) {
                    int dist2 = dx * dx + dy * dy + dz * dz;
                    if (dist2 > outerR2) continue;

                    int px = x + dx;
                    int py = y + dy;
                    int pz = z + dz;

                    if (px < minX || px > maxX || pz < minZ || pz > maxZ) continue;

                    m.set(px, py, pz);
                    if (!chunk.getBlockState(m).isAir()) continue;

                    boolean isShell = dist2 > coreR2;

                    if (!isShell) {
                        chunk.setBlockState(m, coreOriented, false);
                        continue;
                    }

                    boolean placeGlowSapCluster = (r.nextInt(GLOW_SAP_CLUSTER_RARE_CHANCE) == 0);

                    if (placeGlowSapCluster) {
                        placeGlowSapCluster(
                                chunk, m,
                                px, py, pz,
                                x, y, z,
                                coreR2, outerR2,
                                minX, minZ, maxX, maxZ,
                                r
                        );
                    } else {
                        chunk.setBlockState(m, shellOriented, false);
                    }
                }
            }
        }
    }

    private static void placeGlowSapCluster(
            Chunk chunk, BlockPos.Mutable m,
            int seedX, int seedY, int seedZ,
            int centerX, int centerY, int centerZ,
            int coreR2, int outerR2,
            int minX, int minZ, int maxX, int maxZ,
            Random r
    ) {
        int target = GLOW_SAP_CLUSTER_MIN + r.nextBetween(0, GLOW_SAP_CLUSTER_MAX - GLOW_SAP_CLUSTER_MIN);
        int placed = 0;

        if (tryPlaceGlowSap(chunk, m, seedX, seedY, seedZ)) {
            placed++;
        }

        int attempts = 0;
        while (placed < target && attempts < GLOW_SAP_CLUSTER_ATTEMPTS) {
            attempts++;

            int ox = r.nextBetween(-3, 3);
            int oy = r.nextBetween(-3, 3);
            int oz = r.nextBetween(-3, 3);

            if (Math.abs(ox) + Math.abs(oy) + Math.abs(oz) > 5) continue;

            int nx = seedX + ox;
            int ny = seedY + oy;
            int nz = seedZ + oz;

            if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) continue;

            int sdx = nx - centerX;
            int sdy = ny - centerY;
            int sdz = nz - centerZ;
            int nd2 = sdx * sdx + sdy * sdy + sdz * sdz;

            if (nd2 > outerR2) continue;
            if (nd2 <= coreR2) continue;

            if (tryPlaceGlowSap(chunk, m, nx, ny, nz)) {
                placed++;
            }
        }
    }

    private static boolean tryPlaceGlowSap(Chunk chunk, BlockPos.Mutable m, int x, int y, int z) {
        m.set(x, y, z);
        if (!chunk.getBlockState(m).isAir()) {
            return false;
        }
        chunk.setBlockState(m, ModBlocks.GLOW_SAP.getDefaultState(), false);
        return true;
    }
}