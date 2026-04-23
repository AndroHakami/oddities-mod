package net.seep.odd.entity.ufo.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.seep.odd.entity.ufo.UfoSaucerEntity;
import net.seep.odd.entity.ufo.UfoSlicerEntity;
import net.seep.odd.sound.ModSounds;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

@Environment(EnvType.CLIENT)
public final class UfoLoopingAudio {
    private static boolean inited = false;
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();

    private static final Map<Integer, EntityLoopSound<UfoSaucerEntity>> SAUCER_HOVERS = new HashMap<>();
    private static final Map<Integer, EntityLoopSound<UfoSaucerEntity>> SAUCER_TRACTORS = new HashMap<>();
    private static final Map<Integer, EntityLoopSound<UfoSlicerEntity>> SLICER_HOVERS = new HashMap<>();

    private UfoLoopingAudio() {}

    public static void init() {
        if (inited) return;
        inited = true;

        ClientTickEvents.END_CLIENT_TICK.register(UfoLoopingAudio::tick);
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            stopAll();
            return;
        }

        Box range = client.player.getBoundingBox().expand(192.0);

        Set<Integer> saucerHoverSeen = new HashSet<>();
        Set<Integer> saucerTractorSeen = new HashSet<>();
        Set<Integer> slicerHoverSeen = new HashSet<>();

        for (UfoSaucerEntity saucer : client.world.getEntitiesByClass(UfoSaucerEntity.class, range, Entity::isAlive)) {
            saucerHoverSeen.add(saucer.getId());
            ensureSaucerHover(saucer);

            if (saucer.isTractorBeamActive()) {
                saucerTractorSeen.add(saucer.getId());
                ensureSaucerTractor(saucer);
            }
        }

        for (UfoSlicerEntity slicer : client.world.getEntitiesByClass(UfoSlicerEntity.class, range, Entity::isAlive)) {
            slicerHoverSeen.add(slicer.getId());
            ensureSlicerHover(slicer);
        }

        prune(SAUCER_HOVERS, saucerHoverSeen);
        prune(SAUCER_TRACTORS, saucerTractorSeen);
        prune(SLICER_HOVERS, slicerHoverSeen);
    }

    private static void ensureSaucerHover(UfoSaucerEntity entity) {
        SAUCER_HOVERS.computeIfAbsent(entity.getId(), id -> {
            EntityLoopSound<UfoSaucerEntity> sound = new EntityLoopSound<>(
                    entity,
                    ModSounds.SAUCER_HOVER,
                    0.58f,
                    1.00f,
                    e -> e.isAlive()
            );
            CLIENT.getSoundManager().play(sound);
            return sound;
        });
    }

    private static void ensureSaucerTractor(UfoSaucerEntity entity) {
        SAUCER_TRACTORS.computeIfAbsent(entity.getId(), id -> {
            EntityLoopSound<UfoSaucerEntity> sound = new EntityLoopSound<>(
                    entity,
                    ModSounds.SAUCER_TRACTOR,
                    0.72f,
                    1.00f,
                    UfoSaucerEntity::isTractorBeamActive
            );
            CLIENT.getSoundManager().play(sound);
            return sound;
        });
    }

    private static void ensureSlicerHover(UfoSlicerEntity entity) {
        SLICER_HOVERS.computeIfAbsent(entity.getId(), id -> {
            EntityLoopSound<UfoSlicerEntity> sound = new EntityLoopSound<>(
                    entity,
                    ModSounds.SAUCER_HOVER,
                    0.44f,
                    1.12f,
                    e -> e.isAlive()
            );
            CLIENT.getSoundManager().play(sound);
            return sound;
        });
    }

    private static <T extends Entity> void prune(Map<Integer, EntityLoopSound<T>> map, Set<Integer> seenIds) {
        map.entrySet().removeIf(entry -> {
            EntityLoopSound<T> sound = entry.getValue();
            boolean remove = !seenIds.contains(entry.getKey()) || sound.isDone();
            if (remove) {
                sound.stopNow();
            }
            return remove;
        });
    }

    private static void stopAll() {
        SAUCER_HOVERS.values().forEach(EntityLoopSound::stopNow);
        SAUCER_TRACTORS.values().forEach(EntityLoopSound::stopNow);
        SLICER_HOVERS.values().forEach(EntityLoopSound::stopNow);

        SAUCER_HOVERS.clear();
        SAUCER_TRACTORS.clear();
        SLICER_HOVERS.clear();
    }

    private static final class EntityLoopSound<T extends Entity> extends MovingSoundInstance {
        private final T entity;
        private final Predicate<T> activeWhen;
        private final float baseVolume;
        private final float basePitch;

        private EntityLoopSound(T entity, SoundEvent sound, float baseVolume, float basePitch, Predicate<T> activeWhen) {
            super(sound, SoundCategory.HOSTILE, Random.create());
            this.entity = entity;
            this.activeWhen = activeWhen;
            this.baseVolume = baseVolume;
            this.basePitch = basePitch;

            this.repeat = true;
            this.repeatDelay = 0;
            this.volume = 0.001f;
            this.pitch = basePitch;
            this.x = (float) entity.getX();
            this.y = (float) entity.getY();
            this.z = (float) entity.getZ();
        }

        @Override
        public boolean canPlay() {
            return true;
        }

        @Override
        public void tick() {
            if (entity == null || entity.isRemoved() || !entity.isAlive() || !activeWhen.test(entity)) {
                stopNow();
                return;
            }

            this.x = (float) entity.getX();
            this.y = (float) entity.getY();
            this.z = (float) entity.getZ();

            float motion = (float) entity.getVelocity().length();
            float targetVolume = MathHelper.clamp(baseVolume + motion * 0.08f, 0.0f, 1.0f);
            float targetPitch = MathHelper.clamp(basePitch + motion * 0.03f, 0.5f, 2.0f);

            this.volume = MathHelper.lerp(0.20f, this.volume, targetVolume);
            this.pitch = MathHelper.lerp(0.20f, this.pitch, targetPitch);
        }

        private void stopNow() {
            this.volume = 0.0f;
            this.setDone();
        }
    }
}