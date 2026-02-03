// src/main/java/net/seep/odd/mixin/ClimberWallClimbMixin.java
package net.seep.odd.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import net.seep.odd.abilities.climber.net.ClimberClimbNetworking;
import net.seep.odd.abilities.power.ClimberPower;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class ClimberWallClimbMixin extends Entity {

    // LivingEntity fields (Yarn)
    @Shadow public float forwardSpeed;
    @Shadow public float sidewaysSpeed;
    @Shadow protected boolean jumping;

    protected ClimberWallClimbMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    /**
     * Make walls act like climbables for players that are allowed to climb.
     * This MUST work both client + server, or movement will feel like "nothing happens".
     */
    @Inject(method = "isClimbing", at = @At("HEAD"), cancellable = true)
    private void odd$climberWallCountsAsClimbing(CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof PlayerEntity player)) return;

        // Never interfere with spectator/flight
        if (player.isSpectator()) return;
        if (player.getAbilities().flying) return;

        // Let vanilla climbables behave normally
        if (player.getWorld().getBlockState(player.getBlockPos()).isIn(BlockTags.CLIMBABLE)) return;

        // Server truth: ONLY climber power can climb
        boolean allowed;
        if (!player.getWorld().isClient) {
            allowed = (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) && ClimberPower.hasClimber(sp);
        } else {
            // Client sim: use server-synced flag (mirrors how ladder-like mods do it)
            allowed = ClimberClimbNetworking.canClimbAnySide(player.getUuid());
        }
        if (!allowed) return;

        // Must be touching a wall
        if (!odd$isTouchingWall(player)) return;

        // Client: require intent so it doesn't feel sticky.
        // Server: DO NOT require intent, otherwise server often never agrees.
        if (player.getWorld().isClient) {
            if (!odd$hasClimbIntentClient(player)) return;
        }

        cir.setReturnValue(true);
    }

    /** Robust wall-touch check (horizontalCollision + slightly expanded probes). */
    private boolean odd$isTouchingWall(PlayerEntity player) {
        World w = player.getWorld();

        // This flag is usually correct and cheap
        if (player.horizontalCollision) return true;

        Box bb = player.getBoundingBox();
        // Trim top/bottom so floor/ceiling don't count
        Box body = new Box(bb.minX, bb.minY + 0.10, bb.minZ, bb.maxX, bb.maxY - 0.10, bb.maxZ);

        final double eps = 0.10; // bigger than before: fixes "almost touching but not detected"

        boolean hitXPos = !w.isSpaceEmpty(player, body.offset( eps, 0, 0));
        boolean hitXNeg = !w.isSpaceEmpty(player, body.offset(-eps, 0, 0));
        boolean hitZPos = !w.isSpaceEmpty(player, body.offset(0, 0,  eps));
        boolean hitZNeg = !w.isSpaceEmpty(player, body.offset(0, 0, -eps));

        return hitXPos || hitXNeg || hitZPos || hitZNeg;
    }

    /** Client-side intent: prevents sticky walls. */
    private boolean odd$hasClimbIntentClient(PlayerEntity player) {
        // Moving or jumping or sneaking means intent.
        return this.jumping
                || player.isSneaking()
                || Math.abs(this.forwardSpeed) > 0.01f
                || Math.abs(this.sidewaysSpeed) > 0.01f;
    }

    /**
     * Faster, snappier climb feel (client mainly).
     * Server will still treat you as climbing via isClimbing() so it "permits" it.
     */
    @Inject(method = "travel(Lnet/minecraft/util/math/Vec3d;)V", at = @At("TAIL"))
    private void odd$climberSpeedBoost(Vec3d movementInput, CallbackInfo ci) {
        if (!((Object) this instanceof PlayerEntity player)) return;

        // Only affect our custom wall-climb (not ladders/vines)
        if (player.getWorld().getBlockState(player.getBlockPos()).isIn(BlockTags.CLIMBABLE)) return;

        // Only when we're actually climbing
        if (!player.isClimbing()) return;

        // Only players allowed to climb
        boolean allowed;
        if (!player.getWorld().isClient) {
            allowed = (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) && ClimberPower.hasClimber(sp);
        } else {
            allowed = ClimberClimbNetworking.canClimbAnySide(player.getUuid());
        }
        if (!allowed) return;

        // Don’t fight spectator/flight
        if (player.isSpectator() || player.getAbilities().flying) return;

        Vec3d v = player.getVelocity();

        // Tuning knobs (feel free to tweak)
        final double maxUp   = 0.48;
        final double maxDown = -0.38;

        // Give “swingy / responsive” control:
        // - moving forward or jump pushes you upward a bit
        // - sneak lets you descend quicker
        boolean upIntent = (player.getWorld().isClient) && (this.jumping || this.forwardSpeed > 0.01f);
        boolean downIntent = player.isSneaking();

        double vy = v.y;

        if (upIntent && !downIntent) {
            vy = Math.min(maxUp, Math.max(vy, 0.18) + 0.12);
        } else if (downIntent && !upIntent) {
            vy = Math.max(maxDown, vy - 0.12);
        } else {
            // neutral: reduce slide-down so you “stick” better
            vy = Math.max(-0.05, vy);
        }

        player.setVelocity(v.x, vy, v.z);
        player.fallDistance = 0.0f;
    }
}
