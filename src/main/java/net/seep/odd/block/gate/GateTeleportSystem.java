package net.seep.odd.block.gate;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import net.seep.odd.Oddities;
import net.seep.odd.expeditions.Expeditions;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GateTeleportSystem {
    private GateTeleportSystem() {}

    // ====== TUNING ======
    // How far from the gate's geometric center we place the TELEPORT slab along the facing normal.
    // Use the SAME sign convention as your visual: positive along `facing`.
    public static double TRIGGER_PUSH = 0.45;      // visual was ~0.55; this is slightly "ahead" of it

    // Thickness of the teleport slab (total depth in blocks, axis-aligned)
    public static double TRIGGER_DEPTH = 2.20;     // make this big so it’s hard to miss

    // Pad the width/height a bit so it feels less “exact frame”
    public static double PAD_W = 1.40;             // +1.4 blocks total width (0.7 each side)
    public static double PAD_H = 1.40;             // +1.4 blocks total height

    // How far around the player we search for a gate controller (in blocks)
    public static int SEARCH_R = 7;

    // Cooldown so you don't spam teleport
    public static long COOLDOWN_TICKS = 40;

    public static boolean DEBUG = true;

    private static final Object2LongOpenHashMap<UUID> LAST_TP = new Object2LongOpenHashMap<>();

    private static final Map<UUID, ReturnSpot> RETURN = new HashMap<>();
    private record ReturnSpot(RegistryKey<World> dim, Vec3d pos, float yaw, float pitch) {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(GateTeleportSystem::tick);
        Oddities.LOGGER.info("[GateTP] GateTeleportSystem enabled");
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p == null || p.getWorld() == null) continue;
            if (p.isSpectator()) continue;

            ServerWorld w = p.getServerWorld();
            BlockPos origin = p.getBlockPos();

            // Search nearby blocks for an OPEN controller
            BlockPos min = origin.add(-SEARCH_R, -3, -SEARCH_R);
            BlockPos max = origin.add( SEARCH_R,  6,  SEARCH_R);

            BlockPos foundGate = null;
            BlockState foundState = null;

            for (BlockPos bp : BlockPos.iterate(min, max)) {
                BlockState s = w.getBlockState(bp);
                if (!(s.getBlock() instanceof DimensionalGateBlock)) continue;
                if (!DimensionalGateBlock.isController(s)) continue;
                if (!s.get(DimensionalGateBlock.OPEN)) continue;

                foundGate = bp.toImmutable();
                foundState = s;
                break;
            }

            if (foundGate == null) continue;

            // Build portal box and check intersection with player BB
            Box portal = computePortalBox(foundGate, foundState);

            if (DEBUG && (w.getTime() % 20 == 0)) {
                Oddities.LOGGER.info("[GateTP] nearGate={} portalBox={} playerPos={}",
                        foundGate, portal, p.getPos());
            }

            if (!portal.intersects(p.getBoundingBox())) continue;

            // Cooldown
            long now = w.getTime();
            UUID id = p.getUuid();
            long last = LAST_TP.getOrDefault(id, Long.MIN_VALUE);
            if (now - last < COOLDOWN_TICKS) continue;
            LAST_TP.put(id, now);

            if (DEBUG) Oddities.LOGGER.info("[GateTP] HIT {} -> teleport attempt", p.getName().getString());

            doTeleport(server, p);
        }
    }

    private static Box computePortalBox(BlockPos basePos, BlockState state) {
        Direction facing = state.get(DimensionalGateBlock.FACING);
        Direction right  = facing.rotateYClockwise();

        // Center of 4x5 doorway plane (same math as your shader)
        double cx = basePos.getX() + 0.5 + right.getOffsetX() * ((DimensionalGateBlock.WIDTH - 1) / 2.0);
        double cy = basePos.getY() + ((DimensionalGateBlock.HEIGHT - 1) / 2.0) + 0.5;
        double cz = basePos.getZ() + 0.5 + right.getOffsetZ() * ((DimensionalGateBlock.WIDTH - 1) / 2.0);

        Vec3d center = new Vec3d(cx, cy, cz).add(
                facing.getOffsetX() * TRIGGER_PUSH,
                0.0,
                facing.getOffsetZ() * TRIGGER_PUSH
        );

        double w = DimensionalGateBlock.WIDTH  + PAD_W;
        double h = DimensionalGateBlock.HEIGHT + PAD_H;
        double d = TRIGGER_DEPTH;

        // Box.of takes full extents in X/Y/Z (axis-aligned), so swap width/depth depending on facing axis
        double sx = (facing.getAxis() == Direction.Axis.X) ? d : w;
        double sz = (facing.getAxis() == Direction.Axis.Z) ? d : w;

        return Box.of(center, sx, h, sz);
    }

    private static void doTeleport(MinecraftServer server, ServerPlayerEntity p) {
        // Return support like your Void system (optional, but handy)
        if (p.getWorld().getRegistryKey().equals(Expeditions.ROTTEN_ROOTS_WORLD)) {
            ReturnSpot r = RETURN.remove(p.getUuid());
            if (r != null) {
                teleport(p, server, r.dim, r.pos, r.yaw, r.pitch);
                return;
            }
            ServerWorld overworld = server.getOverworld();
            BlockPos sp = overworld.getSpawnPos();
            teleport(p, server, overworld.getRegistryKey(),
                    new Vec3d(sp.getX() + 0.5, sp.getY() + 1.0, sp.getZ() + 0.5),
                    p.getYaw(), p.getPitch());
            return;
        }

        // store return
        RETURN.put(p.getUuid(), new ReturnSpot(
                p.getWorld().getRegistryKey(), p.getPos(), p.getYaw(), p.getPitch()
        ));

        // EXACTLY like your command (Y=120)
        ServerWorld dst = server.getWorld(Expeditions.ROTTEN_ROOTS_WORLD);
        if (dst == null) {
            Oddities.LOGGER.error("[GateTP] Rotten Roots world missing (server.getWorld returned null). Check JSON/world load.");
            return;
        }

        teleport(p, server, Expeditions.ROTTEN_ROOTS_WORLD, new Vec3d(0.5, 120.0, 0.5), p.getYaw(), p.getPitch());
    }

    private static void teleport(ServerPlayerEntity p, MinecraftServer server,
                                 RegistryKey<World> dim, Vec3d pos, float yaw, float pitch) {
        ServerWorld target = server.getWorld(dim);
        if (target == null) {
            Oddities.LOGGER.error("[GateTP] teleport failed: target world is null for {}", dim.getValue());
            return;
        }

        // Make it extra reliable
        p.stopRiding();
        p.setVelocity(Vec3d.ZERO);
        p.fallDistance = 0f;

        p.teleport(target, pos.x, pos.y, pos.z, yaw, pitch);

        if (DEBUG) Oddities.LOGGER.info("[GateTP] TELEPORT CALLED -> nowWorld={}", p.getWorld().getRegistryKey().getValue());
    }
}
