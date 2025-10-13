package net.seep.odd.abilities.power;

/** Implement this if a slot should use "charges" instead of a single cooldown. */
public interface ChargedPower {
    /** Return true if this slot (primary/secondary/third/fourth) uses charges. */
    default boolean usesCharges(String slot) { return false; }

    /** Maximum number of charges for this slot. */
    default int maxCharges(String slot) { return 1; }

    /** Time (ticks) to regenerate ONE charge for this slot (independent timers). */
    default long rechargeTicks(String slot) { return 20L; } // 1s default
}
