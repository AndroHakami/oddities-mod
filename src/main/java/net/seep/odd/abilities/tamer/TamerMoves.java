
package net.seep.odd.abilities.tamer;

import net.minecraft.util.Identifier;
import java.util.HashMap;
import java.util.Map;

/** Minimal move registry (IDs + display names for now). */
public final class TamerMoves {
    private TamerMoves() {}

    /** moveId -> display name (and later: power, cooldown, effect, etc.) */
    public static final Map<String, String> MOVES = new HashMap<>();

    /** entityTypeId -> starter moves (2-3) */
    private static final Map<Identifier, String[]> STARTERS = new HashMap<>();

    private static Identifier mc(String path) { return new Identifier("minecraft", path); }
    @SuppressWarnings("unused")
    private static Identifier odd(String path) { return new Identifier("odd", path); }

    static {
        // === Define some generic moves ===
        define("tackle",        "Tackle");
        define("quick_dash",    "Quick Dash");
        define("howl",          "Howl");
        define("venom_sting",   "Venom Sting");
        define("web_shot",      "Web Shot");
        define("bone_arrow",    "Bone Arrow");
        define("power_shot",    "Power Shot");
        define("rot_grasp",     "Rotting Grasp");
        define("infect_bite",   "Infected Bite");
        define("spark_pop",     "Spark Pop");
        define("emerald_shuriken", "Emerald Shuriken");

        // === Map common mobs to starters (NO registry calls here) ===
        mapStarters(mc("zombie"),    "tackle", "rot_grasp", "infect_bite");
        mapStarters(mc("skeleton"),  "bone_arrow", "power_shot");
        mapStarters(mc("spider"),    "venom_sting", "web_shot", "quick_dash");
        mapStarters(mc("creeper"),   "spark_pop", "tackle");
        mapStarters(mc("enderman"),  "quick_dash", "howl");
        mapStarters(mc("villager"),  "emerald_shuriken", "quick_dash"); // your ninja villager
        // add more vanilla/mod ids with mapStarters(new Identifier(modid, path), ...)
    }

    private static void define(String id, String display) {
        MOVES.put(id, display);
    }

    private static void mapStarters(Identifier typeId, String... moveIds) {
        STARTERS.put(typeId, moveIds);
    }

    /** Lookup using the entity type’s Identifier (safe at runtime). */
    public static String[] starterMovesFor(Identifier typeId) {
        String[] arr = STARTERS.get(typeId);
        if (arr != null && arr.length > 0) return arr.clone();
        // generic fallback
        return new String[] { "tackle", "quick_dash" };
    }
}
