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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Void terrain + bedrock floor, baked “Rotten Roots” webs.
 */
public final class RottenRootsChunkGenerator extends ChunkGenerator {

    // MapCodec used by the CHUNK_GENERATOR registry (type-based dispatch)
    public static final MapCodec<RottenRootsChunkGenerator> CONFIG_CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
                    Codec.LONG.optionalFieldOf("seed", 0L).forGetter(g -> g.seed),
                    Codec.FLOAT.optionalFieldOf("density", 1.0f).forGetter(g -> g.density)
            ).apply(instance, RottenRootsChunkGenerator::new)
    );

    // IMPORTANT: stable Codec instance (do NOT call CONFIG_CODEC.codec() every time)
    public static final Codec<RottenRootsChunkGenerator> CODEC = CONFIG_CODEC.codec();

    // === Multipliers ===
    private static final float DENSITY_FACTOR = 1.4f;
    private static final int   LENGTH_MULT    = 3;
    private static final int   THICK_SCALE_MAX= 2;

    private final long seed;
    private final float density;

    public RottenRootsChunkGenerator(BiomeSource biomeSource, long seed, float density) {
        super(biomeSource);
        this.seed = seed;
        this.density = Math.max(0.05f, density);
    }

    // THIS is what fixes your world save corruption:
    @Override
    protected Codec<? extends ChunkGenerator> getCodec() {
        return CODEC; // stable instance
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
        paintBedrockFloor(chunk);
        paintRoots(chunk);
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

        final int CELL = 40;

        int cx0 = Math.floorDiv(minX, CELL) - 1;
        int cz0 = Math.floorDiv(minZ, CELL) - 1;
        int cx1 = Math.floorDiv(maxX, CELL) + 1;
        int cz1 = Math.floorDiv(maxZ, CELL) + 1;

        BlockPos.Mutable m = new BlockPos.Mutable();
        List<Anchor> reps = new ArrayList<>();

        for (int cx = cx0; cx <= cx1; cx++) for (int cz = cz0; cz <= cz1; cz++) {
            int cellId = (cx * 7349) ^ (cz * 9157);
            Random r = Random.create(scrambleId(seed, cellId));

            int baseAnchors = 3 + r.nextBetween(0, 2);
            int anchorsHere = Math.max(2, Math.round(baseAnchors * DENSITY_FACTOR));

            List<Anchor> anchors = new ArrayList<>(anchorsHere);
            for (int i = 0; i < anchorsHere; i++) {
                int ax = cx * CELL + r.nextBetween(0, CELL - 1);
                int az = cz * CELL + r.nextBetween(0, CELL - 1);
                int ay = r.nextBetween(24, 320);
                anchors.add(new Anchor(ax, ay, az, cx, cz, i));
            }
            reps.add(anchors.get(r.nextInt(anchors.size())));

            for (Anchor a : anchors) {
                int baseStrands = 2 + r.nextBetween(0, 2);
                int strands = Math.max(1, Math.round(baseStrands * DENSITY_FACTOR));
                for (int s = 0; s < strands; s++) {
                    Vec3d d = chooseWeightedDirection(r);
                    int len = (90 + r.nextBetween(0, 120)) * LENGTH_MULT;
                    int baseThick = pickThickness(r);
                    int scale = 1 + r.nextBetween(0, THICK_SCALE_MAX - 1);
                    int thick = Math.min(8, baseThick * scale);
                    BlockState log = pickLog(r);
                    carveStrand(chunk, m, a.x, a.y, a.z, d, len, thick, log,
                            minX, minZ, maxX, maxZ, bottomY, r);
                }
            }

            int baseBridgesLocal = 2 + r.nextBetween(0, 2);
            int bridgesLocal = Math.max(1, Math.round(baseBridgesLocal * DENSITY_FACTOR));
            for (int b = 0; b < bridgesLocal; b++) {
                Anchor a = anchors.get(r.nextInt(anchors.size()));
                Anchor b2 = anchors.get(r.nextInt(anchors.size()));
                if (a == b2) continue;

                Vec3d dir = directionToward(a.x, a.y, a.z, b2.x, b2.y, b2.z, r, 20, 40);
                int baseLen = (int)Math.max(50, new Vec3d(b2.x - a.x, b2.y - a.y, b2.z - a.z).length());
                int len = baseLen * LENGTH_MULT;

                int baseThick = pickThickness(r);
                int scale = 1 + r.nextBetween(0, THICK_SCALE_MAX - 1);
                int thick = Math.max(1, Math.min(7, baseThick * scale - 1));

                BlockState log = pickLog(r);
                carveStrand(chunk, m, a.x, a.y, a.z, dir, len, thick, log,
                        minX, minZ, maxX, maxZ, bottomY, r);
            }
        }

        for (int cx = cx0; cx <= cx1; cx++) for (int cz = cz0; cz <= cz1; cz++) {
            int cellId = (cx * 7349) ^ (cz * 9157);
            Random r = Random.create(scrambleId(seed, cellId));

            int baseBridges = 2 + r.nextBetween(0, 2);
            int bridges = Math.max(1, Math.round(baseBridges * DENSITY_FACTOR));

            Anchor a = representativeFor(reps, cx, cz);
            if (a == null) continue;

            for (int b = 0; b < bridges; b++) {
                int nx = cx + r.nextBetween(-1, 1);
                int nz = cz + r.nextBetween(-1, 1);
                if (nx == cx && nz == cz) nz += 1;

                Anchor nb = representativeFor(reps, nx, nz);
                if (nb == null) continue;

                Vec3d dir = directionToward(a.x, a.y, a.z, nb.x, nb.y, nb.z, r, 20, 40);
                int baseLen = (int)Math.max(60, new Vec3d(nb.x - a.x, nb.y - a.y, nb.z - a.z).length());
                int len = baseLen * LENGTH_MULT;

                int baseThick = pickThickness(r);
                int scale = 1 + r.nextBetween(0, THICK_SCALE_MAX - 1);
                int thick = Math.max(1, Math.min(7, baseThick * scale - 1));

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

    private static Anchor representativeFor(List<Anchor> reps, int cx, int cz) {
        for (Anchor a : reps) if (a.cellX == cx && a.cellZ == cz) return a;
        return null;
    }

    private static Vec3d chooseWeightedDirection(Random r) {
        double lw = 0.60 * 0.70;
        double mw = 0.25 * 0.70;
        double sw = 0.15 * 1.50;
        double sum = lw + mw + sw;
        double lowT = lw / sum;
        double medT = (lw + mw) / sum;

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

    private static int pickThickness(Random r) {
        float f = r.nextFloat();
        if (f < 0.15f) return 1;
        if (f < 0.60f) return 2;
        return 3;
    }

    private static BlockState pickLog(Random r) {
        List<BlockState> logs = List.of(
                Blocks.OAK_LOG.getDefaultState(),
                Blocks.SPRUCE_LOG.getDefaultState(),
                Blocks.DARK_OAK_LOG.getDefaultState()
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

            for (int dx = -thick; dx <= thick; dx++)
                for (int dy = -thick; dy <= thick; dy++)
                    for (int dz = -thick; dz <= thick; dz++) {
                        if (dx*dx + dy*dy + dz*dz > thick*thick) continue;
                        int px = x + dx, py = y + dy, pz = z + dz;
                        if (px < minX || px > maxX || pz < minZ || pz > maxZ) continue;
                        m.set(px, py, pz);
                        if (chunk.getBlockState(m).isAir()) chunk.setBlockState(m, oriented, false);
                    }
        }
    }
}
