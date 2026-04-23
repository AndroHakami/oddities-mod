package net.seep.odd.abilities.shift;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.seep.odd.abilities.power.ShiftPower;

public final class ShiftNet {
    private ShiftNet() {}

    private static boolean serverRegistered = false;

    public static void registerServer() {
        if (serverRegistered) return;
        serverRegistered = true;

        ServerTickEvents.END_WORLD_TICK.register((ServerWorld world) -> {
            for (ServerPlayerEntity player : world.getPlayers()) {
                ShiftPower.serverTick(player);
            }
        });
    }
}
