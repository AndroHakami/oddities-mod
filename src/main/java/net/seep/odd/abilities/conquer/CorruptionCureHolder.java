package net.seep.odd.abilities.conquer;

/** Implemented by mixins on entities that support corruption curing. */
public interface CorruptionCureHolder {
    int odd$getCorruptionCureTicks();
    void odd$setCorruptionCureTicks(int ticks);
}
