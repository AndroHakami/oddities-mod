package net.seep.odd.abilities.wizard.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.abilities.wizard.WizardTargeting;

public final class WizardTargetingClient {
    private WizardTargetingClient() {}

    /** Same idea as server: top-of-block center, or null. */
    public static Vec3d getCirclePos(MinecraftClient mc) {
        if (mc.player == null) return null;

        HitResult hr = mc.player.raycast(WizardTargeting.RANGE, mc.getTickDelta(), false);
        if (hr.getType() == HitResult.Type.BLOCK && hr instanceof BlockHitResult bhr) {
            BlockPos bp = bhr.getBlockPos();
            return new Vec3d(bp.getX() + 0.5, bp.getY() + 1.01, bp.getZ() + 0.5);
        }
        return null;
    }
}
