package net.seep.odd.abilities.ghostlings.entity;

import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Optional;

public final class GhostlingGatherables {
    private GhostlingGatherables() {}

    // Minimal mapping: if sample is diamond -> diamond ore, logs -> #logs, else simple heuristic
    public static boolean matchesTarget(BlockState state, String sampleItemId) {
        Identifier id = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock());
        String path = id.getPath();

        if (sampleItemId.contains(":diamond")) {
            return path.contains("diamond_ore");
        }
        if (sampleItemId.endsWith("_log")) {
            return path.endsWith("_log");
        }
        // Fallback: generic logs or ores if player gave something generic
        return path.endsWith("_log") || path.endsWith("_ore");
    }

    public static Optional<BlockPos> findNearestTarget(GhostlingEntity g, String sampleItemId, BlockPos workOrigin) {
        World world = g.getWorld();
        BlockPos origin = (workOrigin != null) ? workOrigin : g.getBlockPos();
        int R = 18;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int r=1;r<=R;r++) {
            for (int dx=-r;dx<=r;dx++) for (int dz=-r;dz<=r;dz++) {
                m.set(origin.getX()+dx, origin.getY(), origin.getZ()+dz);
                for (int dy=-6;dy<=6;dy++) {
                    m.setY(origin.getY()+dy);
                    if (matchesTarget(world.getBlockState(m), sampleItemId))
                        return Optional.of(m.toImmutable());
                }
            }
        }
        return Optional.empty();
    }
}
