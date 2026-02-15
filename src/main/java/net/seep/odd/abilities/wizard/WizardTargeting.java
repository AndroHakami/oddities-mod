package net.seep.odd.abilities.wizard;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public final class WizardTargeting {
    private WizardTargeting() {}

    public static final double RANGE = 32.0;

    /** Returns a nice "circle position" on top of the targeted block, or null if no block hit. */
    public static Vec3d getCirclePos(ServerPlayerEntity p) {
        Vec3d start = p.getCameraPosVec(1f);
        Vec3d end = start.add(p.getRotationVector().multiply(RANGE));

        HitResult hr = p.getWorld().raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                p
        ));

        if (hr.getType() == HitResult.Type.BLOCK && hr instanceof BlockHitResult bhr) {
            BlockPos bp = bhr.getBlockPos();
            // Always render/spawn on TOP of the block (consistent + looks good)
            return new Vec3d(bp.getX() + 0.5, bp.getY() + 1.01, bp.getZ() + 0.5);
        }
        return null;
    }
}
