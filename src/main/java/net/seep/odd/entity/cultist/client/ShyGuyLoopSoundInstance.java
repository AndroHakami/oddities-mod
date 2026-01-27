package net.seep.odd.entity.cultist.client;

import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.entity.cultist.ShyGuyEntity;

public final class ShyGuyLoopSoundInstance extends MovingSoundInstance {
    private final ShyGuyEntity entity;

    public ShyGuyLoopSoundInstance(ShyGuyEntity entity, SoundEvent event, float volume, float pitch) {
        super(event, SoundCategory.HOSTILE, entity.getRandom());
        this.entity = entity;

        this.repeat = true;
        this.repeatDelay = 0;

        this.volume = volume;
        this.pitch = pitch;

        this.x = entity.getX();
        this.y = entity.getY();
        this.z = entity.getZ();
    }

    @Override
    public void tick() {
        if (entity == null || entity.isRemoved() || !entity.isAlive()) {
            this.setDone();
            return;
        }

        this.x = entity.getX();
        this.y = entity.getY();
        this.z = entity.getZ();

        // small dynamic volume when far / silent, but still looped
        this.volume = MathHelper.clamp(this.volume, 0.0f, 2.0f);
    }
}
