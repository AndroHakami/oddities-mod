package net.seep.odd.entity.ufo.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.seep.odd.entity.ufo.OuterMechEntity;
import net.seep.odd.sound.ModSounds;

@Environment(EnvType.CLIENT)
public final class OuterMechLoopingAudio {
    private OuterMechLoopingAudio() {}

    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static final Long2ObjectOpenHashMap<StateSoundInstance> SOUNDS = new Long2ObjectOpenHashMap<>();
    private static boolean inited = false;

    private enum Channel {
        WALK(true),
        RUN(true),
        LASER_CHARGE(false),
        LASER_LOOP(true);

        final boolean repeating;

        Channel(boolean repeating) {
            this.repeating = repeating;
        }
    }

    public static void init() {
        if (inited) return;
        inited = true;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> tickClient());
    }

    private static void tickClient() {
        if (client == null || client.world == null || client.getSoundManager() == null) {
            stopAll();
            return;
        }

        for (OuterMechEntity mech : client.world.getEntitiesByClass(
                OuterMechEntity.class,
                client.player != null ? client.player.getBoundingBox().expand(220.0) : nullSafeHugeBox(),
                e -> e.isAlive() && !e.isRemoved()
        )) {
            ensureStateSound(mech, Channel.WALK, ModSounds.MECH_WALK,
                    mech.isWalkAnimating(), 1.20f);

            ensureStateSound(mech, Channel.RUN, ModSounds.MECH_RUN,
                    mech.isRunAnimating(), 1.45f);

            ensureStateSound(mech, Channel.LASER_CHARGE, ModSounds.MECH_LASER_CHARGE,
                    mech.isLaserCharging(), Math.max(0.18f, mech.getWarmupAlpha() * 1.55f));

            ensureStateSound(mech, Channel.LASER_LOOP, ModSounds.MECH_LASER_LOOP,
                    mech.isLaserLooping(), Math.max(0.22f, mech.getBeamAlpha() * 1.75f));
        }

        var it = SOUNDS.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var entry = it.next();
            StateSoundInstance inst = entry.getValue();

            if (!client.getSoundManager().isPlaying(inst) && !inst.isActive()) {
                inst.forceStop();
            }

            if (inst.isReadyToRemove()) {
                it.remove();
            }
        }
    }

    private static net.minecraft.util.math.Box nullSafeHugeBox() {
        return new net.minecraft.util.math.Box(-3.0E7, -2048, -3.0E7, 3.0E7, 2048, 3.0E7);
    }

    private static void ensureStateSound(OuterMechEntity mech, Channel channel, SoundEvent sound, boolean active, float targetVolume) {
        long key = (((long) mech.getId()) << 4) | channel.ordinal();
        StateSoundInstance existing = SOUNDS.get(key);

        if (existing == null || existing.isReadyToRemove()) {
            if (!active) return;

            existing = new StateSoundInstance(mech, sound, channel.repeating, targetVolume);
            SOUNDS.put(key, existing);
            client.getSoundManager().play(existing);
        }

        existing.setTarget(active, active ? targetVolume : 0.0f);
    }

    private static void stopAll() {
        for (StateSoundInstance inst : SOUNDS.values()) {
            inst.forceStop();
        }
        SOUNDS.clear();
    }

    private static final class StateSoundInstance extends MovingSoundInstance {
        private final OuterMechEntity mech;
        private final boolean repeatingSound;
        private float targetVolume;
        private boolean active;
        private boolean readyToRemove = false;

        private StateSoundInstance(OuterMechEntity mech, SoundEvent sound, boolean repeatingSound, float initialVolume) {
            super(sound, SoundCategory.HOSTILE, SoundInstance.createRandom());
            this.mech = mech;
            this.repeatingSound = repeatingSound;
            this.repeat = repeatingSound;
            this.repeatDelay = 0;
            this.active = true;
            this.targetVolume = Math.max(0.0f, initialVolume);
            this.volume = Math.max(0.0f, initialVolume);
            this.pitch = 1.0f;
            this.x = mech.getX();
            this.y = mech.getY();
            this.z = mech.getZ();
            this.attenuationType = AttenuationType.NONE;
        }

        void setTarget(boolean active, float volume) {
            this.active = active;
            this.targetVolume = Math.max(0.0f, volume);
        }

        boolean isActive() {
            return this.active;
        }

        boolean isReadyToRemove() {
            return this.readyToRemove;
        }

        void forceStop() {
            this.setDone();
            this.readyToRemove = true;
        }

        @Override
        public void tick() {
            if (this.mech == null || this.mech.isRemoved() || !this.mech.isAlive()) {
                this.active = false;
                this.targetVolume = 0.0f;
            }

            if (this.mech != null) {
                this.x = this.mech.getX();
                this.y = this.mech.getY();
                this.z = this.mech.getZ();
            }

            if (this.repeatingSound) {
                this.volume = net.minecraft.util.math.MathHelper.lerp(0.22f, this.volume, this.targetVolume);

                if (!this.active && this.targetVolume <= 0.001f && this.volume <= 0.01f) {
                    this.setDone();
                    this.readyToRemove = true;
                }
                return;
            }

            if (this.active) {
                this.volume = this.targetVolume;
            } else {
                this.volume = net.minecraft.util.math.MathHelper.lerp(0.28f, this.volume, 0.0f);
                if (this.volume <= 0.01f) {
                    this.setDone();
                    this.readyToRemove = true;
                }
            }
        }
    }
}