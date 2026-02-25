package net.seep.odd.event.alien;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.seep.odd.event.alien.net.AlienInvasionNet;

public final class AlienInvasionInit {
    private AlienInvasionInit() {}

    private static final AlienInvasionManager MANAGER = new AlienInvasionManager();

    public static AlienInvasionManager manager() {
        return MANAGER;
    }

    public static void init() {
        // world tick (only overworld)
        ServerTickEvents.END_WORLD_TICK.register((ServerWorld w) -> {
            if (w.getRegistryKey() == World.OVERWORLD) {
                MANAGER.tick(w);
            }
        });

        // join sync
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.getPlayer();
            if (p.getWorld().getRegistryKey() == World.OVERWORLD) {
                AlienInvasionNet.sendState(p, MANAGER.isActive(), MANAGER.wave(), MANAGER.maxWaves());
            }
        });
    }
}