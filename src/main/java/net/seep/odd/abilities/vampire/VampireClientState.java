package net.seep.odd.abilities.vampire;

/**
 * Common-safe: exists on both sides.
 * Client updates this via S2C_VAMPIRE_FLAG.
 */
public final class VampireClientState {
    private VampireClientState() {}

    private static volatile boolean clientIsVampire = false;

    public static boolean isClientVampire() {
        return clientIsVampire;
    }

    public static void setClientVampire(boolean v) {
        clientIsVampire = v;
    }
}
