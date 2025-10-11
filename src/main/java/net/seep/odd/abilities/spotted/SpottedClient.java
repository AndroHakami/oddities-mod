package net.seep.odd.abilities.spotted;

import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import net.minecraft.entity.LivingEntity;

import java.util.UUID;

/** Client-only memory of who is invisible due to Spotted Phantom (not generic potions). */
public final class SpottedClient {
    private SpottedClient() {}
    private static final Object2BooleanOpenHashMap<UUID> ACTIVE = new Object2BooleanOpenHashMap<>();

    public static boolean isSpottedInvisible(LivingEntity e) {
        return ACTIVE.getOrDefault(e.getUuid(), false);
    }
    public static void set(UUID id, boolean active) {
        if (active) ACTIVE.put(id, true);
        else ACTIVE.removeBoolean(id);
    }
    public static void clearAll() {
        ACTIVE.clear();
    }
}
