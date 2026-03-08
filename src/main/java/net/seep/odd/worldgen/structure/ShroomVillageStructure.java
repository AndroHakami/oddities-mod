package net.seep.odd.worldgen.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;
import net.seep.odd.worldgen.ModStructures;

import java.util.Optional;

public final class ShroomVillageStructure extends Structure {

    public static final Codec<ShroomVillageStructure> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Structure.configCodecBuilder(instance),

                    Codec.INT.optionalFieldOf("radius", 28).forGetter(s -> s.radius),
                    Codec.INT.optionalFieldOf("wall_thickness", 4).forGetter(s -> s.wallThickness),
                    Codec.INT.optionalFieldOf("base_y", 40).forGetter(s -> s.baseY),
                    Codec.INT.optionalFieldOf("height", 260).forGetter(s -> s.height),

                    // 0 = perfectly vertical. 15 = slight lean (≈75° from horizontal).
                    Codec.INT.optionalFieldOf("tilt_degrees", 15).forGetter(s -> s.tiltDegrees)
            ).apply(instance, ShroomVillageStructure::new)
    );

    public final int radius;
    public final int wallThickness;
    public final int baseY;
    public final int height;
    public final int tiltDegrees;

    public ShroomVillageStructure(Config config, int radius, int wallThickness, int baseY, int height, int tiltDegrees) {
        super(config);
        this.radius = radius;
        this.wallThickness = wallThickness;
        this.baseY = baseY;
        this.height = height;
        this.tiltDegrees = tiltDegrees;
    }

    @Override
    public Optional<StructurePosition> getStructurePosition(Context context) {
        // Structure placement system decides *which chunks* get a start.
        // We just decide where inside this chunk the start anchors.
        int x = context.chunkPos().getStartX() + 8;
        int z = context.chunkPos().getStartZ() + 8;

        // clamp height so we don't slam into your bedrock ceiling
        int minY = context.chunkGenerator().getMinimumY();
        int topExclusive = minY + context.chunkGenerator().getWorldHeight();
        int maxAllowedTop = topExclusive - 3; // keep 2 layers bedrock above
        int baseY = Math.max(minY + 1, this.baseY);
        int height = Math.max(40, Math.min(this.height, maxAllowedTop - baseY));

        Random r = context.random();

        // tilt vector (horizontal shift per 1 Y). tilt=0 => vertical
        double tiltDx;
        double tiltDz;
        if (tiltDegrees > 0) {
            double yaw = r.nextDouble() * Math.PI * 2.0;
            double t = Math.tan(Math.toRadians(tiltDegrees)); // per 1 Y step
            tiltDx = Math.cos(yaw) * t;
            tiltDz = Math.sin(yaw) * t;
        } else {
            tiltDx = 0.0;
            tiltDz = 0.0;
        }

        BlockPos origin = new BlockPos(x, baseY, z);

        return Optional.of(new StructurePosition(origin, collector -> {
            collector.addPiece(new ShroomVillagePiece(origin, height, radius, wallThickness, tiltDx, tiltDz));
        }));
    }

    @Override
    public StructureType<?> getType() {
        return ModStructures.SHROOM_VILLAGE_TYPE;
    }
}