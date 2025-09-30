package net.seep.odd.client.audio;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.random.Random;
import net.seep.odd.entity.car.RiderCarEntity;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

@Environment(EnvType.CLIENT)
public class CarRadioSound extends MovingSoundInstance {
    private final RiderCarEntity car;
    public final int trackIdx;

    public CarRadioSound(RiderCarEntity car, SoundEvent event, int trackIdx) {
        super(event, SoundCategory.RECORDS, Random.create());
        this.car = car;
        this.trackIdx = trackIdx;

        this.repeat = true;
        this.repeatDelay = 0;
        this.attenuationType = SoundInstance.AttenuationType.LINEAR;

        this.volume = car.getRadioVolumeClient();
        this.pitch = 1.0f;

        this.x = (float)car.getX();
        this.y = (float)car.getY();
        this.z = (float)car.getZ();
    }

    @Override
    public void tick() {
        if (car.isRemoved() || !car.isAlive() || !car.isRadioOnClient()
                || car.getRadioTrackIndex() != this.trackIdx) {
            this.setDone();   // <- instead of: this.done = true;
            return;
        }
        this.x = (float)car.getX();
        this.y = (float)car.getY();
        this.z = (float)car.getZ();
        this.volume = car.getRadioVolumeClient();
    }

    public void stopNow() {
        this.setDone();       // <- instead of: this.done = true;
    }
    public void setVol(float v) { this.volume = v; }
}
