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
import net.minecraft.world.BlockView;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Void terrain + bedrock floor, baked “Rotten Roots” webs.
 */
public final class RottenRootsChunkGenerator extends ChunkGenerator {

    public static final MapCodec<RottenRootsChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
                    Codec.LONG.optionalFieldOf("seed", 0L).forGetter(g -> g.seed),
                    Codec.FLOAT.optionalFieldOf("density", 1.0f).forGetter(g -> g.density)
            ).apply(instance, RottenRootsChunkGenerator::new)
    );

    private final long seed;
    private final float density;

    public RottenRootsChunkGenerator(BiomeSource biomeSource, long seed, float density) {
        super(biomeSource);
        this.seed = seed;
        this.density = Math.max(0.05f, density);
    }

    @Override public Codec<? extends ChunkGenerator> getCodec() { return CODEC.codec(); }

    @Override
    public void carve(ChunkRegion chunkRegion, long seed, NoiseConfig noiseConfig, BiomeAccess biomeAccess, StructureAccessor structureAccessor, Chunk chunk, GenerationStep.Carver carverStep) {

    }

    public ChunkGenerator withSeed(long seed) { return new RottenRootsChunkGenerator(this.biomeSource, seed, this.density); }
     public void carve(ChunkRegion region, long seed, NoiseConfig nc, BiomeSource ba, StructureAccessor sa, Chunk chunk, GenerationStep.Carver step) {}

    @Override
    public CompletableFuture<Chunk> populateNoise(Executor executor, Blender blender, NoiseConfig noiseConfig, StructureAccessor structures, Chunk chunk) {
        paintBedrockFloor(chunk);
        paintRoots(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    @Override public int getSeaLevel() { return 0; }
    @Override public void buildSurface(ChunkRegion region, StructureAccessor sa, NoiseConfig nc, Chunk chunk) {}
    public void carve(ChunkRegion region, long seed, NoiseConfig nc, BlockPos origin) {}
    @Override public void populateEntities(ChunkRegion region) {}
    public int getSeaLevel(HeightLimitView world) { return 0; }
    @Override public int getMinimumY() { return -64; }
    @Override public int getWorldHeight() { return 384; }
    @Override public int getSpawnHeight(HeightLimitView world) { return 120; }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
        int bottom = world.getBottomY();
        int height = world.getHeight();
        BlockState[] states = new BlockState[height];
        for (int i = 0; i < height; i++) states[i] = Blocks.AIR.getDefaultState();
        states[0] = Blocks.BEDROCK.getDefaultState();
        return new VerticalBlockSample(bottom, states);
    }

    @Override public void getDebugHudText(List<String> text, NoiseConfig nc, BlockPos pos) {}
    @Override public int getHeight(int x, int z, Heightmap.Type type, HeightLimitView limits, NoiseConfig noiseConfig) { return 0; }

    /* -------------------- painting -------------------- */

    private void paintBedrockFloor(Chunk chunk) {
        final int y = chunk.getBottomY();
        final int x0 = chunk.getPos().getStartX();
        final int z0 = chunk.getPos().getStartZ();
        final BlockState bedrock = Blocks.BEDROCK.getDefaultState();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            m.set(x0 + dx, y, z0 + dz);
            chunk.setBlockState(m, bedrock, false);
        }
    }

    private void paintRoots(Chunk chunk) {
        final int minX = chunk.getPos().getStartX();
        final int minZ = chunk.getPos().getStartZ();
        final int maxX = minX + 15;
        final int maxZ = minZ + 15;
        final int bottomY = chunk.getBottomY();

        final int CELL = 40; // dense

        int cx0 = Math.floorDiv(minX, CELL) - 1;
        int cz0 = Math.floorDiv(minZ, CELL) - 1;
        int cx1 = Math.floorDiv(maxX, CELL) + 1;
        int cz1 = Math.floorDiv(maxZ, CELL) + 1;

        BlockPos.Mutable m = new BlockPos.Mutable();
        List<Anchor> reps = new ArrayList<>();

        for (int cx = cx0; cx <= cx1; cx++) for (int cz = cz0; cz <= cz1; cz++) {
            int cellId = (cx * 7349) ^ (cz * 9157);
            Random r = Random.create(scrambleId(seed, cellId));

            int anchorsHere = 3 + r.nextBetween(0, 2); // 3..5
            List<Anchor> anchors = new ArrayList<>(anchorsHere);
            for (int i = 0; i < anchorsHere; i++) {
                int ax = cx * CELL + r.nextBetween(0, CELL - 1);
                int az = cz * CELL + r.nextBetween(0, CELL - 1);
                int ay = r.nextBetween(24, 320);
                anchors.add(new Anchor(ax, ay, az, cx, cz, i));
            }
            reps.add(anchors.get(r.nextInt(anchors.size())));

            // Main strands (reweighted: fewer non-steep, more steep)
            for (Anchor a : anchors) {
                int strands = 2 + r.nextBetween(0, 2); // 2..4
                for (int s = 0; s < strands; s++) {
                    Vec3d d = chooseWeightedDirection(r);
                    int len = 90 + r.nextBetween(0, 120);
                    int thick = pickThickness(r);
                    BlockState log = pickLog(r);
                    carveStrand(chunk, m, a.x, a.y, a.z, d, len, thick, log,
                            minX, minZ, maxX, maxZ, bottomY, r);
                }
            }

            // Intra-cell bridges (medium tilt)
            int bridgesLocal = 2 + r.nextBetween(0, 2); // 2..4
            for (int b = 0; b < bridgesLocal; b++) {
                Anchor a = anchors.get(r.nextInt(anchors.size()));
                Anchor b2 = anchors.get(r.nextInt(anchors.size()));
                if (a == b2) continue;

                Vec3d dir = directionToward(a.x, a.y, a.z, b2.x, b2.y, b2.z, r, 20, 40);
                int len = (int)Math.max(50, new Vec3d(b2.x - a.x, b2.y - a.y, b2.z - a.z).length());
                int thick = Math.max(1, pickThickness(r) - 1);
                BlockState log = pickLog(r);

                carveStrand(chunk, m, a.x, a.y, a.z, dir, len, thick, log,
                        minX, minZ, maxX, maxZ, bottomY, r);
            }
        }

        // External bridges between neighboring cells
        for (int cx = cx0; cx <= cx1; cx++) for (int cz = cz0; cz <= cz1; cz++) {
            int cellId = (cx * 7349) ^ (cz * 9157);
            Random r = Random.create(scrambleId(seed, cellId));

            int bridges = 2 + r.nextBetween(0, 2); // 2..4
            Anchor a = representativeFor(reps, cx, cz, CELL);
            if (a == null) continue;

            for (int b = 0; b < bridges; b++) {
                int nx = cx + r.nextBetween(-1, 1);
                int nz = cz + r.nextBetween(-1, 1);
                if (nx == cx && nz == cz) nz += 1;

                Anchor nb = representativeFor(reps, nx, nz, CELL);
                if (nb == null) continue;

                Vec3d dir = directionToward(a.x, a.y, a.z, nb.x, nb.y, nb.z, r, 20, 40);
                int len = (int)Math.max(60, new Vec3d(nb.x - a.x, nb.y - a.y, nb.z - a.z).length());
                int thick = Math.max(1, pickThickness(r) - 1);
                BlockState log = pickLog(r);

                carveStrand(chunk, m, a.x, a.y, a.z, dir, len, thick, log,
                        minX, minZ, maxX, maxZ, bottomY, r);
            }
        }
    }

    /* -------------------- helpers -------------------- */

    private static long scrambleId(long seed, int salt) {
        long h = seed ^ (salt * 0x9E3779B97F4A7C15L);
        h ^= (h >>> 30); h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 27); h *= 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    private static class Anchor {
        final int x, y, z, cellX, cellZ, id;
        Anchor(int x, int y, int z, int cellX, int cellZ, int id) {
            this.x = x; this.y = y; this.z = z;
            this.cellX = cellX; this.cellZ = cellZ; this.id = id;
        }
    }

    private static Anchor representativeFor(List<Anchor> reps, int cx, int cz, int cellSize) {
        for (Anchor a : reps) if (a.cellX == cx && a.cellZ == cz) return a;
        return null;
    }

    /** Reweighted: non-steep −30%, steep +50% (renormalized). */
    private static Vec3d chooseWeightedDirection(Random r) {
        // base: low 0.60, med 0.25, steep 0.15
        double lw = 0.60 * 0.70;   // 0.42
        double mw = 0.25 * 0.70;   // 0.175
        double sw = 0.15 * 1.50;   // 0.225
        double sum = lw + mw + sw; // 0.82
        double lowT = lw / sum;                // ≈ 0.512195
        double medT = (lw + mw) / sum;         // ≈ 0.725610

        float f = r.nextFloat();
        if (f < lowT) return dirLow(r);
        if (f < medT) return dirMedium(r);
        return dirSteep(r);
    }

    private static Vec3d dirLow(Random r) {
        double yaw = r.nextDouble() * Math.PI * 2.0;
        double pitch = Math.toRadians(r.nextBetween(0, 20));
        return fromAngles(yaw, pitch);
    }
    private static Vec3d dirMedium(Random r) {
        double yaw = r.nextDouble() * Math.PI * 2.0;
        double pitch = Math.toRadians(r.nextBetween(20, 45));
        return fromAngles(yaw, pitch);
    }
    private static Vec3d dirSteep(Random r) {
        double yaw = r.nextDouble() * Math.PI * 2.0;
        double pitch = Math.toRadians(r.nextBetween(45, 80));
        return fromAngles(yaw, pitch);
    }

    private static Vec3d directionToward(int ax, int ay, int az, int bx, int by, int bz, Random r, int minPitchDeg, int maxPitchDeg) {
        Vec3d toward = new Vec3d(bx - ax, by - ay, bz - az).normalize();
        double yaw = Math.atan2(toward.z, toward.x) + (r.nextDouble() - 0.5) * 0.4;
        int pitchDeg = MathHelper.clamp(minPitchDeg + r.nextBetween(0, Math.max(0, maxPitchDeg - minPitchDeg)), 0, 80);
        double pitch = Math.toRadians(pitchDeg) * (toward.y < 0 ? -1 : 1);
        return fromAngles(yaw, pitch);
    }
    private static Vec3d fromAngles(double yaw, double pitch) {
        double x = Math.cos(yaw) * Math.cos(pitch);
        double y = Math.sin(pitch);
        double z = Math.sin(yaw) * Math.cos(pitch);
        return new Vec3d(x, y, z).normalize();
    }

    /** Thickness skewed so *most are thick*: 1:18%, 2:45%, 3:37%. */
    private static int pickThickness(Random r) {
        float f = r.nextFloat();
        if (f < 0.18f) return 1;
        if (f < 0.63f) return 2;   // 0.18 + 0.45
        return 3;
    }

    private static BlockState pickLog(Random r) {
        // No birch.
        List<BlockState> logs = List.of(
                Blocks.OAK_LOG.getDefaultState(),
                Blocks.SPRUCE_LOG.getDefaultState(),
                Blocks.DARK_OAK_LOG.getDefaultState(),
                Blocks.ACACIA_LOG.getDefaultState(),
                Blocks.JUNGLE_LOG.getDefaultState(),
                Blocks.MANGROVE_LOG.getDefaultState()
        );
        return logs.get(r.nextInt(logs.size()));
    }

    private static Direction.Axis dominantAxis(Vec3d v) {
        double ax = Math.abs(v.x), ay = Math.abs(v.y), az = Math.abs(v.z);
        if (ax >= ay && ax >= az) return Direction.Axis.X;
        if (az >= ax && az >= ay) return Direction.Axis.Z;
        return Direction.Axis.Y;
    }

    private static void carveStrand(
            Chunk chunk, BlockPos.Mutable m,
            int sx, int sy, int sz, Vec3d dir, int len, int thick, BlockState log,
            int minX, int minZ, int maxX, int maxZ, int bottomY, Random r
    ) {
        Vec3d p = new Vec3d(sx + 0.5, sy + 0.5, sz + 0.5);
        Vec3d d = dir.normalize();

        for (int i = 0; i < len; i++) {
            d = d.add((r.nextDouble() - 0.5) * 0.035, (r.nextDouble() - 0.5) * 0.03, (r.nextDouble() - 0.5) * 0.035).normalize();
            p = p.add(d.multiply(1.15));

            int x = MathHelper.floor(p.x);
            int y = MathHelper.floor(p.y);
            int z = MathHelper.floor(p.z);
            if (y < bottomY + 1 || y >= bottomY + 384) continue;

            if (x < minX - thick || x > maxX + thick || z < minZ - thick || z > maxZ + thick) continue;

            Direction.Axis axis = dominantAxis(d);
            BlockState oriented = log.with(PillarBlock.AXIS, axis);

            for (int dx = -thick; dx <= thick; dx++) for (int dy = -thick; dy <= thick; dy++) for (int dz = -thick; dz <= thick; dz++) {
                if (dx*dx + dy*dy + dz*dz > thick*thick) continue;
                int px = x + dx, py = y + dy, pz = z + dz;
                if (px < minX || px > maxX || pz < minZ || pz > maxZ) continue;
                m.set(px, py, pz);
                if (chunk.getBlockState(m).isAir()) chunk.setBlockState(m, oriented, false);
            }
        }
    }
}
