// src/main/java/net/seep/odd/abilities/PowerlessEnforcer.java
package net.seep.odd.abilities;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.seep.odd.status.ModStatusEffects;

public final class PowerlessEnforcer {
    private PowerlessEnforcer() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity sp : server.getPlayerManager().getPlayerList()) {
                if (sp.hasStatusEffect(ModStatusEffects.POWERLESS)) {
                    PowerAPI.enforcePowerlessTick(sp);
                }
            }
        });
    }
}