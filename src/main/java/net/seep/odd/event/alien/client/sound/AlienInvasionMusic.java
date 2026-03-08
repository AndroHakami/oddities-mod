package net.seep.odd.event.alien.client.sound;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.world.World;
import net.seep.odd.client.audio.DistantIslesLoopingEventSound;
import net.seep.odd.sound.ModSounds;

public final class AlienInvasionMusic {
    private AlienInvasionMusic() {}

    private static SoundInstance current = null;

    public static void ensurePlaying() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        if (mc.world.getRegistryKey() != World.OVERWORLD) return;

        SoundManager sm = mc.getSoundManager();
        if (current != null && sm.isPlaying(current)) return;

        mc.getMusicTracker().stop();

        current = new DistantIslesLoopingEventSound(ModSounds.ALIEN_INVASION, 1.0f);
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
    }
}