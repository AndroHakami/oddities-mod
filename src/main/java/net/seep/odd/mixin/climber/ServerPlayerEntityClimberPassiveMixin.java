// FILE: src/main/java/net/seep/odd/mixin/climber/ServerPlayerEntityClimberPassiveMixin.java
package net.seep.odd.mixin.climber;

import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.abilities.power.ClimberPower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityClimberPassiveMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void odd$climberPassiveServerTick(CallbackInfo ci) {
        ServerPlayerEntity sp = (ServerPlayerEntity)(Object)this;

        if (!ClimberPower.hasClimber(sp)) return;

        // ✅ always run cleanup + detach-on-powerless
        ClimberPower.serverTick(sp);

        // ✅ POWERLESS: cannot wall climb
        if (ClimberPower.isPowerless(sp)) return;

        // Don’t fight rope physics
        if (ClimberPower.isPrimaryEngaged(sp)) return;

        // Don’t interfere with other movement modes
        if (sp.isFallFlying() || sp.isSwimming() || sp.hasVehicle()) return;

        // Need to be in contact with a wall
        if (!isTouchingWall(sp)) return;

        byte in = ClimberPower.getInputFlags(sp);

        boolean jump    = (in & ClimberPower.IN_JUMP) != 0;
        boolean sneak   = (in & ClimberPower.IN_SNEAK) != 0;

        boolean forward = (in & ClimberPower.IN_FORWARD) != 0;
        boolean back    = (in & ClimberPower.IN_BACK) != 0;

        boolean left    = (in & ClimberPower.IN_LEFT) != 0;
        boolean right   = (in & ClimberPower.IN_RIGHT) != 0;

        boolean up   = jump || forward;
        boolean down = sneak || back;

        final double CLIMB_UP   = 0.30;
        final double CLIMB_DOWN = 0.24;
        final double DAMP_MOVE  = 0.92;
        final double DAMP_IDLE  = 0.70;

        Vec3d v = sp.getVelocity();

        double y;
        if (up && !down) {
            y = Math.max(v.y, CLIMB_UP);
        } else if (down && !up) {
            y = Math.min(v.y, -CLIMB_DOWN);
        } else {
            y = (v.y < 0.0) ? 0.0 : v.y * 0.35;
        }

        boolean anyMove = forward || back || left || right;
        double damp = anyMove ? DAMP_MOVE : DAMP_IDLE;

        sp.setVelocity(v.x * damp, y, v.z * damp);
        sp.fallDistance = 0.0f;
    }

    private static boolean isTouchingWall(ServerPlayerEntity sp) {
        World w = sp.getWorld();
        Box bb = sp.getBoundingBox();

        double cx = (bb.minX + bb.maxX) * 0.5;
        double cz = (bb.minZ + bb.maxZ) * 0.5;

        double y0 = bb.minY + 0.10;
        double y1 = bb.minY + sp.getHeight() * 0.55;
        double y2 = bb.maxY - 0.10;

        double eps = 0.02;

        if (solidAt(w, bb.minX - eps, y0, cz) || solidAt(w, bb.minX - eps, y1, cz) || solidAt(w, bb.minX - eps, y2, cz)) return true;
        if (solidAt(w, bb.maxX + eps, y0, cz) || solidAt(w, bb.maxX + eps, y1, cz) || solidAt(w, bb.maxX + eps, y2, cz)) return true;

        if (solidAt(w, cx, y0, bb.minZ - eps) || solidAt(w, cx, y1, bb.minZ - eps) || solidAt(w, cx, y2, bb.minZ - eps)) return true;
        if (solidAt(w, cx, y0, bb.maxZ + eps) || solidAt(w, cx, y1, bb.maxZ + eps) || solidAt(w, cx, y2, bb.maxZ + eps)) return true;

        return false;
    }

    private static boolean solidAt(World w, double x, double y, double z) {
        BlockPos bp = BlockPos.ofFloored(x, y, z);
        BlockState st = w.getBlockState(bp);
        if (st.isAir()) return false;
        return !st.getCollisionShape(w, bp).isEmpty();
    }
}