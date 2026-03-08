// FILE: src/main/java/net/seep/odd/worldgen/structure/ShroomVillagePiece.java
package net.seep.odd.worldgen.structure;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PillarBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
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
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.seep.odd.Oddities;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.worldgen.ModStructures;

public final class ShroomVillagePiece extends StructurePiece {

    private static final Identifier TREASURE_HOUSE_ID =
            new Identifier(Oddities.MOD_ID, "treasure_house_mushroom");

    private BlockPos origin;
    private int height;
    private int radius;
    private int wallThickness;
    private double tiltDx;
    private double tiltDz;

    // Used by the StructurePieceType loader (NBT)
    public ShroomVillagePiece(StructureContext context, NbtCompound nbt) {
        super(ModStructures.SHROOM_VILLAGE_PIECE, nbt);
        this.origin = BlockPos.fromLong(nbt.getLong("Origin"));
        this.height = nbt.getInt("Height");
        this.radius = nbt.getInt("Radius");
        this.wallThickness = nbt.getInt("Wall");
        this.tiltDx = nbt.getDouble("TiltDx");
        this.tiltDz = nbt.getDouble("TiltDz");
    }

    // Used when we first create it
    public ShroomVillagePiece(BlockPos origin, int height, int radius, int wallThickness, double tiltDx, double tiltDz) {
        super(ModStructures.SHROOM_VILLAGE_PIECE, 0, makeBox(origin, height, radius, tiltDx, tiltDz));
        this.origin = origin;
        this.height = height;
        this.radius = radius;
        this.wallThickness = wallThickness;
        this.tiltDx = tiltDx;
        this.tiltDz = tiltDz;
    }

    private static BlockBox makeBox(BlockPos origin, int height, int radius, double tiltDx, double tiltDz) {
        double x0 = origin.getX() + 0.5;
        double z0 = origin.getZ() + 0.5;
        double x1 = x0 + tiltDx * height;
        double z1 = z0 + tiltDz * height;

        int minX = (int) Math.floor(Math.min(x0, x1) - radius - 2);
        int maxX = (int) Math.floor(Math.max(x0, x1) + radius + 2);
        int minZ = (int) Math.floor(Math.min(z0, z1) - radius - 2);
        int maxZ = (int) Math.floor(Math.max(z0, z1) + radius + 2);

        int minY = origin.getY();
        int maxY = origin.getY() + height;

        return new BlockBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) {
        nbt.putLong("Origin", origin.asLong());
        nbt.putInt("Height", height);
        nbt.putInt("Radius", radius);
        nbt.putInt("Wall", wallThickness);
        nbt.putDouble("TiltDx", tiltDx);
        nbt.putDouble("TiltDz", tiltDz);
    }

    @Override
    public void generate(StructureWorldAccess world,
                         StructureAccessor structureAccessor,
                         ChunkGenerator chunkGenerator,
                         Random random,
                         BlockBox chunkBox,
                         ChunkPos chunkPos,
                         BlockPos pivot) {

        BlockState log = ModBlocks.BOGGY_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Y);
        BlockState air = Blocks.AIR.getDefaultState();

        int innerR = Math.max(1, radius - wallThickness);
        int outerR2 = radius * radius;
        int innerR2 = innerR * innerR;

        // Build along Y; center shifts by (tiltDx, tiltDz)
        for (int dy = 0; dy <= height; dy++) {
            double cx = origin.getX() + 0.5 + tiltDx * dy;
            double cz = origin.getZ() + 0.5 + tiltDz * dy;
            int y = origin.getY() + dy;

            int minX = MathHelper.floor(cx - radius - 1);
            int maxX = MathHelper.floor(cx + radius + 1);
            int minZ = MathHelper.floor(cz - radius - 1);
            int maxZ = MathHelper.floor(cz + radius + 1);

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double dx = (x + 0.5) - cx;
                    double dz = (z + 0.5) - cz;
                    int d2 = (int) Math.floor(dx * dx + dz * dz);

                    if (d2 > outerR2) continue;

                    BlockPos p = new BlockPos(x, y, z);
                    if (!chunkBox.contains(p)) continue;

                    // hollow inside, solid shell
                    if (d2 < innerR2) {
                        // clear interior so the village space is empty
                        if (!world.getBlockState(p).isAir()) {
                            world.setBlockState(p, air, 2);
                        }
                    } else {
                        // shell
                        if (world.getBlockState(p).isAir()) {
                            world.setBlockState(p, log, 2);
                        }
                    }
                }
            }
        }

        // Place the treasure house template inside the hollow space
        tryPlaceTreasureHouse(world, random, chunkBox);
    }

    private void tryPlaceTreasureHouse(StructureWorldAccess world, Random random, BlockBox chunkBox) {
        // Structure templates require server access; in worldgen this is normally a ChunkRegion
        if (!(world instanceof ChunkRegion region)) return;

        ServerWorld serverWorld = region.toServerWorld();
        StructureTemplateManager stm = serverWorld.getServer().getStructureTemplateManager();

        StructureTemplate template = stm.getTemplate(TREASURE_HOUSE_ID).orElse(null);
        if (template == null) return;

        // Keep rotation fixed so it doesn't "desync" between chunks.
        // If you want random rotation later, store it in NBT so every chunk uses the same rotation.
        BlockRotation rot = BlockRotation.NONE;

        Vec3i size = template.getSize();
        Vec3i rotSize = rotatedSize(size, rot);

        // Center it on the village origin, 1 block above the base
        BlockPos placePos = origin.add(-rotSize.getX() / 2, 1, -rotSize.getZ() / 2);

        BlockBox placedBox = new BlockBox(
                placePos.getX(),
                placePos.getY(),
                placePos.getZ(),
                placePos.getX() + rotSize.getX() - 1,
                placePos.getY() + rotSize.getY() - 1,
                placePos.getZ() + rotSize.getZ() - 1
        );

        // If this chunk isn't relevant to the template footprint, skip work
        if (!chunkBox.intersects(placedBox)) return;

        StructurePlacementData data = new StructurePlacementData()
                .setRotation(rot)
                .setMirror(BlockMirror.NONE)
                .setIgnoreEntities(false)
                .setUpdateNeighbors(false)
                // IMPORTANT: clip placement to the current chunk call (prevents duplicates + works across chunks)
                .setBoundingBox(chunkBox);

        template.place(world, placePos, BlockPos.ORIGIN, data, random, Block.NOTIFY_LISTENERS);
    }

    private static Vec3i rotatedSize(Vec3i size, BlockRotation rot) {
        return switch (rot) {
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> new Vec3i(size.getZ(), size.getY(), size.getX());
            default -> size;
        };
    }
}