package net.seep.odd.quest.types;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.librarian.LibrarianEntity;
import net.seep.odd.entity.robo_rascal.RoboRascalEntity;
import net.seep.odd.entity.scared_rascal_fight.ScaredRascalFightEntity;
import net.seep.odd.quest.ActiveQuestState;
import net.seep.odd.quest.PlayerQuestProfile;
import net.seep.odd.quest.QuestDefinition;
import net.seep.odd.quest.QuestManager;
import net.seep.odd.quest.QuestNetworking;
import net.seep.odd.quest.QuestObjectiveData;
import net.seep.odd.quest.QuestRegistry;
import org.jetbrains.annotations.Nullable;

public final class ScaredRascalTwoQuestLogic {
    private static final double START_RADIUS = 5.5D;
    private static final double AMBUSH_RADIUS = 6.5D;
    private static final double LIBRARIAN_REACH = 4.75D;

    private static final String INTRO_LINE = "yo I lowkey punched made fun of this dude online and he kinda pissed at me... he is small, care to help?";
    private static final String AMBUSH_LINE = "ok so the more I think about... I don't think he was that small";
    private static final String AFTER_FIGHT_LINE = "OH THANK GOD... can you get me back to the librarian?";

    private ScaredRascalTwoQuestLogic() {
    }

    public static void tick(ServerPlayerEntity player, ActiveQuestState active, QuestDefinition def) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }

        if (active.stage == ActiveQuestState.STAGE_GO_TO_AREA && active.hasTarget) {
            BlockPos target = new BlockPos(active.targetX, active.targetY, active.targetZ);
            if (player.getBlockPos().isWithinDistance(target, START_RADIUS)) {
                active.stage = ActiveQuestState.STAGE_SCARED_RASCAL_2_TALK_START;
                active.hasTarget = false;
                spawnScaredRascal(player, active, target);
                QuestManager.markDirty(player);
                QuestManager.refreshBossBar(player);
                QuestManager.syncQuest(player);
            }
            return;
        }

        ScaredRascalFightEntity rascal = getScaredRascal(player, active);
        if ((active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_2_TALK_START
                || active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_2_GO_TO_AMBUSH
                || active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_2_TALK_AMBUSH
                || active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_2_REVEAL
                || active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_2_FIGHT
                || active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_2_TALK_END
                || active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_2_ESCORT)
                && (rascal == null || rascal.isRemoved() || !rascal.isAlive())) {
            QuestManager.failActiveQuest(player, "The scared rascal was taken out.");
            return;
        }

        if (active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_2_GO_TO_AMBUSH && active.hasTarget) {
            BlockPos target = new BlockPos(active.targetX, active.targetY, active.targetZ);
            if (player.getBlockPos().isWithinDistance(target, AMBUSH_RADIUS) && rascal != null && rascal.squaredDistanceTo(player) <= 10.0D * 10.0D) {
                active.stage = ActiveQuestState.STAGE_SCARED_RASCAL_2_TALK_AMBUSH;
                active.hasTarget = false;
                active.progress = Math.max(active.progress, 2);
                rascal.setFollowActive(false);
                rascal.setEscortActive(false);
                QuestManager.markDirty(player);
                QuestManager.refreshBossBar(player);
                QuestManager.syncQuest(player);
            }
            return;
        }

        if (active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_2_REVEAL) {
            tickReveal(player, active, rascal);
            return;
        }

        if (active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_2_FIGHT) {
            tickFight(player, active, def, rascal);
            return;
        }

        if (active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_2_ESCORT) {
            tickEscort(player, active, def, rascal);
        }
    }

    public static boolean onRascalInteracted(ServerPlayerEntity player, ScaredRascalFightEntity rascal) {
        PlayerQuestProfile profile = QuestManager.profile(player);
        ActiveQuestState active = profile.activeQuest;
        if (active == null || active.readyToClaim) return false;

        QuestDefinition def = QuestRegistry.get(active.questId);
        if (def == null || def.objective.type != QuestObjectiveData.Type.SCARED_RASCAL_2) return false;
        if (rascal.getQuestOwnerUuid() == null || !rascal.getQuestOwnerUuid().equals(player.getUuid())) return false;
        if (active.scaredRascalFightId == null || !active.scaredRascalFightId.equals(rascal.getUuid())) return false;

        if (active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_2_TALK_START) {
            QuestNetworking.sendOpenScaredRascalDialog(player, rascal.getId(), "Scared Rascal 2", INTRO_LINE, "continue");
            return true;
        }
        if (active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_2_TALK_AMBUSH) {
            QuestNetworking.sendOpenScaredRascalDialog(player, rascal.getId(), "Scared Rascal 2", AMBUSH_LINE, "okayyy…");
            return true;
        }
        if (active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_2_TALK_END) {
            QuestNetworking.sendOpenScaredRascalDialog(player, rascal.getId(), "Scared Rascal 2", AFTER_FIGHT_LINE, "continue");
            return true;
        }
        return false;
    }

    public static void continueDialogue(ServerPlayerEntity player, int rascalEntityId) {
        PlayerQuestProfile profile = QuestManager.profile(player);
        ActiveQuestState active = profile.activeQuest;
        if (active == null || active.readyToClaim) return;

        QuestDefinition def = QuestRegistry.get(active.questId);
        if (def == null || def.objective.type != QuestObjectiveData.Type.SCARED_RASCAL_2) return;

        Entity entity = player.getWorld().getEntityById(rascalEntityId);
        if (!(entity instanceof ScaredRascalFightEntity rascal)) return;
        if (active.scaredRascalFightId == null || !active.scaredRascalFightId.equals(rascal.getUuid())) return;
        if (rascal.getQuestOwnerUuid() == null || !rascal.getQuestOwnerUuid().equals(player.getUuid())) return;

        if (active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_2_TALK_START) {
            beginTripToAmbush(player, active, rascal);
        } else if (active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_2_TALK_AMBUSH) {
            beginReveal(player, active, rascal);
        } else if (active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_2_TALK_END) {
            beginEscort(player, active, rascal);
        }
    }

    private static void spawnScaredRascal(ServerPlayerEntity player, ActiveQuestState active, BlockPos center) {
        if (!(player.getWorld() instanceof ServerWorld world) || active.scaredRascalFightId != null) return;

        BlockPos floor = QuestManager.findNearestFloor(world, center);
        if (floor == null) floor = center.down();

        ScaredRascalFightEntity rascal = ModEntities.SCARED_RASCAL_FIGHT.create(world);
        if (rascal == null) return;

        rascal.refreshPositionAndAngles(
                floor.getX() + 0.5D,
                floor.getY() + 1.0D,
                floor.getZ() + 0.5D,
                world.random.nextFloat() * 360.0F,
                0.0F
        );
        rascal.assignQuestOwner(player);
        rascal.setFollowActive(false);
        rascal.setEscortActive(false);
        world.spawnEntityAndPassengers(rascal);

        active.scaredRascalFightId = rascal.getUuid();
        active.spawnedEntityIds.add(rascal.getUuid());
    }

    private static void beginTripToAmbush(ServerPlayerEntity player, ActiveQuestState active, ScaredRascalFightEntity rascal) {
        BlockPos spot = QuestManager.pickQuestArea(player, 54.0D, 88.0D);
        active.stage = ActiveQuestState.STAGE_SCARED_RASCAL_2_GO_TO_AMBUSH;
        active.hasTarget = true;
        active.targetX = spot.getX();
        active.targetY = spot.getY();
        active.targetZ = spot.getZ();
        active.progress = Math.max(active.progress, 1);

        rascal.setFollowActive(true);
        rascal.setEscortActive(false);
        rascal.speak("lead the way...", 35);

        QuestManager.markDirty(player);
        QuestManager.refreshBossBar(player);
        QuestManager.syncQuest(player);
    }

    private static void beginReveal(ServerPlayerEntity player, ActiveQuestState active, ScaredRascalFightEntity rascal) {
        active.stage = ActiveQuestState.STAGE_SCARED_RASCAL_2_REVEAL;
        active.countdownTicks = 100;
        active.progress = Math.max(active.progress, 2);

        rascal.setFollowActive(false);
        rascal.setEscortActive(false);
        rascal.speak("uh.. yo..uh...", 60);

        QuestManager.markDirty(player);
        QuestManager.refreshBossBar(player);
        QuestManager.syncQuest(player);
    }

    private static void tickReveal(ServerPlayerEntity player, ActiveQuestState active, @Nullable ScaredRascalFightEntity rascal) {
        if (rascal == null) {
            QuestManager.failActiveQuest(player, "The scared rascal disappeared.");
            return;
        }

        if (active.countdownTicks == 40) {
            rascal.speak("YO HE GOT BIG OH SHI", 40);
        }

        if (active.countdownTicks > 0) {
            active.countdownTicks--;
        }

        if (active.countdownTicks <= 0) {
            startFight(player, active, rascal);
            return;
        }

        if (active.countdownTicks % 20 == 0) {
            QuestManager.syncQuest(player);
        }
    }

    private static void startFight(ServerPlayerEntity player, ActiveQuestState active, ScaredRascalFightEntity rascal) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }

        RoboRascalEntity robo = spawnRoboRascal(world, player, rascal);
        if (robo == null) {
            QuestManager.failActiveQuest(player, "Couldn't spawn the Robo Rascal.");
            return;
        }

        robo.assignQuestOwner(player);
        robo.setTargetRascalUuid(rascal.getUuid());
        world.spawnEntityAndPassengers(robo);

        rascal.setRoboRascalUuid(robo.getUuid());
        rascal.setFollowActive(true);
        rascal.setEscortActive(false);

        active.roboRascalId = robo.getUuid();
        active.spawnedEntityIds.add(robo.getUuid());
        active.stage = ActiveQuestState.STAGE_SCARED_RASCAL_2_FIGHT;
        active.countdownTicks = 0;

        QuestManager.markDirty(player);
        QuestManager.refreshBossBar(player);
        QuestManager.syncQuest(player);
    }

    @Nullable
    private static RoboRascalEntity spawnRoboRascal(ServerWorld world, ServerPlayerEntity player, ScaredRascalFightEntity rascal) {
        RoboRascalEntity robo = ModEntities.ROBO_RASCAL.create(world);
        if (robo == null) return null;

        Vec3d fromPlayerToRascal = rascal.getPos().subtract(player.getPos());
        Vec3d spawnDir = fromPlayerToRascal.lengthSquared() < 1.0E-6D
                ? player.getRotationVec(1.0F).negate()
                : fromPlayerToRascal.normalize();

        Vec3d desired = rascal.getPos().add(spawnDir.multiply(9.0D));
        BlockPos probe = BlockPos.ofFloored(desired.x, desired.y + 6.0D, desired.z);
        BlockPos floor = QuestManager.findNearestFloor(world, probe);
        if (floor == null) {
            floor = QuestManager.findSpawnFloorAround(world, rascal.getBlockPos(), 10, 24);
        }
        if (floor == null) {
            floor = rascal.getBlockPos();
        }

        robo.refreshPositionAndAngles(
                floor.getX() + 0.5D,
                floor.getY() + 1.0D,
                floor.getZ() + 0.5D,
                player.getYaw(),
                0.0F
        );
        return robo;
    }

    private static void tickFight(ServerPlayerEntity player, ActiveQuestState active, QuestDefinition def, @Nullable ScaredRascalFightEntity rascal) {
        if (rascal == null || !rascal.isAlive()) {
            QuestManager.failActiveQuest(player, "The scared rascal didn't make it.");
            return;
        }

        RoboRascalEntity robo = getRoboRascal(player, active);
        if (robo == null || robo.isRemoved() || !robo.isAlive()) {
            if (robo != null) {
                active.spawnedEntityIds.remove(robo.getUuid());
            }
            active.roboRascalId = null;
            rascal.setRoboRascalUuid(null);
            rascal.setFollowActive(false);

            active.stage = ActiveQuestState.STAGE_SCARED_RASCAL_2_TALK_END;
            active.progress = Math.max(active.progress, 3);
            active.hasTarget = false;

            player.sendMessage(Text.literal("You beat the Robo Rascal. Talk to the rascal."), true);
            QuestManager.markDirty(player);
            QuestManager.refreshBossBar(player);
            QuestManager.syncQuest(player);
        }
    }

    private static void beginEscort(ServerPlayerEntity player, ActiveQuestState active, ScaredRascalFightEntity rascal) {
        rascal.setFollowActive(false);
        rascal.setEscortActive(true);
        rascal.setRoboRascalUuid(null);
        rascal.speak("stay close...", 45);

        active.stage = ActiveQuestState.STAGE_SCARED_RASCAL_2_ESCORT;
        updateEscortTarget(player, active);

        QuestManager.markDirty(player);
        QuestManager.syncQuest(player);
    }

    private static void tickEscort(ServerPlayerEntity player, ActiveQuestState active, QuestDefinition def, @Nullable ScaredRascalFightEntity rascal) {
        if (rascal == null || !rascal.isAlive()) {
            QuestManager.failActiveQuest(player, "The scared rascal disappeared.");
            return;
        }

        updateEscortTarget(player, active);

        LibrarianEntity librarian = QuestManager.findNearestLibrarian(player);
        if (librarian == null) {
            return;
        }

        boolean rascalNear = rascal.squaredDistanceTo(librarian) <= LIBRARIAN_REACH * LIBRARIAN_REACH;
        boolean playerNear = player.squaredDistanceTo(librarian) <= 10.0D * 10.0D;
        if (!rascalNear || !playerNear) {
            return;
        }

        active.progress = def.goal();
        active.readyToClaim = true;
        active.stage = ActiveQuestState.STAGE_ACTIVE;
        active.hasTarget = false;

        active.spawnedEntityIds.remove(rascal.getUuid());
        active.scaredRascalFightId = null;
        QuestManager.poofRemove(rascal);

        player.sendMessage(Text.literal("Scared Rascal 2 complete. Claim your reward from the librarian."), true);
        QuestManager.markDirty(player);
        QuestManager.refreshBossBar(player);
        QuestManager.syncQuest(player);
    }

    private static void updateEscortTarget(ServerPlayerEntity player, ActiveQuestState active) {
        BlockPos librarianPos = QuestManager.findNearestLibrarianPos(player);
        if (librarianPos == null) {
            active.hasTarget = false;
            return;
        }
        active.hasTarget = true;
        active.targetX = librarianPos.getX();
        active.targetY = librarianPos.getY();
        active.targetZ = librarianPos.getZ();
    }

    @Nullable
    public static ScaredRascalFightEntity getScaredRascal(ServerPlayerEntity player, ActiveQuestState active) {
        Entity entity = QuestManager.findEntity(player, active.scaredRascalFightId);
        return entity instanceof ScaredRascalFightEntity rascal ? rascal : null;
    }

    @Nullable
    public static RoboRascalEntity getRoboRascal(ServerPlayerEntity player, ActiveQuestState active) {
        Entity entity = QuestManager.findEntity(player, active.roboRascalId);
        return entity instanceof RoboRascalEntity robo ? robo : null;
    }
}
