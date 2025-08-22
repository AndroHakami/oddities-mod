package net.seep.odd.abilities.tamer;

import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Minimal move registry (IDs + display names for now). */
public final class TamerMoves {
    private TamerMoves() {}

    /** moveId -> display name (and later: power, cooldown, effect, etc.) */
    public static final Map<String, String> MOVES = new HashMap<>();

    /** entityTypeId (Identifier) -> starter moves (2–3). */
    private static final Map<Identifier, String[]> STARTERS = new HashMap<>();

    // ---------- helpers (no registry access here) ----------
    private static void define(String id, String display) { MOVES.put(id, display); }

    private static Identifier id(String s) {
        // allow "minecraft:zombie" or just "zombie"
        int c = s.indexOf(':');
        return (c >= 0) ? new Identifier(s) : new Identifier("minecraft", s);
    }

    private static void mapStarters(String entityId, String... moveIds) {
        STARTERS.put(id(entityId), moveIds);
    }

    // ---------- static bootstrap (SAFE: only Identifiers & Strings) ----------
    static {
        // moves
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

        // vanilla starters — ONLY identifiers here, no registry access
        mapStarters("minecraft:zombie",      "tackle", "rot_grasp", "infect_bite");
        mapStarters("minecraft:skeleton",    "bone_arrow", "power_shot");
        mapStarters("minecraft:spider",      "venom_sting", "web_shot", "quick_dash");
        mapStarters("minecraft:creeper",     "spark_pop", "tackle");
        mapStarters("minecraft:enderman",    "quick_dash", "howl");

        // you can add your own with full ids, e.g.:
        // mapStarters("odd:villager_evo", "tackle");
    }

    /** Returns a copy so callers can modify their array safely. */
    public static String[] starterMovesFor(Identifier typeId) {
        String[] arr = STARTERS.get(typeId);
        if (arr != null && arr.length > 0) return arr.clone();
        return new String[] { "tackle", "quick_dash" }; // fallback
    }
}
