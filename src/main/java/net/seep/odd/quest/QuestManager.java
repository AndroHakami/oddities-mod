package net.seep.odd.quest;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.seep.odd.entity.him.HimEntity;
import net.seep.odd.entity.librarian.LibrarianEntity;
import net.seep.odd.entity.race_rascal.RaceRascalEntity;
import net.seep.odd.entity.rake.RakeEntity;
import net.seep.odd.entity.rascal.RascalEntity;
import net.seep.odd.entity.scared_rascal.ScaredRascalEntity;
import net.seep.odd.entity.star_ride.StarRideEntity;
import net.seep.odd.quest.types.BookQuizQuestLogic;
import net.seep.odd.quest.types.FindHimQuestLogic;
import net.seep.odd.quest.types.RaceQuestLogic;
import net.seep.odd.quest.types.RoundupQuestLogic;
import net.seep.odd.quest.types.ScaredRascalQuestLogic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class QuestManager {
    public static final RegistryKey<World> ATHENEUM =
            RegistryKey.of(RegistryKeys.WORLD, new Identifier("odd", "atheneum"));

    private static final Map<UUID, ServerBossBar> QUEST_BARS = new HashMap<>();

    private QuestManager() {}

    public record SyncView(String phase, String objectiveHint, boolean playMusic,
                           Set<Integer> trackedEntityIds, Set<Integer> calmedTrackedEntityIds) {}

    public static PlayerQuestProfile profile(net.minecraft.entity.player.PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            throw new IllegalStateException("Quest profile requested from non-server player");
        }
        return QuestPersistentState.get(serverPlayer.getServer()).getProfile(serverPlayer.getUuid());
    }

    public static void openLibrarianScreen(net.minecraft.entity.player.PlayerEntity player, LibrarianEntity librarian) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;

        ActiveQuestState active = profile(serverPlayer).activeQuest;
        if (active != null) {
            QuestDefinition def = QuestRegistry.get(active.questId);
            if (def != null && def.objective.type == QuestObjectiveData.Type.LORE_BOOK_QUIZ) {
                if (BookQuizQuestLogic.readyForQuiz(serverPlayer, active)) {
                    QuestNetworking.sendOpenLoreQuiz(serverPlayer, librarian.getId(), active);
                    return;
                }
                if (BookQuizQuestLogic.hasRequiredBook(serverPlayer, active) && !active.requiredBookRead) {
                    serverPlayer.sendMessage(Text.literal("Read " + net.seep.odd.lore.AtheneumLoreBooks.titleOf(active.requestedLoreId) + " first."), true);
                }
            }
        }

        QuestNetworking.sendSync(serverPlayer, true, librarian.getId());
    }

    public static boolean hasActiveQuest(ServerPlayerEntity player) {
        return profile(player).activeQuest != null;
    }

    public static SyncView buildSyncView(ServerPlayerEntity player, ActiveQuestState active) {
        QuestDefinition def = QuestRegistry.get(active.questId);
        String phase = "active";
        String hint = "Quest in progress";
        boolean playMusic = def != null && def.objective.type == QuestObjectiveData.Type.KILL;

        if (def != null) {
            if (active.readyToClaim) {
                phase = "claim";
                hint = "Return to the librarian to claim your reward";
                playMusic = false;
            } else if (def.objective.type == QuestObjectiveData.Type.CALM_RETURN) {
                if (active.stage == ActiveQuestState.STAGE_GO_TO_AREA) {
                    phase = "travel";
                    hint = "Go to the marked location";
                    playMusic = false;
                } else {
                    phase = "active";
                    hint = "Calm the rascals and bring them back";
                    playMusic = true;
                }
            } else if (def.objective.type == QuestObjectiveData.Type.RACE_RETURN) {
                switch (active.stage) {
                    case ActiveQuestState.STAGE_GO_TO_AREA -> { phase = "travel"; hint = "Go to the marked location"; playMusic = false; }
                    case ActiveQuestState.STAGE_WAIT_MOUNT -> { phase = "active"; hint = "Mount the waiting star ride"; playMusic = false; }
                    case ActiveQuestState.STAGE_COUNTDOWN -> { phase = "active"; hint = "Get ready..."; playMusic = false; }
                    case ActiveQuestState.STAGE_RACING -> { phase = "active"; hint = "Beat the rascal back to the librarian"; playMusic = true; }
                    default -> { phase = "active"; hint = "Race back to the librarian"; playMusic = false; }
                }
            } else if (def.objective.type == QuestObjectiveData.Type.LORE_BOOK_QUIZ) {
                phase = "active";
                hint = BookQuizQuestLogic.buildHint(player, active);
                playMusic = false;
            } else if (def.objective.type == QuestObjectiveData.Type.FIND_HIM) {
                phase = "active";
                hint = buildFindHimHint(active);
                playMusic = active.stage == ActiveQuestState.STAGE_FIND_HIM_MAZE;
            } else if (def.objective.type == QuestObjectiveData.Type.SCARED_RASCAL) {
                switch (active.stage) {
                    case ActiveQuestState.STAGE_GO_TO_AREA -> { phase = "travel"; hint = "Go to the marked location"; playMusic = false; }
                    case ActiveQuestState.STAGE_SCARED_RASCAL_TALK_START -> { phase = "active"; hint = "Talk to the scared rascal"; playMusic = false; }
                    case ActiveQuestState.STAGE_SCARED_RASCAL_CHASE -> { phase = "active"; hint = "Survive for " + Math.max(1, (active.countdownTicks + 19) / 20) + "s"; playMusic = true; }
                    case ActiveQuestState.STAGE_SCARED_RASCAL_TALK_END -> { phase = "active"; hint = "Talk to the scared rascal"; playMusic = false; }
                    case ActiveQuestState.STAGE_SCARED_RASCAL_ESCORT -> { phase = "return"; hint = "Escort the scared rascal back to the librarian"; playMusic = false; }
                    default -> { phase = "active"; hint = "Help the scared rascal"; playMusic = false; }
                }
            }
        }

        Set<Integer> tracked = new HashSet<>();
        Set<Integer> calmed = new HashSet<>();
        for (UUID uuid : active.spawnedEntityIds) {
            Entity entity = findEntity(player, uuid);
            if (entity instanceof RascalEntity rascal) {
                tracked.add(rascal.getId());
                if (rascal.isCalmed()) calmed.add(rascal.getId());
            } else if (entity instanceof HimEntity him && !him.isRemoved()) {
                tracked.add(him.getId());
            } else if (entity instanceof ScaredRascalEntity scaredRascal && !scaredRascal.isRemoved()) {
                tracked.add(scaredRascal.getId());
            } else if (entity instanceof RakeEntity rake && !rake.isRemoved()) {
                tracked.add(rake.getId());
            }
        }
        return new SyncView(phase, hint, playMusic, tracked, calmed);
    }

    public static void acceptQuest(ServerPlayerEntity player, int librarianEntityId, String questId) {
        QuestDefinition def = QuestRegistry.get(questId);
        if (def == null || !validateLibrarian(player, librarianEntityId)) return;

        PlayerQuestProfile profile = profile(player);
        if (profile.activeQuest != null) {
            player.sendMessage(Text.literal("You already have an active quest."), true);
            QuestNetworking.sendSync(player, false, librarianEntityId);
            return;
        }

        int level = QuestLevelRegistry.get().currentLevel(profile.questXp);
        if (level < def.unlockLevel) {
            player.sendMessage(Text.literal("That quest is still locked."), true);
            QuestNetworking.sendSync(player, false, librarianEntityId);
            return;
        }

        if (profile.claimedQuestIds.contains(def.id)) {
            player.sendMessage(Text.literal("You already completed that quest."), true);
            QuestNetworking.sendSync(player, false, librarianEntityId);
            return;
        }

        if (def.objective.type == QuestObjectiveData.Type.CALM_RETURN) {
            BlockPos spot = pickQuestArea(player, 24.0D, 42.0D);
            profile.activeQuest = ActiveQuestState.forAreaQuest(def.id, spot.getX(), spot.getY(), spot.getZ());
        } else if (def.objective.type == QuestObjectiveData.Type.RACE_RETURN) {
            BlockPos spot = pickQuestArea(player, 58.0D, 84.0D);
            profile.activeQuest = ActiveQuestState.forAreaQuest(def.id, spot.getX(), spot.getY(), spot.getZ());
        } else if (def.objective.type == QuestObjectiveData.Type.LORE_BOOK_QUIZ) {
            profile.activeQuest = BookQuizQuestLogic.create(player, def.id);
        } else if (def.objective.type == QuestObjectiveData.Type.FIND_HIM) {
            profile.activeQuest = new ActiveQuestState(def.id);
            profile.activeQuest.stage = ActiveQuestState.STAGE_FIND_HIM_FIELD;
            FindHimQuestLogic.rememberReturnPosition(player, profile.activeQuest);
            FindHimQuestLogic.spawnOutdoorHim(player, profile.activeQuest);
        } else if (def.objective.type == QuestObjectiveData.Type.SCARED_RASCAL) {
            BlockPos spot = pickQuestArea(player, 50.0D, 82.0D);
            profile.activeQuest = ActiveQuestState.forAreaQuest(def.id, spot.getX(), spot.getY(), spot.getZ());
        } else {
            profile.activeQuest = new ActiveQuestState(def.id);
        }

        markDirty(player);
        refreshBossBar(player);
        syncQuest(player);
    }

    public static void abandonQuest(ServerPlayerEntity player, boolean sync) {
        PlayerQuestProfile profile = profile(player);
        if (profile.activeQuest == null) return;
        ActiveQuestState active = profile.activeQuest;
        cleanupQuestEntities(player, active);
        RaceQuestLogic.clearRaceTitle(player);
        if (active != null && QuestObjectiveData.Type.FIND_HIM == questType(active.questId)) {
            FindHimQuestLogic.onQuestCancelled(player, active);
        }
        profile.activeQuest = null;
        markDirty(player);
        removeBossBar(player);
        if (sync) QuestNetworking.sendSync(player, false, -1);
    }

    public static void claimQuest(ServerPlayerEntity player, int librarianEntityId, String questId) {
        if (!validateLibrarian(player, librarianEntityId)) return;
        PlayerQuestProfile profile = profile(player);
        if (profile.activeQuest == null || !profile.activeQuest.questId.equals(questId) || !profile.activeQuest.readyToClaim) return;

        QuestDefinition def = QuestRegistry.get(questId);
        if (def == null) return;

        cleanupQuestEntities(player, profile.activeQuest);
        RaceQuestLogic.clearRaceTitle(player);
        for (QuestRewardData reward : def.rewards) reward.grant(player);
        profile.questXp += Math.max(0, def.questXp);
        profile.claimedQuestIds.add(def.id);
        profile.activeQuest = null;

        markDirty(player);
        removeBossBar(player);
        QuestNetworking.sendSync(player, false, librarianEntityId);
    }

    public static void tickPlayerQuest(ServerPlayerEntity player) {
        PlayerQuestProfile profile = profile(player);
        ActiveQuestState active = profile.activeQuest;
        if (active == null) return;
        QuestDefinition def = QuestRegistry.get(active.questId);
        if (def == null) return;

        if (def.objective.type == QuestObjectiveData.Type.CALM_RETURN) {
            RoundupQuestLogic.tick(player, active, def);
        } else if (def.objective.type == QuestObjectiveData.Type.RACE_RETURN) {
            RaceQuestLogic.tick(player, active, def);
        } else if (def.objective.type == QuestObjectiveData.Type.FIND_HIM) {
            FindHimQuestLogic.tick(player, active, def);
        } else if (def.objective.type == QuestObjectiveData.Type.SCARED_RASCAL) {
            ScaredRascalQuestLogic.tick(player, active, def);
        }
    }

    public static void onKill(ServerPlayerEntity player, Entity killed) {
        PlayerQuestProfile profile = profile(player);
        ActiveQuestState active = profile.activeQuest;
        if (active == null || active.readyToClaim) return;
        QuestDefinition def = QuestRegistry.get(active.questId);
        if (def == null || def.objective.type != QuestObjectiveData.Type.KILL) return;

        Identifier expected = new Identifier(def.objective.entity);
        if (!Registries.ENTITY_TYPE.getId(killed.getType()).equals(expected)) return;

        active.progress = Math.min(active.progress + 1, def.goal());
        active.readyToClaim = active.progress >= def.goal();
        markDirty(player);
        refreshBossBar(player);
        syncQuest(player);
    }

    public static boolean canCalmRascal(ServerPlayerEntity player, RascalEntity rascal) {
        return RoundupQuestLogic.canCalmRascal(player, rascal);
    }

    public static void onRascalReturned(ServerPlayerEntity player, RascalEntity rascal) {
        RoundupQuestLogic.onRascalReturned(player, rascal);
    }

    public static void cancelIfOutsideAtheneum(ServerPlayerEntity player) {
        if (profile(player).activeQuest != null && !player.getWorld().getRegistryKey().equals(ATHENEUM)) abandonQuest(player, true);
    }

    public static void refreshBossBar(ServerPlayerEntity player) {
        PlayerQuestProfile profile = profile(player);
        if (profile.activeQuest == null) { removeBossBar(player); return; }
        QuestDefinition def = QuestRegistry.get(profile.activeQuest.questId);
        if (def == null) { removeBossBar(player); return; }

        ServerBossBar bar = QUEST_BARS.computeIfAbsent(player.getUuid(), uuid -> {
            ServerBossBar created = new ServerBossBar(Text.empty(), BossBar.Color.YELLOW, BossBar.Style.PROGRESS);
            created.setVisible(true);
            created.setDarkenSky(false);
            created.setThickenFog(false);
            return created;
        });

        bar.setName(Text.empty());
        bar.setColor(BossBar.Color.YELLOW);
        bar.setStyle(BossBar.Style.PROGRESS);
        bar.setPercent(MathHelper.clamp(profile.activeQuest.progress / (float) Math.max(1, def.goal()), 0.0f, 1.0f));
        if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
    }

    public static void removeBossBar(ServerPlayerEntity player) {
        ServerBossBar bar = QUEST_BARS.remove(player.getUuid());
        if (bar != null) {
            bar.removePlayer(player);
            bar.clearPlayers();
            bar.setVisible(false);
        }
    }

    public static void cleanupQuestEntities(ServerPlayerEntity player, ActiveQuestState active) {
        for (UUID uuid : active.spawnedEntityIds) {
            Entity entity = findEntity(player, uuid);
            if (entity != null) poofRemove(entity);
        }
        active.spawnedEntityIds.clear();
        active.playerRideId = null;
        active.rivalRideId = null;
        active.rivalRascalId = null;
        active.scaredRascalId = null;
        active.rakeId = null;
    }

    public static void syncQuest(ServerPlayerEntity player) {
        QuestNetworking.sendSync(player, false, -1);
    }

    public static void markDirty(ServerPlayerEntity player) {
        QuestPersistentState.get(player.getServer()).markDirty();
    }

    public static void failActiveQuest(ServerPlayerEntity player, String message) {
        PlayerQuestProfile profile = profile(player);
        if (profile.activeQuest == null) return;
        ActiveQuestState active = profile.activeQuest;
        cleanupQuestEntities(player, active);
        RaceQuestLogic.clearRaceTitle(player);
        if (active != null && QuestObjectiveData.Type.FIND_HIM == questType(active.questId)) {
            FindHimQuestLogic.onQuestCancelled(player, active);
        }
        profile.activeQuest = null;
        markDirty(player);
        removeBossBar(player);
        player.sendMessage(Text.literal(message), true);
        syncQuest(player);
    }

    public static Entity findEntity(ServerPlayerEntity player, UUID uuid) {
        if (uuid == null) return null;
        for (ServerWorld world : player.getServer().getWorlds()) {
            Entity entity = world.getEntity(uuid);
            if (entity != null) return entity;
        }
        return null;
    }

    public static StarRideEntity getStarRide(ServerPlayerEntity player, UUID uuid) {
        Entity entity = findEntity(player, uuid);
        return entity instanceof StarRideEntity ride ? ride : null;
    }

    public static RaceRascalEntity getRaceRascal(ServerPlayerEntity player, UUID uuid) {
        Entity entity = findEntity(player, uuid);
        return entity instanceof RaceRascalEntity rascal ? rascal : null;
    }

    public static void poofRemove(Entity entity) {
        if (entity.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.POOF, entity.getX(), entity.getBodyY(0.5D), entity.getZ(), 10, 0.18D, 0.18D, 0.18D, 0.0D);
            world.spawnParticles(ParticleTypes.SMOKE, entity.getX(), entity.getBodyY(0.5D), entity.getZ(), 12, 0.22D, 0.22D, 0.22D, 0.01D);
            world.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENTITY_ALLAY_ITEM_TAKEN, SoundCategory.NEUTRAL, 1.0F, 0.9F);
        }
        entity.discard();
    }

    public static BlockPos pickQuestArea(ServerPlayerEntity player, double minDistance, double maxDistance) {
        BlockPos origin = player.getBlockPos();
        float yaw = player.getYaw();
        for (int i = 0; i < 10; i++) {
            double distance = minDistance + player.getRandom().nextDouble() * (maxDistance - minDistance);
            double extra = (player.getRandom().nextDouble() - 0.5D) * 70.0D;
            double angle = Math.toRadians(yaw + 90.0D + extra);
            int x = origin.getX() + MathHelper.floor(Math.cos(angle) * distance);
            int z = origin.getZ() + MathHelper.floor(Math.sin(angle) * distance);
            BlockPos floor = findNearestFloor(player.getWorld(), new BlockPos(x, origin.getY() + 6, z));
            if (floor != null) return floor.up();
        }
        BlockPos fallback = findNearestFloor(player.getWorld(), origin);
        return fallback == null ? origin : fallback.up();
    }

    public static BlockPos findNearestFloor(World world, BlockPos start) {
        BlockPos.Mutable mutable = start.mutableCopy();
        for (int y = Math.min(world.getTopY() - 2, start.getY()); y >= world.getBottomY() + 1; y--) {
            mutable.set(start.getX(), y, start.getZ());
            if (!world.getBlockState(mutable).isAir() && world.getBlockState(mutable.up()).isAir()) {
                return mutable.toImmutable();
            }
        }
        return null;
    }

    public static BlockPos findSpawnFloorAround(World world, BlockPos center, int horizontalRadius, int tries) {
        for (int i = 0; i < tries; i++) {
            int x = center.getX() + world.random.nextBetween(-horizontalRadius, horizontalRadius);
            int z = center.getZ() + world.random.nextBetween(-horizontalRadius, horizontalRadius);
            BlockPos floor = findNearestFloor(world, new BlockPos(x, center.getY() + 6, z));
            if (floor != null && floor.isWithinDistance(center, horizontalRadius + 2.0D)) return floor;
        }
        return null;
    }

    public static LibrarianEntity findNearestLibrarian(ServerPlayerEntity player) {
        LibrarianEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LibrarianEntity librarian : player.getWorld().getEntitiesByClass(LibrarianEntity.class, player.getBoundingBox().expand(128.0D), l -> true)) {
            double dist = player.squaredDistanceTo(librarian);
            if (dist < bestDist) {
                bestDist = dist;
                best = librarian;
            }
        }
        return best;
    }

    public static BlockPos findNearestLibrarianPos(ServerPlayerEntity player) {
        LibrarianEntity librarian = findNearestLibrarian(player);
        return librarian != null ? librarian.getBlockPos() : null;
    }

    private static boolean validateLibrarian(ServerPlayerEntity player, int librarianEntityId) {
        Entity entity = player.getWorld().getEntityById(librarianEntityId);
        return entity instanceof LibrarianEntity librarian && player.squaredDistanceTo(librarian) <= 64.0D;
    }

    public static boolean validateLibrarianForQuiz(ServerPlayerEntity player, int librarianEntityId) {
        return validateLibrarian(player, librarianEntityId);
    }

    private static QuestObjectiveData.Type questType(String questId) {
        QuestDefinition def = QuestRegistry.get(questId);
        return def == null ? null : def.objective.type;
    }

    private static String buildFindHimHint(ActiveQuestState active) {
        if (active == null) return "Track Him down and get close";
        if (active.stage == ActiveQuestState.STAGE_FIND_HIM_MAZE) {
            int seconds = Math.max(1, (active.countdownTicks + 19) / 20);
            return active.hasTarget
                    ? "Follow the glow before time runs out (" + seconds + "s)"
                    : "Catch Him before he escapes (" + seconds + "s)";
        }
        return "Track Him down and get close";
    }

    public static void registerPackets() {
        ServerPlayNetworking.registerGlobalReceiver(QuestNetworking.C2S_ACCEPT, (server, player, handler, buf, responseSender) -> {
            int librarianId = buf.readInt();
            String questId = buf.readString();
            server.execute(() -> acceptQuest(player, librarianId, questId));
        });
        ServerPlayNetworking.registerGlobalReceiver(QuestNetworking.C2S_ABANDON, (server, player, handler, buf, responseSender) ->
                server.execute(() -> abandonQuest(player, true)));
        ServerPlayNetworking.registerGlobalReceiver(QuestNetworking.C2S_CLAIM, (server, player, handler, buf, responseSender) -> {
            int librarianId = buf.readInt();
            String questId = buf.readString();
            server.execute(() -> claimQuest(player, librarianId, questId));
        });
        ServerPlayNetworking.registerGlobalReceiver(QuestNetworking.C2S_SUBMIT_LORE_QUIZ, (server, player, handler, buf, responseSender) -> {
            int librarianId = buf.readInt();
            String questId = buf.readString();
            int selected = buf.readInt();
            server.execute(() -> BookQuizQuestLogic.submitAnswer(player, librarianId, questId, selected));
        });
        ServerPlayNetworking.registerGlobalReceiver(QuestNetworking.C2S_CONTINUE_SCARED_RASCAL_DIALOG, (server, player, handler, buf, responseSender) -> {
            int rascalEntityId = buf.readInt();
            server.execute(() -> ScaredRascalQuestLogic.continueDialogue(player, rascalEntityId));
        });
        BookReadTracker.init();
    }
}
