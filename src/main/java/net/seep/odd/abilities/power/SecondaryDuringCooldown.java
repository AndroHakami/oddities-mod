package net.seep.odd.abilities.power;

/**
 * Marker interface: if a Power implements this, PowerAPI will let
 * activateSecondary(...) be called even while the secondary cooldown is active.
 * The Power must ensure it only performs allowed actions during cooldown.
 */
public interface SecondaryDuringCooldown {}
