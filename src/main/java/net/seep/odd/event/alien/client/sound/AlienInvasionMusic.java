package net.seep.odd.event.alien.client.sound;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.sound.SoundEvent;
import net.minecraft.world.World;
import net.seep.odd.client.audio.DistantIslesLoopingEventSound;
import net.seep.odd.event.alien.client.AlienInvasionClientState;
import net.seep.odd.sound.ModSounds;

public final class AlienInvasionMusic {
    private AlienInvasionMusic() {}

    private static SoundInstance current = null;
    private static int currentPhase = -1;

    public static void ensurePlaying() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        if (mc.world.getRegistryKey() != World.OVERWORLD) return;

        int phase = Math.max(1, AlienInvasionClientState.wave());
        SoundEvent wanted = getPhaseMusic(phase);

        SoundManager sm = mc.getSoundManager();
        boolean samePhase = current != null && currentPhase == phase && sm.isPlaying(current);
        if (samePhase) return;

        if (current != null) {
            sm.stop(current);
            current = null;
        }

        mc.getMusicTracker().stop();

        current = new DistantIslesLoopingEventSound(wanted, 1.0f);
        currentPhase = phase;
        sm.play(current);
    }

    public static void stop() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        SoundManager sm = mc.getSoundManager();
        if (current != null) {
            sm.stop(current);
            current = null;
        }
        currentPhase = -1;
    }

    private static SoundEvent getPhaseMusic(int phase) {
        return switch (phase) {
            case 2 -> ModSounds.ALIEN_INVASION_2;
            case 3 -> ModSounds.ALIEN_INVASION_3;
            case 4 -> ModSounds.ALIEN_INVASION_BOSS;
            default -> ModSounds.ALIEN_INVASION;
        };
    }
}
