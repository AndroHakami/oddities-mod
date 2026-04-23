package net.seep.odd.quest.types;

import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.rascal.RascalEntity;
import net.seep.odd.quest.ActiveQuestState;
import net.seep.odd.quest.PlayerQuestProfile;
import net.seep.odd.quest.QuestDefinition;
import net.seep.odd.quest.QuestManager;
import net.seep.odd.quest.QuestObjectiveData;

public final class RoundupQuestLogic {
    private RoundupQuestLogic() {}

    public static void tick(ServerPlayerEntity player, ActiveQuestState active, QuestDefinition def) {
        if (active.stage == ActiveQuestState.STAGE_GO_TO_AREA && active.hasTarget) {
            BlockPos target = new BlockPos(active.targetX, active.targetY, active.targetZ);
            if (player.getBlockPos().isWithinDistance(target, 5.5D)) {
                active.stage = ActiveQuestState.STAGE_ROUNDUP;
                active.hasTarget = false;
                spawnQuestRascals(player, def, active, target);
                QuestManager.markDirty(player);
                QuestManager.refreshBossBar(player);
                QuestManager.syncQuest(player);
            }
        }
    }

    public static boolean canCalmRascal(ServerPlayerEntity player, RascalEntity rascal) {
        PlayerQuestProfile profile = QuestManager.profile(player);
        ActiveQuestState active = profile.activeQuest;
        if (active == null || active.readyToClaim) return false;

        QuestDefinition def = net.seep.odd.quest.QuestRegistry.get(active.questId);
        if (def == null || def.objective.type != QuestObjectiveData.Type.CALM_RETURN) return false;
        if (active.stage != ActiveQuestState.STAGE_ROUNDUP) return false;
        if (!Registries.ENTITY_TYPE.getId(rascal.getType()).equals(new Identifier(def.objective.entity))) return false;
        return rascal.getQuestOwnerUuid() != null && rascal.getQuestOwnerUuid().equals(player.getUuid());
    }

    public static void onRascalReturned(ServerPlayerEntity player, RascalEntity rascal) {
        PlayerQuestProfile profile = QuestManager.profile(player);
        ActiveQuestState active = profile.activeQuest;
        if (active == null || active.readyToClaim) return;

        QuestDefinition def = net.seep.odd.quest.QuestRegistry.get(active.questId);
        if (def == null || def.objective.type != QuestObjectiveData.Type.CALM_RETURN) return;

        active.progress = Math.min(active.progress + 1, def.goal());
        active.readyToClaim = active.progress >= def.goal();
        QuestManager.poofRemove(rascal);
        QuestManager.markDirty(player);
        QuestManager.refreshBossBar(player);
        QuestManager.syncQuest(player);
    }

    private static void spawnQuestRascals(ServerPlayerEntity player, QuestDefinition def, ActiveQuestState active, BlockPos center) {
        if (!(player.getWorld() instanceof ServerWorld world) || !active.spawnedEntityIds.isEmpty()) return;
        int count = Math.max(1, def.goal());
        for (int i = 0; i < count; i++) {
            BlockPos floor = QuestManager.findSpawnFloorAround(world, center, 10, 20);
            if (floor == null) floor = center;
            RascalEntity rascal = ModEntities.RASCAL.create(world);
            if (rascal == null) continue;
            rascal.refreshPositionAndAngles(floor.getX() + 0.5D, floor.getY() + 1.0D, floor.getZ() + 0.5D,
                    world.random.nextFloat() * 360.0F, 0.0F);
            rascal.assignQuestOwner(player);
            world.spawnEntityAndPassengers(rascal);
            active.spawnedEntityIds.add(rascal.getUuid());
        }
    }
}
