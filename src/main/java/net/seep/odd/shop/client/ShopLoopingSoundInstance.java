package net.seep.odd.shop.client;

import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

public final class ShopLoopingSoundInstance extends AbstractSoundInstance {

    private boolean stopped = false;

    public ShopLoopingSoundInstance(SoundEvent event) {
        // RECORDS = “Jukebox / Note Blocks” slider
        super(event, SoundCategory.RECORDS, SoundInstance.createRandom());
        this.repeat = true;
        this.repeatDelay = 0;
        this.volume = 0.9f;
        this.relative = true; // UI-style
    }

    public void stopNow() {
        stopped = true;
    }


    public boolean isDone() {
        return stopped;
    }
}
