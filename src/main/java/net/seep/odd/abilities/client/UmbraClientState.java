package net.seep.odd.abilities.client;

public final class UmbraClientState {
    private static boolean active;
    private static float energy;
    private static float max;

    public static void update(boolean a, float e, float m) {
        active = a; energy = e; max = Math.max(1f, m);
    }

    public static boolean active() { return active; }
    public static float energy() { return energy; }
    public static float max() { return max; }

    private UmbraClientState(){}
}
