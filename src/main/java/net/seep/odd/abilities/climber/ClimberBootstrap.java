package net.seep.odd.abilities.climber;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.seep.odd.abilities.climber.net.ClimberClimbNetworking;
import net.seep.odd.abilities.power.ClimberPower;

public final class ClimberBootstrap {
    private static boolean inited = false;

    private ClimberBootstrap() {}

    /** Call ONCE from Oddities.onInitialize() */
    public static void initCommon() {
        if (inited) return;
        inited = true;

        // networking (client keyflags -> server)
        ClimberPower.registerNetworking();

        // sync the passive wall-climb permission to clients so multiplayer
        // movement prediction stays in lockstep with the server.
        ClimberClimbNetworking.registerServer();

        // guaranteed power ticking (no mixin needed)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity sp : server.getPlayerManager().getPlayerList()) {
                ClimberPower.serverTick(sp);
            }
        });
    }
}
