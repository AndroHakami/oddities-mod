package net.seep.odd.quest.types;

import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.ClearTitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.entity.librarian.LibrarianEntity;
import net.seep.odd.entity.race_rascal.RaceRascalEntity;
import net.seep.odd.entity.star_ride.StarRideEntity;
import net.seep.odd.quest.ActiveQuestState;
import net.seep.odd.quest.QuestDefinition;
import net.seep.odd.quest.QuestManager;
import net.seep.odd.sound.ModSounds;

public final class RaceQuestLogic {
    private static final double FINISH_RADIUS = 4.75D;
    private static final double REMOUNT_RADIUS_SQ = 4.5D * 4.5D;
    private static final double FAIL_DISTANCE_SQ = 40.0D * 40.0D;

    private RaceQuestLogic() {}

    public static void tick(ServerPlayerEntity player, ActiveQuestState active, QuestDefinition def) {
        if (active.readyToClaim || active.stage == ActiveQuestState.STAGE_ACTIVE) {
            return;
        }

        if (active.stage == ActiveQuestState.STAGE_GO_TO_AREA && active.hasTarget) {
            BlockPos target = new BlockPos(active.targetX, active.targetY, active.targetZ);
            if (player.getBlockPos().isWithinDistance(target, 5.5D)) {
                active.stage = ActiveQuestState.STAGE_WAIT_MOUNT;
                active.hasTarget = false;
                spawnRaceSetup(player, active, target);
                QuestManager.markDirty(player);
                QuestManager.syncQuest(player);
            }
            return;
        }

        StarRideEntity playerRide = QuestManager.getStarRide(player, active.playerRideId);
        StarRideEntity rivalRide = QuestManager.getStarRide(player, active.rivalRideId);
        RaceRascalEntity rivalRascal = QuestManager.getRaceRascal(player, active.rivalRascalId);

        if (playerRide == null || rivalRide == null || rivalRascal == null
                || playerRide.isRemoved() || rivalRide.isRemoved() || rivalRascal.isRemoved()) {
            QuestManager.failActiveQuest(player, "Return to the librarian to restart quest.");
            return;
        }

        if (rivalRascal.getVehicle() != rivalRide) {
            rivalRascal.startRiding(rivalRide, true);
        }

        LibrarianEntity librarian = QuestManager.findNearestLibrarian(player);
        if (librarian == null) return;

        BlockPos librarianPos = librarian.getBlockPos();

        if (active.stage == ActiveQuestState.STAGE_WAIT_MOUNT) {
            playerRide.setQuestLocked(true);
            rivalRide.setQuestLocked(true);
            playerRide.setRaceMode(false);
            rivalRide.setRaceMode(false);

            rivalRascal.setRaceTarget(librarianPos);
            rivalRascal.setRaceReleased(false);
            rivalRascal.primeRacePath();

            rivalRide.configureRaceAi(librarianPos);

            if (player.hasVehicle() && player.getVehicle() == playerRide) {
                active.stage = ActiveQuestState.STAGE_COUNTDOWN;
                active.countdownTicks = 60;
                sendCountdownStep(player, 3);
                QuestManager.markDirty(player);
                QuestManager.syncQuest(player);
            }
            return;
        }

        if (active.stage == ActiveQuestState.STAGE_COUNTDOWN) {
            playerRide.setQuestLocked(true);
            rivalRide.setQuestLocked(true);
            playerRide.setRaceMode(false);
            rivalRide.setRaceMode(false);

            rivalRascal.setRaceTarget(librarianPos);
            rivalRascal.setRaceReleased(false);
            rivalRascal.primeRacePath();

            rivalRide.configureRaceAi(librarianPos);

            if (!(player.hasVehicle() && player.getVehicle() == playerRide)) {
                if (playerRide.squaredDistanceTo(player) <= REMOUNT_RADIUS_SQ && !player.hasVehicle()) {
                    player.startRiding(playerRide, true);
                } else if (playerRide.squaredDistanceTo(player) > FAIL_DISTANCE_SQ) {
                    active.stage = ActiveQuestState.STAGE_WAIT_MOUNT;
                    active.countdownTicks = 0;
                    clearRaceTitle(player);
                    QuestManager.markDirty(player);
                    QuestManager.syncQuest(player);
                }
                return;
            }

            playerRide.setVelocity(0.0D, 0.0D, 0.0D);
            rivalRide.setVelocity(0.0D, 0.0D, 0.0D);

            active.countdownTicks--;
            if (active.countdownTicks == 40) sendCountdownStep(player, 2);
            if (active.countdownTicks == 20) sendCountdownStep(player, 1);

            if (active.countdownTicks <= 0) {
                active.stage = ActiveQuestState.STAGE_RACING;

                playerRide.setQuestLocked(false);
                rivalRide.setQuestLocked(false);

                playerRide.setRaceMode(true);
                rivalRide.setRaceMode(true);

                rivalRascal.setRaceTarget(librarianPos);
                rivalRascal.setRaceReleased(true);

                rivalRide.configureRaceAi(librarianPos);

                sendGoTitle(player);
                QuestManager.markDirty(player);
                QuestManager.syncQuest(player);
            }
            return;
        }

        if (active.stage == ActiveQuestState.STAGE_RACING) {
            rivalRide.setQuestLocked(false);
            rivalRide.setRaceMode(true);
            rivalRide.configureRaceAi(librarianPos);

            rivalRascal.setRaceTarget(librarianPos);
            rivalRascal.setRaceReleased(true);

            if (isPlayerAtFinish(player, playerRide, librarian)) {
                QuestManager.cleanupQuestEntities(player, active);
                clearRaceTitle(player);
                active.progress = def.goal();
                active.readyToClaim = true;
                active.stage = ActiveQuestState.STAGE_ACTIVE;
                player.sendMessage(Text.literal("You won the race! Return to the librarian to claim your reward."), true);
                QuestManager.markDirty(player);
                QuestManager.refreshBossBar(player);
                QuestManager.syncQuest(player);
                return;
            }

            if (!player.hasVehicle() || player.getVehicle() != playerRide) {
                if (playerRide.squaredDistanceTo(player) > FAIL_DISTANCE_SQ) {
                    QuestManager.failActiveQuest(player, "Return to the librarian to restart quest.");
                    return;
                }
            }

            if (isRivalAtFinish(rivalRide, rivalRascal, librarian)) {
                QuestManager.failActiveQuest(player, "The rascal won! Return to the librarian to restart quest.");
            }
        }
    }

    private static boolean isPlayerAtFinish(ServerPlayerEntity player, StarRideEntity playerRide, LibrarianEntity librarian) {
        return isWithinFinish(player.getX(), player.getY(), player.getZ(), librarian)
                || (playerRide != null && isWithinFinish(playerRide.getX(), playerRide.getY(), playerRide.getZ(), librarian));
    }

    private static boolean isRivalAtFinish(StarRideEntity rivalRide, RaceRascalEntity rivalRascal, LibrarianEntity librarian) {
        return isWithinFinish(rivalRide.getX(), rivalRide.getY(), rivalRide.getZ(), librarian)
                || isWithinFinish(rivalRascal.getX(), rivalRascal.getY(), rivalRascal.getZ(), librarian);
    }

    private static boolean isWithinFinish(double x, double y, double z, LibrarianEntity librarian) {
        double dx = x - librarian.getX();
        double dy = y - librarian.getY();
        double dz = z - librarian.getZ();
        return dx * dx + dy * dy + dz * dz <= FINISH_RADIUS * FINISH_RADIUS;
    }

    private static void spawnRaceSetup(ServerPlayerEntity player, ActiveQuestState active, BlockPos center) {
        if (!(player.getWorld() instanceof ServerWorld world) || !active.spawnedEntityIds.isEmpty()) return;

        BlockPos baseFloor = QuestManager.findNearestFloor(world, center);
        if (baseFloor == null) baseFloor = center.down();

        BlockPos leftFloor = QuestManager.findNearestFloor(world, baseFloor.add(-2, 2, 0));
        BlockPos rightFloor = QuestManager.findNearestFloor(world, baseFloor.add(2, 2, 0));
        if (leftFloor == null) leftFloor = baseFloor;
        if (rightFloor == null) rightFloor = baseFloor;

        StarRideEntity playerRide = createStarRide(world);
        StarRideEntity rivalRide = createStarRide(world);
        RaceRascalEntity rivalRascal = createRaceRascal(world);
        if (playerRide == null || rivalRide == null || rivalRascal == null) return;

        playerRide.refreshPositionAndAngles(leftFloor.getX() + 0.5D, leftFloor.getY() + 1.0D, leftFloor.getZ() + 0.5D, 180.0F, 0.0F);
        playerRide.assignQuestOwner(player);
        playerRide.setQuestLocked(true);
        playerRide.setPlayerMountable(true);
        playerRide.setRaceMode(false);
        playerRide.configureRaceAi(null);

        rivalRide.refreshPositionAndAngles(rightFloor.getX() + 0.5D, rightFloor.getY() + 1.0D, rightFloor.getZ() + 0.5D, 180.0F, 0.0F);
        rivalRide.assignQuestOwner(player);
        rivalRide.setQuestLocked(true);
        rivalRide.setPlayerMountable(false);
        rivalRide.setRaceMode(false);
        rivalRide.configureRaceAi(null);

        rivalRascal.refreshPositionAndAngles(rightFloor.getX() + 0.5D, rightFloor.getY() + 1.85D, rightFloor.getZ() + 0.5D, 180.0F, 0.0F);
        rivalRascal.assignQuestOwner(player);
        rivalRascal.setRaceReleased(false);

        world.spawnEntity(playerRide);
        world.spawnEntity(rivalRide);
        world.spawnEntity(rivalRascal);
        rivalRascal.startRiding(rivalRide, true);

        active.playerRideId = playerRide.getUuid();
        active.rivalRideId = rivalRide.getUuid();
        active.rivalRascalId = rivalRascal.getUuid();
        active.spawnedEntityIds.add(playerRide.getUuid());
        active.spawnedEntityIds.add(rivalRide.getUuid());
        active.spawnedEntityIds.add(rivalRascal.getUuid());
    }

    private static StarRideEntity createStarRide(ServerWorld world) {
        Entity raw = net.minecraft.registry.Registries.ENTITY_TYPE
                .get(new net.minecraft.util.Identifier("odd", "star_ride"))
                .create(world);
        return raw instanceof StarRideEntity ride ? ride : null;
    }

    private static RaceRascalEntity createRaceRascal(ServerWorld world) {
        Entity raw = net.minecraft.registry.Registries.ENTITY_TYPE
                .get(new net.minecraft.util.Identifier("odd", "race_rascal"))
                .create(world);
        return raw instanceof RaceRascalEntity rascal ? rascal : null;
    }

    public static void sendCountdownStep(ServerPlayerEntity player, int value) {
        player.getServerWorld().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                ModSounds.COUNTDOWN,
                SoundCategory.PLAYERS,
                1.0F,
                1.0F
        );
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(0, 15, 5));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(String.valueOf(value))));
    }

    public static void sendGoTitle(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(0, 15, 8));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("GO!")));
    }

    public static void clearRaceTitle(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new ClearTitleS2CPacket(true));
    }
}