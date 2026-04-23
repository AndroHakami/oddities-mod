package net.seep.odd.device.store.client;

import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.random.Random;

public final class StoreLoopingSoundInstance extends MovingSoundInstance {
    private boolean stopped;

    public StoreLoopingSoundInstance(SoundEvent sound) {
        super(sound, SoundCategory.RECORDS, Random.create());
        this.repeat = true;
        this.repeatDelay = 0;
        this.volume = 0.55f;
        this.pitch = 1.0f;
        this.relative = true;
        this.x = 0.0;
        this.y = 0.0;
        this.z = 0.0;
    }

    @Override
    public void tick() {
        if (stopped) {
            setDone();
        }
    }

    public void stopNow() {
        this.stopped = true;
    }
}
