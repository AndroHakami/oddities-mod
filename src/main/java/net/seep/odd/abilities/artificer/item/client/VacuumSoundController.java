// FILE: src/main/java/net/seep/odd/abilities/artificer/item/client/VacuumSoundController.java
package net.seep.odd.abilities.artificer.item.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.seep.odd.abilities.artificer.item.ArtificerVacuumItem;
import net.seep.odd.sound.ModSounds;

@Environment(EnvType.CLIENT)
public final class VacuumSoundController {
    private VacuumSoundController() {}

    private static boolean inited = false;

    private static LoopSound idle;
    private static LoopSound suck;

    public static void init() {
        if (inited) return;
        inited = true;
        ClientTickEvents.END_CLIENT_TICK.register(VacuumSoundController::tick);
    }

    private static void tick(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.world == null) {
            fadeOutAll();
            cleanupDead();
            return;
        }

        PlayerEntity p = mc.player;

        boolean holding = isHoldingVacuum(p);
        boolean suckingNow = holding && p.isUsingItem() && (p.getActiveItem().getItem() instanceof ArtificerVacuumItem);

        // ---------- IDLE ----------
        if (holding) {
            float idleBase = 0.35f;
            float desiredIdle = suckingNow ? idleBase * 1.4f : idleBase;

            if (idle == null || idle.isDone()) {
                idle = new LoopSound(ModSounds.VACUUM_IDLE, SoundCategory.PLAYERS, p);

                // IMPORTANT: start at epsilon so the engine actually starts the source
                idle.primeEpsilon();

                idle.setPitch(1.0f);
                idle.setTargetVolume(desiredIdle); // set target BEFORE play
                mc.getSoundManager().play(idle);
            } else {
                idle.setTargetVolume(desiredIdle);
            }
        } else {
            if (idle != null) idle.fadeOutAndStop();
        }

        // ---------- SUCK ----------
        if (suckingNow) {
            float desiredSuck = 0.55f;

            if (suck == null || suck.isDone()) {
                suck = new LoopSound(ModSounds.VACUUM_SUCK, SoundCategory.PLAYERS, p);

                // IMPORTANT: start at epsilon so the engine actually starts the source
                suck.primeEpsilon();

                suck.setPitch(1.0f);
                suck.setTargetVolume(desiredSuck); // set target BEFORE play
                mc.getSoundManager().play(suck);
            } else {
                suck.setTargetVolume(desiredSuck);
            }
        } else {
            if (suck != null) suck.fadeOutAndStop();
        }

        cleanupDead();
    }

    private static boolean isHoldingVacuum(PlayerEntity p) {
        return (p.getMainHandStack().getItem() instanceof ArtificerVacuumItem)
                || (p.getOffHandStack().getItem() instanceof ArtificerVacuumItem);
    }

    private static void fadeOutAll() {
        if (idle != null) idle.fadeOutAndStop();
        if (suck != null) suck.fadeOutAndStop();
    }

    private static void cleanupDead() {
        if (idle != null && idle.isDone()) idle = null;
        if (suck != null && suck.isDone()) suck = null;
    }

    /** Looping sound pinned to player position, smooth fade in/out. */
    private static final class LoopSound extends MovingSoundInstance {
        private final PlayerEntity owner;

        private boolean stopped = false;
        private boolean stopWhenSilent = false;

        private float targetVol = 0.0f;
        private float curVol = 0.0f;

        private float basePitch = 1.0f;

        // Fade speed: lower = slower fade
        private static final float FADE_LERP = 0.20f;

        LoopSound(SoundEvent event, SoundCategory cat, PlayerEntity owner) {
            super(event, cat, Random.create());
            this.owner = owner;

            this.repeat = true;
            this.repeatDelay = 0;

            // no attenuation, but still a “world” sound pinned to player
            this.attenuationType = SoundInstance.AttenuationType.NONE;
            this.relative = false;

            // start effectively silent (but we’ll prime epsilon before play)
            this.volume = 0.0f;
            this.pitch = 1.0f;

            if (owner != null) {
                this.x = owner.getX();
                this.y = owner.getY();
                this.z = owner.getZ();
            }
        }

        /** Call before SoundManager.play() so MC actually allocates a source. */
        void primeEpsilon() {
            this.curVol = 0.001f;
            this.volume = 0.001f;
        }

        void setPitch(float pitch) {
            this.basePitch = pitch;
        }

        void setTargetVolume(float vol) {
            this.targetVol = MathHelper.clamp(vol, 0.0f, 10.0f);
            this.stopWhenSilent = false;
        }

        void fadeOutAndStop() {
            this.targetVol = 0.0f;
            this.stopWhenSilent = true;
        }

        @Override
        public boolean isDone() {
            return stopped;
        }

        @Override
        public void tick() {
            if (stopped) return;

            if (owner == null || owner.isRemoved()) {
                stopped = true;
                return;
            }

            // pin to player so it doesn't pan weirdly
            this.x = owner.getX();
            this.y = owner.getY();
            this.z = owner.getZ();

            // fade
            curVol = MathHelper.lerp(FADE_LERP, curVol, targetVol);
            this.volume = curVol;
            this.pitch = basePitch;

            if (stopWhenSilent && curVol < 0.0012f) {
                stopped = true;
            }
        }
    }
}