package net.seep.odd.abilities.tamer.data;

import java.util.UUID;

public interface TamerData {
    boolean odd$isTamed();
    void odd$setTamed(boolean b);

    UUID odd$getTamerOwner();
    void odd$setTamerOwner(UUID id);

    int odd$getLevel();
    void odd$setLevel(int lvl);

    int odd$getXp();
    void odd$setXp(int xp);

    default void odd$addXp(int amount) { odd$setXp(Math.max(0, odd$getXp() + amount)); }
}
