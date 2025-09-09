package net.seep.odd.abilities.voids;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class VoidSystem {
    private VoidSystem(){}

    /** Dimension key. Autoloaded by the datapack below (flat void). */
    public static final RegistryKey<World> VOID_WORLD =
            RegistryKey.of(RegistryKeys.WORLD, new Identifier("odd","void"));

    /** Where to return players from the Void. */
    private static final Map<UUID, ReturnSpot> RETURN = new HashMap<>();
    private record ReturnSpot(RegistryKey<World> dim, Vec3d pos, float yaw, float pitch) {}

    private static boolean islandBuilt = false;

    public static void init() {
        // after worlds load, stamp the island if needed
        ServerLifecycleEvents.SERVER_STARTED.register(VoidSystem::onServerStarted);

        // light ambient ash in the void
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerWorld w = server.getWorld(VOID_WORLD);
            if (w == null) return;
            if (w.getTime() % 5 == 0) {
                for (int i = 0; i < 20; i++) {
                    double x = (w.random.nextDouble() - 0.5) * 120.0;
                    double z = (w.random.nextDouble() - 0.5) * 120.0;
                    w.spawnParticles(ParticleTypes.PORTAL, x, 66, z, 1, 0.3, 0.2, 0.3, 0.0);
                }
            }
        });
    }

    private static void onServerStarted(MinecraftServer server) {
        ServerWorld voidW = server.getWorld(VoidSystem.VOID_WORLD);
        if (voidW != null && !islandBuilt) {
            buildIsland(voidW);
            islandBuilt = true;
        }
    }

    /** Primary action: spawn portal in normal worlds; inside void → return. */
    public static void onPrimary(ServerPlayerEntity p) {
        if (p.getWorld().getRegistryKey().equals(VOID_WORLD)) {
            ReturnSpot r = RETURN.remove(p.getUuid());
            if (r == null) { p.sendMessage(Text.literal("Nowhere to return to."), true); return; }
            teleport(p, r.dim, r.pos, r.yaw, r.pitch);
            p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.PLAYERS, 0.6f, 1.2f);
            return;
        }

        // normal world → remember return spot + spawn a portal in front
        storeReturn(p);
        var look = p.getRotationVec(1f);
        var pos = p.getPos().add(look.multiply(1.5)).add(0, 0.1, 0);

        var portal = new VoidPortalEntity(VoidRegistry.VOID_PORTAL, p.getWorld());
        portal.setPos(pos.x, pos.y, pos.z);
        portal.setOwner(p.getUuid());
        p.getWorld().spawnEntity(portal);
        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_PORTAL_AMBIENT, SoundCategory.PLAYERS, 0.7f, 1.4f);
    }

    /** Called by the portal entity when a player touches it. */
    static void enterPortal(ServerPlayerEntity p) {
        var server = p.getServer(); if (server == null) return;
        ServerWorld w = server.getWorld(VOID_WORLD); if (w == null) return;

        teleport(p, VOID_WORLD, new Vec3d(0.5, 66.0, 0.5), p.getYaw(), p.getPitch());
        w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.PLAYERS, 0.7f, 0.9f);
    }

    private static void storeReturn(ServerPlayerEntity p) {
        RETURN.put(p.getUuid(), new ReturnSpot(
                p.getWorld().getRegistryKey(), p.getPos(), p.getYaw(), p.getPitch()));
    }

    private static void teleport(ServerPlayerEntity p, RegistryKey<World> dim, Vec3d pos, float yaw, float pitch) {
        var server = p.getServer(); if (server == null) return;
        ServerWorld target = server.getWorld(dim); if (target == null) return;
        p.teleport(target, pos.x, pos.y, pos.z, yaw, pitch);
    }

    /** Stamp a 100×100 basalt/mud island around (0,64,0). */
    private static void buildIsland(ServerWorld w) {
        int half = 50, baseY = 64;

        BlockState BASALT = Blocks.SMOOTH_BASALT.getDefaultState();
        BlockState MUD = Blocks.MUD.getDefaultState();
        BlockState BLACKSTONE = Blocks.BLACKSTONE.getDefaultState();

        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                double r = Math.sqrt(x*x + z*z) / half; if (r > 1.0) continue;
                double rim = 1.0 - r;
                double wobble = Math.sin(x * 0.25) * 0.5 + Math.cos(z * 0.25) * 0.5;
                int h = baseY + (int)Math.round(2 + 4 * rim + 1.5 * wobble * rim);

                for (int y = baseY - 6; y <= h; y++) {
                    BlockPos bp = new BlockPos(x, y, z);
                    if (y == h) {
                        w.setBlockState(bp, ((x + z) & 5) == 0 ? BASALT : MUD, 3);
                    } else if (y >= h - 2) {
                        w.setBlockState(bp, BASALT, 3);
                    } else {
                        w.setBlockState(bp, ((x ^ z) & 7) == 0 ? BLACKSTONE : BASALT, 3);
                    }
                }
            }
        }
        w.setSpawnPos(new BlockPos(0, baseY + 2, 0), 0.0f);
    }
}
