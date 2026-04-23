package net.seep.odd.quest.types;

import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.him.HimEntity;
import net.seep.odd.quest.ActiveQuestState;
import net.seep.odd.quest.QuestDefinition;
import net.seep.odd.quest.QuestManager;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.UUID;

public final class FindHimQuestLogic {
    private static final double FIELD_CATCH_RADIUS = 2.85D;
    private static final double EXIT_REACH_RADIUS = 2.35D;
    private static final int MAZE_STEP_TICKS = 20 * 20;

    private FindHimQuestLogic() {}

    public static void rememberReturnPosition(ServerPlayerEntity player, ActiveQuestState active) {
        if (active == null || active.hasReturnPos) return;
        active.hasReturnPos = true;
        active.returnX = player.getBlockX();
        active.returnY = player.getBlockY();
        active.returnZ = player.getBlockZ();
        active.returnYaw = player.getYaw();
        active.returnPitch = player.getPitch();
    }

    public static void tick(ServerPlayerEntity player, ActiveQuestState active, QuestDefinition def) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        if (!active.hasReturnPos) {
            rememberReturnPosition(player, active);
        }

        if (active.stage == ActiveQuestState.STAGE_FIND_HIM_MAZE) {
            tickMaze(player, world, active, def);
            return;
        }

        active.stage = ActiveQuestState.STAGE_FIND_HIM_FIELD;
        HimEntity him = findTrackedHim(player, active);
        if (him == null || him.isRemoved()) {
            active.spawnedEntityIds.clear();
            spawnOutdoorHim(player, active);
            QuestManager.markDirty(player);
            QuestManager.syncQuest(player);
            return;
        }

        if (player.squaredDistanceTo(him) <= FIELD_CATCH_RADIUS * FIELD_CATCH_RADIUS) {
            startMazeSequence(player, world, active);
        }
    }

    public static void spawnOutdoorHim(ServerPlayerEntity player, ActiveQuestState active) {
        if (!(player.getWorld() instanceof ServerWorld world)) return;

        BlockPos spawn = QuestManager.pickQuestArea(player, 42.0D, 74.0D);
        HimEntity him = ModEntities.HIM.create(world);
        if (him == null) return;

        him.refreshPositionAndAngles(
                spawn.getX() + 0.5D,
                spawn.getY(),
                spawn.getZ() + 0.5D,
                world.random.nextFloat() * 360.0F,
                0.0F
        );
        him.assignQuestOwner(player);
        him.setHomeCenter(spawn);
        world.spawnEntityAndPassengers(him);
        active.stage = ActiveQuestState.STAGE_FIND_HIM_FIELD;
        active.hasTarget = false;
        active.countdownTicks = 0;
        active.spawnedEntityIds.add(him.getUuid());
    }

    public static void onQuestCancelled(ServerPlayerEntity player, ActiveQuestState active) {
        if (active == null || !active.hasReturnPos || player.getServer() == null) return;
        ServerWorld atheneum = player.getServer().getWorld(QuestManager.ATHENEUM);
        if (atheneum == null) return;
        player.teleport(
                atheneum,
                active.returnX + 0.5D,
                active.returnY,
                active.returnZ + 0.5D,
                EnumSet.noneOf(PositionFlag.class),
                active.returnYaw,
                active.returnPitch
        );
    }

    private static void tickMaze(ServerPlayerEntity player, ServerWorld world, ActiveQuestState active, QuestDefinition def) {
        int oldSeconds = secondsLeft(active.countdownTicks);
        if (active.countdownTicks > 0) {
            active.countdownTicks--;
        }
        int newSeconds = secondsLeft(active.countdownTicks);

        if (active.countdownTicks <= 0) {
            QuestManager.failActiveQuest(player, "You lost Him in the maze.");
            return;
        }

        HimEntity him = findTrackedHim(player, active);
        if (him != null && !him.isRemoved()) {
            if (him.hasReachedMazeExit()) {
                BlockPos exit = him.getMazeExitMarker();
                if (exit != null) {
                    leaveGlowTrail(world, exit);
                    active.hasTarget = true;
                    active.targetX = exit.getX();
                    active.targetY = exit.getY() + 1;
                    active.targetZ = exit.getZ();
                }
                QuestManager.poofRemove(him);
                active.spawnedEntityIds.remove(him.getUuid());
                player.sendMessage(Text.literal("Him slipped away. Follow the glow."), true);
                QuestManager.markDirty(player);
                QuestManager.syncQuest(player);
                return;
            }
        } else if (active.hasTarget) {
            BlockPos target = new BlockPos(active.targetX, active.targetY, active.targetZ);
            if (player.getBlockPos().isWithinDistance(target, EXIT_REACH_RADIUS)) {
                advanceMaze(player, world, active, def, target, null);
                return;
            }
            if (player.age % 10 == 0) {
                leaveGlowTrail(world, target.down());
            }
        }

        if (newSeconds != oldSeconds) {
            QuestManager.syncQuest(player);
        }
    }

    private static void startMazeSequence(ServerPlayerEntity player, ServerWorld world, ActiveQuestState active) {
        HimEntity him = findTrackedHim(player, active);
        if (him != null) {
            QuestManager.poofRemove(him);
            active.spawnedEntityIds.remove(him.getUuid());
        }

        active.progress = 0;
        active.readyToClaim = false;
        active.stage = ActiveQuestState.STAGE_FIND_HIM_MAZE;
        active.hasTarget = false;
        active.countdownTicks = MAZE_STEP_TICKS;

        HimMazeManager.MazePlacement placement = HimMazeManager.pickAndPrepare(world, player.getRandom(), -1);
        if (placement == null) {
            QuestManager.failActiveQuest(player, "No Him mazes were found. Add him_maze NBTs first.");
            return;
        }

        teleportPlayerToPlacement(player, world, active, placement);
        spawnMazeHim(world, player, active, placement);
        QuestManager.markDirty(player);
        QuestManager.refreshBossBar(player);
        QuestManager.syncQuest(player);
    }

    private static void advanceMaze(ServerPlayerEntity player, ServerWorld world, ActiveQuestState active,
                                    QuestDefinition def, BlockPos previousExit, HimEntity caughtHim) {
        if (caughtHim != null && !caughtHim.isRemoved()) {
            QuestManager.poofRemove(caughtHim);
            active.spawnedEntityIds.remove(caughtHim.getUuid());
        }
        if (previousExit != null) {
            leaveGlowTrail(world, previousExit.down());
        }

        active.hasTarget = false;
        active.progress = Math.min(active.progress + 1, def.goal());

        if (active.progress >= def.goal()) {
            active.readyToClaim = true;
            active.stage = ActiveQuestState.STAGE_ACTIVE;
            active.countdownTicks = 0;
            active.spawnedEntityIds.clear();
            QuestManager.refreshBossBar(player);
            QuestManager.markDirty(player);
            onQuestCancelled(player, active);
            player.sendMessage(Text.literal("You found Him. Return to the librarian for your reward."), true);
            QuestManager.syncQuest(player);
            return;
        }

        HimMazeManager.MazePlacement placement = HimMazeManager.pickAndPrepare(world, player.getRandom(), -1);
        if (placement == null) {
            QuestManager.failActiveQuest(player, "No Him mazes were found. Add him_maze NBTs first.");
            return;
        }

        active.countdownTicks = MAZE_STEP_TICKS;
        teleportPlayerToPlacement(player, world, active, placement);
        spawnMazeHim(world, player, active, placement);
        QuestManager.refreshBossBar(player);
        QuestManager.markDirty(player);
        QuestManager.syncQuest(player);
    }

    private static void teleportPlayerToPlacement(ServerPlayerEntity player, ServerWorld world, ActiveQuestState active,
                                                  HimMazeManager.MazePlacement placement) {
        BlockPos start = placement.playerStart();
        player.teleport(
                world,
                start.getX() + 0.5D,
                start.getY(),
                start.getZ() + 0.5D,
                EnumSet.noneOf(PositionFlag.class),
                player.getYaw(),
                player.getPitch()
        );
        active.hasTarget = false;
    }

    private static void spawnMazeHim(ServerWorld world, ServerPlayerEntity player, ActiveQuestState active,
                                     HimMazeManager.MazePlacement placement) {
        active.spawnedEntityIds.clear();
        HimEntity him = ModEntities.HIM.create(world);
        if (him == null) return;

        BlockPos spawn = placement.himStartMarker();
        BlockPos exit = placement.himExitMarker();
        him.refreshPositionAndAngles(
                spawn.getX() + 0.5D,
                spawn.getY() + 1.0D,
                spawn.getZ() + 0.5D,
                world.random.nextFloat() * 360.0F,
                0.0F
        );
        him.assignQuestOwner(player);
        him.beginMazeRun(spawn, exit);
        world.spawnEntityAndPassengers(him);
        active.spawnedEntityIds.add(him.getUuid());
    }

    private static int secondsLeft(int ticks) {
        return Math.max(1, (ticks + 19) / 20);
    }

    private static void leaveGlowTrail(ServerWorld world, BlockPos pos) {
        world.spawnParticles(ParticleTypes.GLOW, pos.getX() + 0.5D, pos.getY() + 1.05D, pos.getZ() + 0.5D,
                22, 0.28D, 0.18D, 0.28D, 0.01D);
        world.spawnParticles(ParticleTypes.END_ROD, pos.getX() + 0.5D, pos.getY() + 1.10D, pos.getZ() + 0.5D,
                6, 0.12D, 0.05D, 0.12D, 0.0D);
    }

    private static HimEntity findTrackedHim(ServerPlayerEntity player, ActiveQuestState active) {
        Iterator<UUID> it = active.spawnedEntityIds.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            Entity entity = QuestManager.findEntity(player, uuid);
            if (entity == null || entity.isRemoved()) {
                it.remove();
                continue;
            }
            if (entity instanceof HimEntity him) {
                return him;
            }
        }
        return null;
    }
}
