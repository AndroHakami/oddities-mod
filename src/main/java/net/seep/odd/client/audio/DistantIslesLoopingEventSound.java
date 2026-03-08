package net.seep.odd.client.audio;

import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.seep.odd.client.audio.DistantIslesMusicVolume;
import net.seep.odd.event.alien.client.sound.LoopingEventSound;

public final class DistantIslesLoopingEventSound extends LoopingEventSound {
    private final float baseVolume;

    public DistantIslesLoopingEventSound(SoundEvent sound, float baseVolume) {
        super(sound, SoundCategory.MASTER);
        this.baseVolume = baseVolume;
    }

    @Override
    public SoundCategory getCategory() {
        return SoundCategory.MASTER;
    }

    @Override
    public float getVolume() {
        return this.baseVolume * DistantIslesMusicVolume.getFloat();
    }
}