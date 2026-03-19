package net.seep.odd.entity.bosswitch.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.seep.odd.client.audio.DistantIslesLoopingEventSound;
import net.seep.odd.entity.bosswitch.BossWitchEntity;
import net.seep.odd.sound.ModSounds;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class BossWitchMusicClient {
    private BossWitchMusicClient() {}

    private static final double RANGE = 96.0D;

    private static SoundInstance current = null;
    private static UUID currentBossUuid = null;
    private static int currentPhase = 0;

    private static UUID pendingPhaseTwoBoss = null;
    private static int pendingPhaseTwoTicks = -1;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(BossWitchMusicClient::tick);
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            stop(client);
            resetPending();
            return;
        }

        BossWitchEntity nearest = findNearestBoss(client);
        if (nearest == null) {
            stop(client);
            resetPending();
            return;
        }

        SoundManager soundManager = client.getSoundManager();
        int phase = nearest.getBossPhase();
        UUID uuid = nearest.getUuid();

        if (phase == 1) {
            resetPending();

            if (current != null && currentBossUuid != null && currentBossUuid.equals(uuid) && currentPhase == 1 && soundManager.isPlaying(current)) {
                return;
            }

            stop(client);
            client.getMusicTracker().stop();

            currentBossUuid = uuid;
            currentPhase = 1;
            current = new DistantIslesLoopingEventSound(ModSounds.WITCH_BOSS, 1.0f);
            soundManager.play(current);
            return;
        }

        // phase 2
        if (current != null && currentBossUuid != null && currentBossUuid.equals(uuid) && currentPhase == 2 && soundManager.isPlaying(current)) {
            return;
        }

        if (pendingPhaseTwoBoss == null || !pendingPhaseTwoBoss.equals(uuid)) {
            stop(client);
            currentBossUuid = null;
            currentPhase = 0;
            pendingPhaseTwoBoss = uuid;
            pendingPhaseTwoTicks = 40; // 2 seconds
            return;
        }

        if (pendingPhaseTwoTicks > 0) {
            pendingPhaseTwoTicks--;
            return;
        }

        stop(client);
        client.getMusicTracker().stop();

        currentBossUuid = uuid;
        currentPhase = 2;
        current = new DistantIslesLoopingEventSound(ModSounds.WITCH_BOSS_2, 1.0f);
        soundManager.play(current);

        resetPending();
    }

    private static void resetPending() {
        pendingPhaseTwoBoss = null;
        pendingPhaseTwoTicks = -1;
    }

    private static BossWitchEntity findNearestBoss(MinecraftClient client) {
        List<BossWitchEntity> bosses = client.world.getEntitiesByClass(
                BossWitchEntity.class,
                client.player.getBoundingBox().expand(RANGE),
                boss -> boss.isAlive() && !boss.isRemoved()
        );

        return bosses.stream()
                .min(Comparator.comparingDouble(client.player::squaredDistanceTo))
                .orElse(null);
    }

    public static void stop() {
        stop(MinecraftClient.getInstance());
    }

    private static void stop(MinecraftClient client) {
        if (client == null) return;

        SoundManager soundManager = client.getSoundManager();
        if (current != null) {
            soundManager.stop(current);
            current = null;
        }

        currentBossUuid = null;
        currentPhase = 0;
    }
}