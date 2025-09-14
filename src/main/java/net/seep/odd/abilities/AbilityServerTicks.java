// src/main/java/net/seep/odd/abilities/AbilityServerTicks.java
package net.seep.odd.abilities;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.seep.odd.abilities.power.TamerPower;

public final class AbilityServerTicks {
    private AbilityServerTicks() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                String cur = PowerAPI.get(p); // your existing API
                if (cur != null && "tamer".equals(cur)) {
                    TamerPower.serverTick(p); // calls TamerSummons.tick(...)
                }
            }
        });
    }
}
