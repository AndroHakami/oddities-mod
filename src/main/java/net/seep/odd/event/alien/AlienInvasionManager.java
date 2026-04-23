package net.seep.odd.event.alien;

import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.event.alien.net.AlienInvasionNet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AlienInvasionManager {

    public static final String TAG = "odd_alien_invasion";

    public static final int TOTAL_PHASES = 4;
    public static final int BEAM_TICKS = 16;
    public static final int PHASE_FOUR_BOSS_DELAY_TICKS = 20 * 8;

    public static final int MIN_R = 18;
    public static final int MAX_R = 40;
    public static final int MAX_TRIES = 18;

    private boolean active = false;
    private int wave = 0; // kept as "wave" for packet compatibility; semantically this is the current phase
    private int maxWaves = TOTAL_PHASES;

    private int killsThisPhase = 0;
    private int requiredKillsThisPhase = 1;
    private long nextSpawnTick = 0L;

    private boolean phaseFourBossQueued = false;
    private boolean phaseFourBossSpawned = false;
    private long phaseFourBossSpawnTick = 0L;

    private final Map<UUID, TrackedSpawn> alive = new HashMap<>();
    private final List<PendingSpawn> pending = new ArrayList<>();

    private final ServerBossBar bossBar = new ServerBossBar(
            Text.literal("Alien Invasion").formatted(Formatting.GREEN),
            BossBar.Color.GREEN,
            BossBar.Style.PROGRESS
    );

    public AlienInvasionManager() {
        bossBar.setVisible(false);
    }

    public boolean isActive() { return active; }
    public int wave() { return wave; }
    public int maxWaves() { return maxWaves; }

    public void start(MinecraftServer server, int ignoredMaxWaves) {
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld == null) return;

        this.maxWaves = TOTAL_PHASES;
        this.wave = 0;
        this.killsThisPhase = 0;
        this.requiredKillsThisPhase = 1;
        this.nextSpawnTick = 0L;
        this.phaseFourBossQueued = false;
        this.phaseFourBossSpawned = false;
        this.phaseFourBossSpawnTick = 0L;

        this.active = true;
        this.alive.clear();
        this.pending.clear();

        bossBar.setVisible(true);
        bossBar.setPercent(0.0f);

        for (ServerPlayerEntity p : overworld.getPlayers()) {
            bossBar.addPlayer(p);
            AlienInvasionNet.sendState(p, true, wave, this.maxWaves);
        }

        beginNextWave(overworld);
    }

    public void stop(MinecraftServer server) {
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        this.active = false;
        this.wave = 0;
        this.killsThisPhase = 0;
        this.requiredKillsThisPhase = 1;
        this.nextSpawnTick = 0L;
        this.phaseFourBossQueued = false;
        this.phaseFourBossSpawned = false;
        this.phaseFourBossSpawnTick = 0L;

        this.pending.clear();
        this.alive.clear();

        bossBar.setVisible(false);
        bossBar.clearPlayers();

        if (overworld != null) {
            for (Entity e : overworld.iterateEntities()) {
                if (e.getCommandTags().contains(TAG)) {
                    e.discard();
                }
            }
            for (ServerPlayerEntity p : overworld.getPlayers()) {
                AlienInvasionNet.sendState(p, false, 0, 0);
            }
        }
    }

    public void tick(ServerWorld overworld) {
        syncBossBarPlayers(overworld);
        if (!active) return;

        collectDeadTrackedEntities(overworld);

        long now = overworld.getTime();
        for (int i = pending.size() - 1; i >= 0; i--) {
            PendingSpawn ps = pending.get(i);
            if (ps.spawnTick <= now) {
                pending.remove(i);
                spawnEntityNow(overworld, ps);
            }
        }

        if (wave >= 1 && wave <= 3 && killsThisPhase >= requiredKillsThisPhase) {
            beginNextWave(overworld);
        }

        if (!active) return;

        if (wave >= 1 && wave <= 3) {
            maintainPhasePopulation(overworld);
        } else if (wave == 4) {
            ensurePhaseFourBossQueued(overworld);
            if (killsThisPhase >= requiredKillsThisPhase) {
                stop(overworld.getServer());
                return;
            }
        }

        bossBar.setPercent(getBossBarPercent(overworld));
        bossBar.setName(title(overworld));
    }

    private void syncBossBarPlayers(ServerWorld overworld) {
        if (!bossBar.isVisible()) return;

        Set<UUID> overworldPlayers = new HashSet<>();
        for (ServerPlayerEntity p : overworld.getPlayers()) {
            overworldPlayers.add(p.getUuid());
            if (!bossBar.getPlayers().contains(p)) bossBar.addPlayer(p);
        }

        for (ServerPlayerEntity p : new ArrayList<>(bossBar.getPlayers())) {
            if (!overworldPlayers.contains(p.getUuid())) {
                bossBar.removePlayer(p);
            }
        }
    }

    private void collectDeadTrackedEntities(ServerWorld overworld) {
        Iterator<Map.Entry<UUID, TrackedSpawn>> it = alive.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TrackedSpawn> entry = it.next();
            Entity e = overworld.getEntity(entry.getKey());
            if (e != null && e.isAlive()) continue;

            TrackedSpawn tracked = entry.getValue();
            it.remove();

            if (wave >= 1 && wave <= 3) {
                killsThisPhase++;
            } else if (wave == 4 && tracked.phaseBoss) {
                killsThisPhase = requiredKillsThisPhase;
            }
        }
    }

    private Text title(ServerWorld overworld) {
        if (!active) return Text.literal("Alien Invasion").formatted(Formatting.GREEN);

        MutableText head = Text.literal("Alien Invasion  ").formatted(Formatting.GREEN)
                .append(Text.literal("Phase " + wave + "/" + maxWaves).formatted(Formatting.WHITE));

        if (wave >= 1 && wave <= 3) {
            return head.append(Text.literal("  " + killsThisPhase + "/" + requiredKillsThisPhase + " kills")
                    .formatted(Formatting.GRAY));
        }

        if (wave == 4) {
            if (!phaseFourBossQueued) {
                return head.append(Text.literal("  Preparing boss").formatted(Formatting.GRAY));
            }
            if (!phaseFourBossSpawned) {
                int seconds = Math.max(0, MathHelper.ceil((phaseFourBossSpawnTick - overworld.getTime()) / 20.0f));
                return head.append(Text.literal("  Boss incoming (" + seconds + "s)").formatted(Formatting.GRAY));
            }
            return head.append(Text.literal("  Defeat the boss").formatted(Formatting.GRAY));
        }

        return head;
    }

    private void beginNextWave(ServerWorld overworld) {
        wave++;
        killsThisPhase = 0;
        requiredKillsThisPhase = getRequiredKillsForPhase(wave, overworld.getPlayers().size());
        nextSpawnTick = overworld.getTime() + 20L;
        phaseFourBossQueued = false;
        phaseFourBossSpawned = false;
        phaseFourBossSpawnTick = 0L;

        for (ServerPlayerEntity p : overworld.getPlayers()) {
            AlienInvasionNet.sendState(p, true, wave, maxWaves);
        }

        bossBar.setPercent(getBossBarPercent(overworld));
        bossBar.setName(title(overworld));

        if (wave == 4) {
            pending.removeIf(spawn -> !spawn.phaseBoss());
            ensurePhaseFourBossQueued(overworld);
        }
    }

    private int getRequiredKillsForPhase(int phase, int playerCount) {
        int extraPlayers = Math.max(0, playerCount - 1);
        return switch (phase) {
            case 1 -> 12 + extraPlayers * 8;
            case 2 -> 12 + extraPlayers * 10;
            case 3 -> 12 + extraPlayers * 12;
            case 4 -> 1;
            default -> 1;
        };
    }

    private void maintainPhasePopulation(ServerWorld overworld) {
        if (overworld.getTime() < nextSpawnTick) return;

        List<ServerPlayerEntity> players = overworld.getPlayers();
        if (players.isEmpty()) {
            nextSpawnTick = overworld.getTime() + 20L;
            return;
        }

        int cap = getActiveCapForPhase(wave, players.size());
        int current = alive.size() + pending.size();
        if (current >= cap) {
            nextSpawnTick = overworld.getTime() + 10L;
            return;
        }

        int budget = Math.min(cap - current, getSpawnBudgetForPhase(wave, players.size()));
        if (budget <= 0) {
            nextSpawnTick = overworld.getTime() + 10L;
            return;
        }

        long spawnTick = overworld.getTime() + BEAM_TICKS;

        if (wave == 3 && canQueueSoloPhaseThreeMech() && overworld.random.nextFloat() < 0.13f) {
            ServerPlayerEntity anchor = players.get(overworld.random.nextInt(players.size()));
            BlockPos mechPos = findSkySafeSurface(overworld, anchor.getBlockPos());
            queueSpawn(overworld, spawnTick, mechPos, SpawnKind.OUTER_MECH, 1.55f, 120.0f, BEAM_TICKS, false);
            nextSpawnTick = overworld.getTime() + getSpawnIntervalForPhase(wave);
            return;
        }

        for (int i = 0; i < budget; i++) {
            ServerPlayerEntity anchor = players.get(overworld.random.nextInt(players.size()));
            BlockPos pos = findSkySafeSurface(overworld, anchor.getBlockPos());
            SpawnKind kind = rollSpawnKind(overworld, wave);
            queueSpawn(overworld, spawnTick, pos, kind, 1.15f, 96.0f, BEAM_TICKS, false);
        }

        nextSpawnTick = overworld.getTime() + getSpawnIntervalForPhase(wave);
    }

    private int getActiveCapForPhase(int phase, int playerCount) {
        int extraPlayers = Math.max(0, playerCount - 1);
        return switch (phase) {
            case 1 -> 8 + playerCount * 2 + extraPlayers;
            case 2 -> 10 + playerCount * 3 + extraPlayers * 2;
            case 3 -> 12 + playerCount * 4 + extraPlayers * 2;
            default -> 0;
        };
    }

    private int getSpawnBudgetForPhase(int phase, int playerCount) {
        return switch (phase) {
            case 1 -> Math.max(2, Math.min(4, 1 + playerCount));
            case 2 -> Math.max(2, Math.min(5, 2 + playerCount));
            case 3 -> Math.max(3, Math.min(6, 2 + playerCount));
            default -> 0;
        };
    }

    private int getSpawnIntervalForPhase(int phase) {
        return switch (phase) {
            case 1 -> 20 * 2;
            case 2 -> 20 + 12;
            case 3 -> 20;
            default -> 20;
        };
    }

    private boolean canQueueSoloPhaseThreeMech() {
        if (countAlive(SpawnKind.OUTER_MECH) > 0) return false;
        return countPending(SpawnKind.OUTER_MECH) == 0;
    }

    private int countAlive(SpawnKind kind) {
        int count = 0;
        for (TrackedSpawn tracked : alive.values()) {
            if (tracked.kind == kind) count++;
        }
        return count;
    }

    private int countPending(SpawnKind kind) {
        int count = 0;
        for (PendingSpawn spawn : pending) {
            if (spawn.kind == kind) count++;
        }
        return count;
    }

    private SpawnKind rollSpawnKind(ServerWorld overworld, int phase) {
        int roll = overworld.random.nextInt(getTotalWeight(phase));

        if (phase == 1) {
            if ((roll -= 50) < 0) return SpawnKind.OUTERMAN;
            if ((roll -= 32) < 0) return SpawnKind.SAUCER;
            return SpawnKind.OUTERMAN_GUNNER;
        }

        if (phase == 2) {
            if ((roll -= 22) < 0) return SpawnKind.OUTERMAN;
            if ((roll -= 20) < 0) return SpawnKind.SAUCER;
            if ((roll -= 28) < 0) return SpawnKind.OUTERMAN_GUNNER;
            if ((roll -= 15) < 0) return SpawnKind.UFO_SLICER;
            return SpawnKind.UFO_BOMBER;
        }

        if ((roll -= 20) < 0) return SpawnKind.OUTERMAN;
        if ((roll -= 18) < 0) return SpawnKind.SAUCER;
        if ((roll -= 20) < 0) return SpawnKind.OUTERMAN_GUNNER;
        if ((roll -= 18) < 0) return SpawnKind.UFO_SLICER;
        return SpawnKind.UFO_BOMBER;
    }

    private int getTotalWeight(int phase) {
        return switch (phase) {
            case 1 -> 50 + 32 + 18;
            case 2 -> 22 + 20 + 28 + 15 + 15;
            case 3 -> 20 + 18 + 20 + 18 + 24;
            default -> 1;
        };
    }

    private void ensurePhaseFourBossQueued(ServerWorld overworld) {
        if (phaseFourBossQueued) return;

        List<ServerPlayerEntity> players = overworld.getPlayers();
        if (players.isEmpty()) return;

        ServerPlayerEntity anchor = players.get(overworld.random.nextInt(players.size()));
        BlockPos bossPos = findSkySafeSurface(overworld, anchor.getBlockPos());

        phaseFourBossQueued = true;
        phaseFourBossSpawnTick = overworld.getTime() + PHASE_FOUR_BOSS_DELAY_TICKS;

        // Placeholder until dragoness exists.
        queueSpawn(overworld, phaseFourBossSpawnTick, bossPos, SpawnKind.DRAGONESS, 2.1f, 140.0f,
                PHASE_FOUR_BOSS_DELAY_TICKS, true);
    }

    private float getBossBarPercent(ServerWorld overworld) {
        if (!active) return 0.0f;

        if (wave >= 1 && wave <= 3) {
            return MathHelper.clamp(killsThisPhase / (float) Math.max(1, requiredKillsThisPhase), 0.0f, 1.0f);
        }

        if (wave == 4) {
            if (killsThisPhase >= requiredKillsThisPhase) return 1.0f;
            if (!phaseFourBossQueued) return 0.0f;
            if (!phaseFourBossSpawned) {
                float t = 1.0f - ((phaseFourBossSpawnTick - overworld.getTime()) / (float) PHASE_FOUR_BOSS_DELAY_TICKS);
                return MathHelper.clamp(t * 0.35f, 0.0f, 0.35f);
            }
            return 0.35f;
        }

        return 0.0f;
    }

    private void queueSpawn(ServerWorld overworld, long spawnTick, BlockPos pos, SpawnKind kind,
                            float beamRadius, float beamHeight, int beamDurationTicks, boolean phaseBoss) {
        AlienInvasionNet.sendPillarFxNear(overworld, Vec3d.ofCenter(pos), beamRadius, beamHeight, beamDurationTicks);
        pending.add(new PendingSpawn(kind, pos, spawnTick, phaseBoss));
    }

    private void spawnEntityNow(ServerWorld overworld, PendingSpawn ps) {
        Entity e = switch (ps.kind) {
            case OUTERMAN -> ModEntities.OUTERMAN.create(overworld);
            case SAUCER -> ModEntities.UFO_SAUCER.create(overworld);
            case OUTERMAN_GUNNER -> ModEntities.OUTERMAN_GUNNER.create(overworld);
            case UFO_SLICER -> ModEntities.UFO_SLICER.create(overworld);
            case UFO_BOMBER -> ModEntities.UFO_BOMBER.create(overworld);
            case OUTER_MECH -> ModEntities.OUTER_MECH.create(overworld);
            case DRAGONESS -> ModEntities.DRAGONESS.create(overworld);
        };
        if (e == null) return;

        double x = ps.pos.getX() + 0.5;
        double y = ps.pos.getY();
        double z = ps.pos.getZ() + 0.5;

        e.refreshPositionAndAngles(x, y, z, overworld.random.nextFloat() * 360f, 0f);
        e.addCommandTag(TAG);

        if (e instanceof MobEntity mob) {
            mob.setPersistent();
            mob.initialize(overworld, overworld.getLocalDifficulty(ps.pos), SpawnReason.EVENT, null, null);
        }

        overworld.spawnEntity(e);
        alive.put(e.getUuid(), new TrackedSpawn(ps.kind, ps.phaseBoss));

        if (ps.phaseBoss) {
            phaseFourBossSpawned = true;
        }
    }

    /**
     * Finds a surface position that is:
     * - top-of-world (never underground)
     * - sky-visible (so not inside houses)
     */
    private BlockPos findSkySafeSurface(ServerWorld overworld, BlockPos origin) {
        var rand = overworld.random;

        for (int t = 0; t < MAX_TRIES; t++) {
            double ang = rand.nextDouble() * MathHelper.TAU;
            int r = MIN_R + rand.nextInt(Math.max(1, (MAX_R - MIN_R)));

            int x = origin.getX() + MathHelper.floor(Math.cos(ang) * r);
            int z = origin.getZ() + MathHelper.floor(Math.sin(ang) * r);

            int y = overworld.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos surface = new BlockPos(x, y, z);

            if (!overworld.isSkyVisible(surface.up())) continue;

            var below = surface.down();
            var st = overworld.getBlockState(below);
            if (st.isAir() || st.getFluidState().isStill()) continue;

            return surface;
        }

        int y = overworld.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, origin.getX(), origin.getZ());
        return new BlockPos(origin.getX(), y, origin.getZ());
    }

    private enum SpawnKind {
        OUTERMAN,
        SAUCER,
        OUTERMAN_GUNNER,
        UFO_SLICER,
        UFO_BOMBER,
        OUTER_MECH,
        DRAGONESS
    }

    private record PendingSpawn(SpawnKind kind, BlockPos pos, long spawnTick, boolean phaseBoss) {}
    private record TrackedSpawn(SpawnKind kind, boolean phaseBoss) {}
}
