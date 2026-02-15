// FILE: src/main/java/net/seep/odd/abilities/wizard/SwapperinoRules.java
package net.seep.odd.abilities.wizard;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class SwapperinoRules {
    private SwapperinoRules() {}

    public static boolean allowedSwapTarget(LivingEntity target) {
        if (!target.isAlive()) return false;

        // "CAN die"
        if (target.isInvulnerable()) return false;

        if (target instanceof PlayerEntity p) {
            if (p.isCreative() || p.isSpectator()) return false;
        }

        // Max HP cap
        return target.getMaxHealth() <= 60.0f;
    }

    public static Vec3d findSafeAbove(ServerWorld world, Vec3d base, Entity who) {
        // start: +1.05 so big mobs don't sink into ground
        double x = base.x;
        double z = base.z;
        double startY = Math.floor(base.y) + 1.05;

        var dims = who.getDimensions(who.getPose());
        for (int i = 0; i < 6; i++) {
            double y = startY + i;
            Box box = dims.getBoxAt(x, y, z);
            if (world.isSpaceEmpty(who, box)) {
                return new Vec3d(x, y, z);
            }
        }

        // fallback
        return new Vec3d(x, base.y + 1.2, z);
    }
}
