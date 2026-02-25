package net.seep.odd.event.alien.client.sound;

import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.random.Random;

public final class LoopingEventSound extends AbstractSoundInstance {

    public LoopingEventSound(SoundEvent event, SoundCategory category) {
        super(event, category, Random.create());
        this.repeat = true;
        this.repeatDelay = 0;
        this.volume = 1.0f;
        this.pitch = 1.0f;
        this.relative = true; // global / non-positional
        this.x = 0;
        this.y = 0;
        this.z = 0;
    }
}