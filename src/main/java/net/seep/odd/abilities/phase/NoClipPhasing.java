package net.seep.odd.abilities.phase;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.UUID;

public final class NoClipPhasing {
    private NoClipPhasing() {}

    private static final Object2LongOpenHashMap<UUID> END_TICK = new Object2LongOpenHashMap<>();

    public static boolean isPhasing(PlayerEntity p) {
        if (!(p instanceof ServerPlayerEntity sp)) return false;
        ServerWorld w = sp.getServerWorld();
        long end = END_TICK.getOrDefault(sp.getUuid(), Long.MIN_VALUE);
        return end != Long.MIN_VALUE && w.getTime() < end;
    }

    public static void startPhasing(ServerPlayerEntity p, int durationTicks) {
        if (p == null) return;
        long end = p.getServerWorld().getTime() + durationTicks;
        END_TICK.put(p.getUuid(), end);
    }

    public static void stopPhasing(ServerPlayerEntity p) {
        if (p == null) return;
        END_TICK.removeLong(p.getUuid());
    }

    public static void clear(UUID id) {
        if (id == null) return;
        END_TICK.removeLong(id);
    }
}
