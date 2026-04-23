package net.seep.odd.device.store;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

public final class DabloonStoreMusic {
    public static final String NONE = "none";

    private DabloonStoreMusic() {}

    public static List<String> allSelectableIds() {
        List<String> out = new ArrayList<>();
        out.add(NONE);

        for (SoundEvent sound : Registries.SOUND_EVENT) {
            Identifier id = Registries.SOUND_EVENT.getId(sound);
            if (id == null) continue;
            if (!Oddities.MOD_ID.equals(id.getNamespace())) continue;

            String path = id.getPath();
            if (path.contains("music") || path.contains("song") || path.contains("shop")) {
                out.add(id.toString());
            }
        }

        out.sort(Comparator.naturalOrder());
        if (!out.contains(NONE)) {
            out.add(0, NONE);
        }
        return out;
    }

    public static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        if (NONE.equals(value)) {
            return NONE;
        }
        Identifier id = Identifier.tryParse(value.trim());
        if (id == null || !Registries.SOUND_EVENT.containsId(id)) {
            return NONE;
        }
        return id.toString();
    }
}
