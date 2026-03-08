package net.seep.odd.abilities.wizard;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/**
 * Server-side: prevents Wizard normal swing-casts from firing when a combo is being confirmed.
 * (Fixes "left click confirm also shoots".)
 */
public final class WizardSwingSuppress {
    private WizardSwingSuppress() {}

    private static final Object2LongOpenHashMap<UUID> BLOCK_UNTIL_TICK = new Object2LongOpenHashMap<>();

    /** Block normal cast for the next N ticks (inclusive). */
    public static void blockNormalCasts(ServerPlayerEntity p, int ticks) {
        if (p == null) return;
        long now = p.getServerWorld().getTime();
        long until = now + Math.max(0, ticks);

        UUID id = p.getUuid();
        long cur = BLOCK_UNTIL_TICK.getOrDefault(id, Long.MIN_VALUE);
        if (until > cur) BLOCK_UNTIL_TICK.put(id, until);
    }

    /** True if normal cast should be blocked at a given world tick. */
    public static boolean isBlockedAt(ServerPlayerEntity p, long tick) {
        if (p == null) return false;
        long until = BLOCK_UNTIL_TICK.getOrDefault(p.getUuid(), Long.MIN_VALUE);
        return until >= tick;
    }

    public static void clear(ServerPlayerEntity p) {
        if (p == null) return;
        BLOCK_UNTIL_TICK.removeLong(p.getUuid());
    }
}