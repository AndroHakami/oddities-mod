package net.seep.odd.block.gate;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.Oddities;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class GateTeleportQueue {
    private GateTeleportQueue() {}

    private static boolean inited = false;

    private record Pending(RegistryKey<World> dim, Vec3d pos, float yaw, float pitch, Text msg) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    /** Call once from your mod init. */
    public static void init() {
        if (inited) return;
        inited = true;

        ServerTickEvents.END_SERVER_TICK.register(GateTeleportQueue::flush);
    }

    /** Queue a teleport to run at END_SERVER_TICK (reliable vs player movement packets). */
    public static void queue(ServerPlayerEntity p, RegistryKey<World> dim, Vec3d pos, float yaw, float pitch, Text msg) {
        PENDING.put(p.getUuid(), new Pending(dim, pos, yaw, pitch, msg));
    }

    private static void flush(MinecraftServer server) {
        if (PENDING.isEmpty()) return;

        Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            it.remove();

            UUID id = e.getKey();
            Pending tp = e.getValue();

            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null) continue;

            ServerWorld dst = server.getWorld(tp.dim);
            if (dst == null) {
                Oddities.LOGGER.error("[Gate] Teleport failed: dst world is null for {}", tp.dim.getValue());
                p.sendMessage(Text.literal("Gate: destination world not loaded (check logs)."), true);
                continue;
            }

            try {
                p.teleport(dst, tp.pos.x, tp.pos.y, tp.pos.z, tp.yaw, tp.pitch);
                if (tp.msg != null) p.sendMessage(tp.msg, true);
                Oddities.LOGGER.info("[Gate] Teleported {} -> {}", p.getName().getString(), dst.getRegistryKey().getValue());
            } catch (Throwable t) {
                Oddities.LOGGER.error("[Gate] Teleport threw", t);
            }
        }
    }
}
