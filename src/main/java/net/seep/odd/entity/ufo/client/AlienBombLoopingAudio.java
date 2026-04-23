package net.seep.odd.entity.ufo.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.seep.odd.entity.ufo.AlienBombEntity;
import net.seep.odd.sound.ModSounds;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Environment(EnvType.CLIENT)
public final class AlienBombLoopingAudio {
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
    private static boolean inited = false;

    private static final Map<Integer, BombFallLoop> LOOPS = new HashMap<>();

    private AlienBombLoopingAudio() {}

    public static void init() {
        if (inited) return;
        inited = true;

        ClientTickEvents.END_CLIENT_TICK.register(AlienBombLoopingAudio::tick);
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            LOOPS.values().forEach(BombFallLoop::stopNow);
            LOOPS.clear();
            return;
        }

        Box range = client.player.getBoundingBox().expand(192.0);
        Set<Integer> seen = new HashSet<>();

        for (AlienBombEntity bomb : client.world.getEntitiesByClass(AlienBombEntity.class, range, e -> e.isAlive() && !e.isRemoved())) {
            seen.add(bomb.getId());
            LOOPS.computeIfAbsent(bomb.getId(), id -> {
                BombFallLoop loop = new BombFallLoop(bomb);
                CLIENT.getSoundManager().play(loop);
                return loop;
            });
        }

        LOOPS.entrySet().removeIf(entry -> {
            BombFallLoop loop = entry.getValue();
            boolean remove = !seen.contains(entry.getKey()) || loop.isDone();
            if (remove) loop.stopNow();
            return remove;
        });
    }

    private static final class BombFallLoop extends MovingSoundInstance {
        private final AlienBombEntity bomb;

        private BombFallLoop(AlienBombEntity bomb) {
            super(ModSounds.ALIEN_BOMB_FALLING, SoundCategory.HOSTILE, Random.create());
            this.bomb = bomb;
            this.repeat = true;
            this.repeatDelay = 0;
            this.volume = 0.001f;
            this.pitch = 1.0f;
            this.x = (float) bomb.getX();
            this.y = (float) bomb.getY();
            this.z = (float) bomb.getZ();
        }

        @Override
        public boolean canPlay() {
            return true;
        }

        @Override
        public void tick() {
            if (bomb == null || bomb.isRemoved() || !bomb.isAlive()) {
                stopNow();
                return;
            }

            this.x = (float) bomb.getX();
            this.y = (float) bomb.getY();
            this.z = (float) bomb.getZ();

            float fallSpeed = (float) Math.max(0.0, -bomb.getVelocity().y);
            float targetVolume = MathHelper.clamp(0.45f + fallSpeed * 0.55f, 0.2f, 1.0f);
            float targetPitch = MathHelper.clamp(0.92f + fallSpeed * 0.18f, 0.8f, 1.35f);

            this.volume = MathHelper.lerp(0.25f, this.volume, targetVolume);
            this.pitch = MathHelper.lerp(0.25f, this.pitch, targetPitch);
        }

        private void stopNow() {
            this.volume = 0.0f;
            this.setDone();
        }
    }
}