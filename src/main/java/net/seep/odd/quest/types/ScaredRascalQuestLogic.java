package net.seep.odd.quest.types;

import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.ClearTitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.librarian.LibrarianEntity;
import net.seep.odd.entity.rake.RakeEntity;
import net.seep.odd.entity.scared_rascal.ScaredRascalEntity;
import net.seep.odd.quest.ActiveQuestState;
import net.seep.odd.quest.PlayerQuestProfile;
import net.seep.odd.quest.QuestDefinition;
import net.seep.odd.quest.QuestManager;
import net.seep.odd.quest.QuestNetworking;
import net.seep.odd.quest.QuestObjectiveData;
import org.jetbrains.annotations.Nullable;

public final class ScaredRascalQuestLogic {
    private static final int CHASE_DURATION_TICKS = 20 * 60;
    private static final double START_RADIUS = 5.5D;
    private static final double LIBRARIAN_REACH = 4.75D;

    private static final String INTRO_LINE = "yo… someone is been on my tail.. care to help?";
    private static final String AFTER_CHASE_LINE = "THANK YOU... can you walk me back to the librarian?";

    private ScaredRascalQuestLogic() {
    }

    public static void tick(ServerPlayerEntity player, ActiveQuestState active, QuestDefinition def) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }

        if (active.stage == ActiveQuestState.STAGE_GO_TO_AREA && active.hasTarget) {
            BlockPos target = new BlockPos(active.targetX, active.targetY, active.targetZ);
            if (player.getBlockPos().isWithinDistance(target, START_RADIUS)) {
                active.stage = ActiveQuestState.STAGE_SCARED_RASCAL_TALK_START;
                active.hasTarget = false;
                spawnScaredRascal(player, active, target);
                QuestManager.markDirty(player);
                QuestManager.refreshBossBar(player);
                QuestManager.syncQuest(player);
            }
            return;
        }

        ScaredRascalEntity rascal = getScaredRascal(player, active);
        if ((active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_TALK_START
                || active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_CHASE
                || active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_TALK_END
                || active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_ESCORT)
                && (rascal == null || rascal.isRemoved())) {
            QuestManager.failActiveQuest(player, "The scared rascal disappeared.");
            return;
        }

        if (active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_CHASE) {
            tickChase(player, active, def, rascal);
            return;
        }

        if (active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_ESCORT) {
            tickEscort(player, active, def, rascal);
        }
    }

    public static boolean onRascalInteracted(ServerPlayerEntity player, ScaredRascalEntity rascal) {
        PlayerQuestProfile profile = QuestManager.profile(player);
        ActiveQuestState active = profile.activeQuest;
        if (active == null || active.readyToClaim) return false;

        QuestDefinition def = net.seep.odd.quest.QuestRegistry.get(active.questId);
        if (def == null || def.objective.type != QuestObjectiveData.Type.SCARED_RASCAL) return false;
        if (rascal.getQuestOwnerUuid() == null || !rascal.getQuestOwnerUuid().equals(player.getUuid())) return false;
        if (active.scaredRascalId == null || !active.scaredRascalId.equals(rascal.getUuid())) return false;

        if (active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_TALK_START) {
            QuestNetworking.sendOpenScaredRascalDialog(player, rascal.getId(), INTRO_LINE);
            return true;
        }
        if (active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_TALK_END) {
            QuestNetworking.sendOpenScaredRascalDialog(player, rascal.getId(), AFTER_CHASE_LINE);
            return true;
        }
        return false;
    }

    public static void continueDialogue(ServerPlayerEntity player, int rascalEntityId) {
        PlayerQuestProfile profile = QuestManager.profile(player);
        ActiveQuestState active = profile.activeQuest;
        if (active == null || active.readyToClaim) return;

        QuestDefinition def = net.seep.odd.quest.QuestRegistry.get(active.questId);
        if (def == null || def.objective.type != QuestObjectiveData.Type.SCARED_RASCAL) return;

        Entity entity = player.getWorld().getEntityById(rascalEntityId);
        if (!(entity instanceof ScaredRascalEntity rascal)) return;
        if (active.scaredRascalId == null || !active.scaredRascalId.equals(rascal.getUuid())) return;
        if (rascal.getQuestOwnerUuid() == null || !rascal.getQuestOwnerUuid().equals(player.getUuid())) return;

        if (active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_TALK_START) {
            beginChase(player, active, rascal);
        } else if (active.stage == ActiveQuestState.STAGE_SCARED_RASCAL_TALK_END) {
            beginEscort(player, active, rascal);
        }
    }

    public static void onRakeCaught(ServerPlayerEntity player) {
        PlayerQuestProfile profile = QuestManager.profile(player);
        ActiveQuestState active = profile.activeQuest;
        if (active == null) return;

        QuestDefinition def = net.seep.odd.quest.QuestRegistry.get(active.questId);
        if (def == null || def.objective.type != QuestObjectiveData.Type.SCARED_RASCAL) return;

        player.networkHandler.sendPacket(new ClearTitleS2CPacket(true));
        QuestManager.failActiveQuest(player, "The Rake caught up.");
    }

    private static void sendRunTitle(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(0, 25, 10));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("RUN!!").formatted(Formatting.RED, Formatting.BOLD)));
    }

    private static void spawnScaredRascal(ServerPlayerEntity player, ActiveQuestState active, BlockPos center) {
        if (!(player.getWorld() instanceof ServerWorld world)) return;
        if (active.scaredRascalId != null) return;

        BlockPos floor = QuestManager.findNearestFloor(world, center);
        if (floor == null) floor = center.down();

        ScaredRascalEntity rascal = ModEntities.SCARED_RASCAL.create(world);
        if (rascal == null) return;

        rascal.refreshPositionAndAngles(
                floor.getX() + 0.5D,
                floor.getY() + 1.0D,
                floor.getZ() + 0.5D,
                world.random.nextFloat() * 360.0F,
                0.0F
        );
        rascal.assignQuestOwner(player);
        rascal.setChaseActive(false);
        rascal.setEscortActive(false);
        world.spawnEntityAndPassengers(rascal);

        active.scaredRascalId = rascal.getUuid();
        active.spawnedEntityIds.add(rascal.getUuid());
    }

    private static void beginChase(ServerPlayerEntity player, ActiveQuestState active, ScaredRascalEntity rascal) {
        if (!(player.getWorld() instanceof ServerWorld world)) return;

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 40, 0, false, false, true));
        rascal.speak("OH SHI RUNNN", 40);
        rascal.setEscortActive(false);
        rascal.setChaseActive(true);

        RakeEntity rake = spawnRakeBehind(world, player, rascal);
        if (rake == null) {
            QuestManager.failActiveQuest(player, "Couldn't spawn the Rake.");
            return;
        }

        rake.assignQuestOwner(player);
        rake.setTargetRascalUuid(rascal.getUuid());
        world.spawnEntityAndPassengers(rake);

        rascal.setRakeUuid(rake.getUuid());

        active.rakeId = rake.getUuid();
        active.spawnedEntityIds.add(rake.getUuid());
        active.stage = ActiveQuestState.STAGE_SCARED_RASCAL_CHASE;
        active.countdownTicks = CHASE_DURATION_TICKS;
        active.progress = 0;

        sendRunTitle(player);

        QuestManager.markDirty(player);
        QuestManager.refreshBossBar(player);
        QuestManager.syncQuest(player);
    }

    @Nullable
    private static RakeEntity spawnRakeBehind(ServerWorld world, ServerPlayerEntity player, ScaredRascalEntity rascal) {
        RakeEntity rake = ModEntities.RAKE.create(world);
        if (rake == null) return null;

        Vec3d fromPlayerToRascal = rascal.getPos().subtract(player.getPos());
        Vec3d spawnDir;
        if (fromPlayerToRascal.lengthSquared() < 1.0E-6D) {
            spawnDir = player.getRotationVec(1.0F).negate();
        } else {
            spawnDir = fromPlayerToRascal.normalize();
        }

        double spawnDist = 8.5D;
        Vec3d desired = rascal.getPos().add(spawnDir.multiply(spawnDist));
        BlockPos floor = findRakeSpawnFloor(world, desired, rascal.getBlockPos());
        if (floor == null) {
            floor = rascal.getBlockPos();
        }

        rake.refreshPositionAndAngles(
                floor.getX() + 0.5D,
                floor.getY() + 1.0D,
                floor.getZ() + 0.5D,
                player.getYaw(),
                0.0F
        );
        return rake;
    }

    @Nullable
    private static BlockPos findRakeSpawnFloor(ServerWorld world, Vec3d desired, BlockPos rascalPos) {
        BlockPos desiredPos = BlockPos.ofFloored(desired.x, desired.y + 6.0D, desired.z);
        BlockPos floor = QuestManager.findNearestFloor(world, desiredPos);
        if (isGoodRakeSpawn(floor, rascalPos)) {
            return floor;
        }

        for (int radius = 4; radius <= 18; radius += 2) {
            for (int dx = -radius; dx <= radius; dx += 2) {
                for (int dz = -radius; dz <= radius; dz += 2) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    BlockPos probe = desiredPos.add(dx, 0, dz);
                    floor = QuestManager.findNearestFloor(world, probe);
                    if (isGoodRakeSpawn(floor, rascalPos)) {
                        return floor;
                    }
                }
            }
        }

        floor = QuestManager.findSpawnFloorAround(world, desiredPos, 4, 16);
        if (isGoodRakeSpawn(floor, rascalPos)) {
            return floor;
        }

        return null;
    }

    private static boolean isGoodRakeSpawn(@Nullable BlockPos floor, BlockPos rascalPos) {
        return floor != null && !floor.isWithinDistance(rascalPos, 4.5D);
    }

    private static void tickChase(ServerPlayerEntity player, ActiveQuestState active, QuestDefinition def, @Nullable ScaredRascalEntity rascal) {
        if (rascal == null) {
            QuestManager.failActiveQuest(player, "The scared rascal disappeared.");
            return;
        }

        RakeEntity rake = getRake(player, active);
        if (rake == null || rake.isRemoved()) {
            QuestManager.failActiveQuest(player, "The Rake vanished and the chase broke.");
            return;
        }

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 10, 1, false, false, false));
        rascal.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 10, 1, false, false, false));
        rake.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 10, 1, false, false, false));

        if (active.countdownTicks > 0) {
            active.countdownTicks--;
        }

        if (active.countdownTicks % 20 == 0) {
            QuestManager.syncQuest(player);
        }

        if (active.countdownTicks <= 0) {
            QuestManager.poofRemove(rake);
            active.spawnedEntityIds.remove(rake.getUuid());
            active.rakeId = null;
            rascal.setRakeUuid(null);
            rascal.setChaseActive(false);

            player.networkHandler.sendPacket(new ClearTitleS2CPacket(true));
            active.stage = ActiveQuestState.STAGE_SCARED_RASCAL_TALK_END;
            active.progress = Math.min(Math.max(active.progress, 1), def.goal());

            player.sendMessage(Text.literal("You lost it. Talk to the rascal."), true);

            QuestManager.markDirty(player);
            QuestManager.refreshBossBar(player);
            QuestManager.syncQuest(player);
        }
    }

    private static void beginEscort(ServerPlayerEntity player, ActiveQuestState active, ScaredRascalEntity rascal) {
        rascal.setChaseActive(false);
        rascal.setEscortActive(true);
        rascal.setRakeUuid(null);
        rascal.speak("Stay close...", 45);

        active.stage = ActiveQuestState.STAGE_SCARED_RASCAL_ESCORT;
        updateEscortTarget(player, active);

        QuestManager.markDirty(player);
        QuestManager.syncQuest(player);
    }

    private static void tickEscort(ServerPlayerEntity player, ActiveQuestState active, QuestDefinition def, @Nullable ScaredRascalEntity rascal) {
        if (rascal == null) {
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

        if (rascalNear && playerNear) {
            active.progress = def.goal();
            active.readyToClaim = true;

            active.spawnedEntityIds.remove(rascal.getUuid());
            active.scaredRascalId = null;
            QuestManager.poofRemove(rascal);

            finishQuestImmediately(player, def, active);
        }
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

    private static void finishQuestImmediately(ServerPlayerEntity player, QuestDefinition def, ActiveQuestState active) {
        PlayerQuestProfile profile = QuestManager.profile(player);

        QuestManager.cleanupQuestEntities(player, active);
        for (net.seep.odd.quest.QuestRewardData reward : def.rewards) {
            reward.grant(player);
        }
        profile.questXp += Math.max(0, def.questXp);
        profile.claimedQuestIds.add(def.id);
        profile.activeQuest = null;

        QuestManager.markDirty(player);
        QuestManager.removeBossBar(player);
        player.sendMessage(Text.literal("Scared Rascal completed."), true);
        QuestManager.syncQuest(player);
    }

    @Nullable
    public static ScaredRascalEntity getScaredRascal(ServerPlayerEntity player, ActiveQuestState active) {
        Entity entity = QuestManager.findEntity(player, active.scaredRascalId);
        return entity instanceof ScaredRascalEntity rascal ? rascal : null;
    }

    @Nullable
    public static RakeEntity getRake(ServerPlayerEntity player, ActiveQuestState active) {
        Entity entity = QuestManager.findEntity(player, active.rakeId);
        return entity instanceof RakeEntity rake ? rake : null;
    }
}
