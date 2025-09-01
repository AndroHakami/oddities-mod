package net.seep.odd.abilities.manifest;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class ManifestServerCooldowns {
    private static final Map<UUID, Integer> map = new HashMap<>();

    static boolean onCooldown(ServerPlayerEntity p) {
        return map.getOrDefault(p.getUuid(), 0) > 0;
    }

    static void arm(ServerPlayerEntity p, int ticks) {
        map.put(p.getUuid(), ticks);
    }

    /** Tick down; call once each server tick. */
    static void tick() {
        if (map.isEmpty()) return;
        var it = map.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            int v = e.getValue() - 1;
            if (v <= 0) it.remove(); else e.setValue(v);
        }
    }
}
