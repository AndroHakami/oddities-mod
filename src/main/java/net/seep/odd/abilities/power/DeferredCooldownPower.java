package net.seep.odd.abilities.power;

/** Optional marker for powers that want cooldown to begin on "fire" rather than on "press". */
public interface DeferredCooldownPower {
    default boolean deferPrimaryCooldown()   { return false; }
    default boolean deferSecondaryCooldown() { return false; }
    default boolean deferThirdCooldown()     { return false; }
}
