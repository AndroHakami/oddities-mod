package net.seep.odd.shop;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public final class ShopServerTick {

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ShopServerTick::tick);
    }

    private static void tick(MinecraftServer server) {
        // every 20 ticks sync balance to anyone with shop open
        if (server.getTicks() % 20 != 0) return;

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.currentScreenHandler != null &&
                    p.currentScreenHandler.getClass().getName().endsWith("DabloonsMachineScreenHandler")) {
                ShopNetworking.sendBalance(p);
            }
        }
    }

    private ShopServerTick() {}
}
