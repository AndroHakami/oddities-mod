package net.seep.odd.entity.car.radio;

import net.seep.odd.entity.car.RiderCarEntity;
import net.seep.odd.sound.ModSounds; // your sound singletons

public final class RadioTracksInit {
    private RadioTracksInit() {}

    /** Call once during common init, after ModSounds are registered. */
    public static void init() {
        // Title can be anything; add as many as you want.
        RiderCarEntity.addRadioTrack(ModSounds.RADIO_TRACK1, "Sunset Highway");
        RiderCarEntity.addRadioTrack(ModSounds.RADIO_TRACK2, "Midnight Drift");
        RiderCarEntity.addRadioTrack(ModSounds.RADIO_TRACK3, "Billy Idol - Eyes without a Face (Ryan Gosling Style)");
        RiderCarEntity.addRadioTrack(ModSounds.RADIO_TRACK4, "Gran Turismo 3 - Light Velocity");
        // RiderCarEntity.addRadioTrack(ModSounds.RADIO_TRACK_3, "Neon Run");
        // ...
    }
}
