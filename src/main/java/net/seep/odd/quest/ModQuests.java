package net.seep.odd.quest;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.network.ServerPlayerEntity;


public final class ModQuests {
    private ModQuests() {
    }

    public static void init() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new QuestRegistry());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new QuestLevelRegistry());


        QuestManager.registerPackets();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            QuestNetworking.sendSync(handler.getPlayer(), false, -1);
            QuestManager.refreshBossBar(handler.getPlayer());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            QuestManager.abandonQuest(handler.getPlayer(), false);
            QuestManager.removeBossBar(handler.getPlayer());
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            QuestManager.removeBossBar(oldPlayer);
            QuestManager.abandonQuest(newPlayer, true);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                QuestManager.cancelIfOutsideAtheneum(player);
                QuestManager.tickPlayerQuest(player);
            }
        });

        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killedEntity) -> {
            if (entity instanceof ServerPlayerEntity player) {
                QuestManager.onKill(player, killedEntity);
            }
        });
    }
}
