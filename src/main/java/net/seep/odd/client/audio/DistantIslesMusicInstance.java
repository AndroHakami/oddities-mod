// FILE: src/main/java/net/seep/odd/client/audio/DistantIslesMusicInstance.java
package net.seep.odd.client.audio;

import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.random.Random;

public final class DistantIslesMusicInstance extends AbstractSoundInstance implements DistantIslesRoutedSound {
    public DistantIslesMusicInstance(SoundEvent soundEvent, float volume, boolean loop) {
        super(soundEvent.getId(), SoundCategory.MUSIC, Random.create());

        this.volume = volume;
        this.pitch = 1.0f;
        this.repeat = loop;
        this.repeatDelay = 0;
        this.relative = true;
        this.attenuationType = SoundInstance.AttenuationType.NONE;
        this.x = 0.0;
        this.y = 0.0;
        this.z = 0.0;
    }
}