// RiderRadioClient.java (client-only)
package net.seep.odd.client.audio;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvent;
import net.seep.odd.entity.car.RiderCarEntity;

import java.util.*;

@Environment(EnvType.CLIENT)
public final class RiderRadioClient {
    private static final Map<Integer, CarRadioSound> PLAYING = new HashMap<>();

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(RiderRadioClient::tick);
    }

    private static void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return;

        // seen set for cleanup
        Set<Integer> seen = new HashSet<>();

        // only cars near the player
        double r = 64.0;
        var cars = mc.world.getEntitiesByClass(
                RiderCarEntity.class,
                mc.player.getBoundingBox().expand(r, r, r),
                e -> true
        );

        for (RiderCarEntity car : cars) {
            int id = car.getId();
            seen.add(id);

            if (!car.isRadioOnClient()) { stop(id); continue; }
            int idx = car.getRadioTrackIndex();
            SoundEvent evt = RiderCarEntity.getRadioSoundAt(idx);
            if (evt == null) { stop(id); continue; }

            CarRadioSound cur = PLAYING.get(id);
            if (cur == null || cur.trackIdx != idx) {
                if (cur != null) cur.stopNow();
                cur = new CarRadioSound(car, evt, idx);
                mc.getSoundManager().play(cur);
                PLAYING.put(id, cur);
            } else {
                cur.setVol(car.getRadioVolumeClient());
            }
        }

        // stop sounds for cars we didn't see this tick
        PLAYING.entrySet().removeIf(e -> {
            if (!seen.contains(e.getKey())) { e.getValue().stopNow(); return true; }
            return false;
        });
    }

    private static void stop(int id) {
        CarRadioSound s = PLAYING.remove(id);
        if (s != null) s.stopNow();
    }
}
