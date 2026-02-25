package net.seep.odd.sound;

import net.minecraft.sound.SoundCategory;

public final class OddSoundCategories {
    private OddSoundCategories() {}

    private static SoundCategory DISTANT_ISLES = SoundCategory.MUSIC;

    public static void setDistantIsles(SoundCategory cat) {
        if (cat != null) DISTANT_ISLES = cat;
    }

    public static SoundCategory distantIslesOrMusic() {
        return DISTANT_ISLES != null ? DISTANT_ISLES : SoundCategory.MUSIC;
    }
}