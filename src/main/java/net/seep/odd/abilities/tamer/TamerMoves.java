// net/seep/odd/abilities/tamer/TamerMoves.java
package net.seep.odd.abilities.tamer;

import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Minimal move registry (IDs + display names; no registry calls in static init). */
public final class TamerMoves {
    private TamerMoves() {}

    /** moveId -> display name */
    public static final Map<String, String> MOVES = new HashMap<>();

    /** entityTypeId (as string) -> starter moves (2-3) */
    private static final Map<String, String[]> STARTERS = new HashMap<>();

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

        // === Map common mobs to starters (by raw ID strings, no registry access here) ===
        mapId("minecraft:zombie",      "tackle", "rot_grasp", "infect_bite");
        mapId("minecraft:skeleton",    "bone_arrow", "power_shot");
        mapId("minecraft:spider",      "venom_sting", "web_shot", "quick_dash");
        mapId("minecraft:creeper",     "spark_pop", "tackle");
        mapId("minecraft:enderman",    "quick_dash", "howl");

        // You can add more like:
        // mapId("minecraft:villager", "tackle", "quick_dash");
    }

    private static void define(String id, String display) {
        MOVES.put(id, display);
    }

    private static void mapId(String entityTypeIdString, String... moveIds) {
        STARTERS.put(entityTypeIdString, moveIds);
    }

    /** Safe at runtime; returns a clone. Falls back to a generic set. */
    public static String[] starterMovesFor(Identifier typeId) {
        String[] arr = STARTERS.get(typeId.toString());
        if (arr != null && arr.length > 0) return arr.clone();
        return new String[] { "tackle", "quick_dash" };
    }
}
