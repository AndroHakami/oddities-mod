package net.seep.odd.event.alien;

import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.event.alien.net.AlienInvasionNet;

import java.util.*;

public final class AlienInvasionManager {

    public static final String TAG = "odd_alien_invasion";

    // per-player per-wave
    public static final int OUTERMEN_PER_PLAYER = 3;
    public static final int SAUCERS_PER_PLAYER  = 1;

    // pacing
    public static final int BEAM_TICKS = 16;                 // pillar drop time before spawn
    public static final int BETWEEN_WAVES_TICKS = 20 * 8;    // pause between waves

    // spawn search
    public static final int MIN_R = 18;
    public static final int MAX_R = 40;
    public static final int MAX_TRIES = 18;

    private boolean active = false;
    private int wave = 0;
    private int maxWaves = 5;

    private int betweenWaveTimer = 0;

    private final Set<UUID> alive = new HashSet<>();
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

    public void start(MinecraftServer server, int maxWaves) {
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld == null) return;

        this.maxWaves = Math.max(1, maxWaves);
        this.wave = 0;
        this.betweenWaveTimer = 0;

        this.active = true;
        this.alive.clear();
        this.pending.clear();

        bossBar.setVisible(true);
        bossBar.setPercent(1.0f);
        bossBar.setName(title());

        // sync state + bar membership
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
        this.betweenWaveTimer = 0;

        this.pending.clear();
        this.alive.clear();

        bossBar.setVisible(false);
        bossBar.clearPlayers();

        if (overworld != null) {
            // kill/cleanup invasion mobs
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
        // keep bossbar membership correct (only overworld players)
        if (bossBar.isVisible()) {
            Set<UUID> overworldPlayers = new HashSet<>();
            for (ServerPlayerEntity p : overworld.getPlayers()) {
                overworldPlayers.add(p.getUuid());
                if (!bossBar.getPlayers().contains(p)) bossBar.addPlayer(p);
            }

            // bossBar.getPlayers() is unmodifiable -> remove via bossBar.removePlayer
            for (ServerPlayerEntity p : new ArrayList<>(bossBar.getPlayers())) {
                if (!overworldPlayers.contains(p.getUuid())) {
                    bossBar.removePlayer(p);
                }
            }
        }

        if (!active) return;

        // prune dead
        alive.removeIf(uuid -> overworld.getEntity(uuid) == null);

        // run pending spawns
        long now = overworld.getTime();
        for (int i = pending.size() - 1; i >= 0; i--) {
            PendingSpawn ps = pending.get(i);
            if (ps.spawnTick <= now) {
                pending.remove(i);
                spawnEntityNow(overworld, ps);
            }
        }

        // bossbar percent = (alive+pending)/totalWaveCount
        int total = Math.max(1, totalThisWave(overworld));
        int remaining = alive.size() + pending.size();
        bossBar.setPercent(MathHelper.clamp(remaining / (float) total, 0f, 1f));
        bossBar.setName(title());

        // wave finished?
        if (alive.isEmpty() && pending.isEmpty()) {
            if (wave >= maxWaves) {
                stop(overworld.getServer());
                return;
            }
            if (betweenWaveTimer <= 0) {
                betweenWaveTimer = BETWEEN_WAVES_TICKS;
            } else {
                betweenWaveTimer--;
                if (betweenWaveTimer == 0) {
                    beginNextWave(overworld);
                }
            }
        }
    }

    private Text title() {
        if (!active) return Text.literal("Alien Invasion").formatted(Formatting.GREEN);
        return Text.literal("Alien Invasion  ")
                .append(Text.literal("Wave " + wave + "/" + maxWaves).formatted(Formatting.WHITE))
                .formatted(Formatting.GREEN);
    }

    private int totalThisWave(ServerWorld overworld) {
        // based on current overworld player count (raid-like scaling)
        int players = overworld.getPlayers().size();
        return players * (OUTERMEN_PER_PLAYER + SAUCERS_PER_PLAYER);
    }

    private void beginNextWave(ServerWorld overworld) {
        wave++;
        bossBar.setName(title());

        List<ServerPlayerEntity> players = overworld.getPlayers();
        if (players.isEmpty()) return;

        long spawnBase = overworld.getTime() + BEAM_TICKS;

        for (ServerPlayerEntity p : players) {
            // 3 outermen
            for (int i = 0; i < OUTERMEN_PER_PLAYER; i++) {
                BlockPos pos = findSkySafeSurface(overworld, p.getBlockPos());
                queueSpawn(overworld, spawnBase, pos, SpawnKind.OUTERMAN);
            }
            // 1 saucer
            for (int i = 0; i < SAUCERS_PER_PLAYER; i++) {
                BlockPos pos = findSkySafeSurface(overworld, p.getBlockPos());
                queueSpawn(overworld, spawnBase, pos, SpawnKind.SAUCER);
            }
        }
    }

    private void queueSpawn(ServerWorld overworld, long spawnTick, BlockPos pos, SpawnKind kind) {
        // pillar FX to nearby players
        AlienInvasionNet.sendPillarFxNear(overworld, Vec3d.ofCenter(pos), 1.15f, 96.0f, BEAM_TICKS);

        pending.add(new PendingSpawn(kind, pos, spawnTick));
    }

    private void spawnEntityNow(ServerWorld overworld, PendingSpawn ps) {
        Entity e;
        if (ps.kind == SpawnKind.OUTERMAN) {
            e = ModEntities.OUTERMAN.create(overworld);
        } else {
            e = ModEntities.UFO_SAUCER.create(overworld);
        }
        if (e == null) return;

        double x = ps.pos.getX() + 0.5;
        double y = ps.pos.getY();
        double z = ps.pos.getZ() + 0.5;

        e.refreshPositionAndAngles(x, y, z, overworld.random.nextFloat() * 360f, 0f);
        e.addCommandTag(TAG);

        if (e instanceof MobEntity mob) {
            mob.initialize(overworld, overworld.getLocalDifficulty(ps.pos), SpawnReason.EVENT, null, null);
        }

        overworld.spawnEntity(e);
        alive.add(e.getUuid());
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

            // sky must be visible at/above spawn
            if (!overworld.isSkyVisible(surface.up())) continue;

            // avoid spawning on leaves / liquids (basic sanity)
            var below = surface.down();
            var st = overworld.getBlockState(below);
            if (st.isAir() || st.getFluidState().isStill()) continue;

            return surface;
        }

        // fallback: player topY
        int y = overworld.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, origin.getX(), origin.getZ());
        BlockPos surface = new BlockPos(origin.getX(), y, origin.getZ());
        return surface;
    }

    private enum SpawnKind { OUTERMAN, SAUCER }

    private record PendingSpawn(SpawnKind kind, BlockPos pos, long spawnTick) {}
}