package net.seep.odd.abilities.artificer;

import org.joml.Vector3f;

public final class EssenceColors {
    private EssenceColors() {}
    public static Vector3f start(net.seep.odd.abilities.artificer.EssenceType t) {
        return switch (t) {
            case LIGHT -> new Vector3f(1.00f, 1.00f, 1.00f); // white
            case GAIA  -> new Vector3f(0.30f, 0.95f, 0.40f); // green
            case HOT   -> new Vector3f(1.00f, 0.50f, 0.10f); // orange
            case COLD  -> new Vector3f(0.40f, 0.80f, 1.00f); // ice blue
            case DEATH -> new Vector3f(0.25f, 0.00f, 0.35f); // deep purple
            case LIFE  -> new Vector3f(1.00f, 0.90f, 0.20f); // yellow
        };
    }
    // a gentle pull toward a desaturated “beam white”
    public static Vector3f end() {
        return new Vector3f(0.92f, 0.95f, 1.00f);
    }
}
