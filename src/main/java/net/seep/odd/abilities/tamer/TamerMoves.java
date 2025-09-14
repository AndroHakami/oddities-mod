package net.seep.odd.abilities.tamer;

import net.minecraft.util.Identifier;

import java.util.*;

/** Tiny registry for moves + per-species starters & learnsets. */
public final class TamerMoves {
    private TamerMoves() {}

    private static final Map<String, TamerMove> MOVES = new HashMap<>();
    private static final Map<String, String>    NAMES = new HashMap<>();
    private static final Map<Identifier, List<String>> STARTERS = new HashMap<>();
    private static final Map<Identifier, Map<Integer, String>> LEARNSETS = new HashMap<>();

    public static void bootstrap() {
        // --- base/sample moves ---
        register(new TamerMove("odd:bite",       "Bite",        "A quick chomp.",             TamerMove.Category.PHYSICAL, 40));
        register(new TamerMove("odd:peck",       "Peck",        "A fast beak jab.",           TamerMove.Category.PHYSICAL, 35));
        register(new TamerMove("odd:wind_gust",  "Wind Gust",   "Kick up a gust.",            TamerMove.Category.SPECIAL,  35));
        register(new TamerMove("odd:heal_pulse", "Heal Pulse",  "Restore some HP.",           TamerMove.Category.STATUS,    0));
        register(new TamerMove("odd:taunt",      "Taunt",       "Draw aggro to the user.",    TamerMove.Category.STATUS,    0));

        // --- SHURIKEN (ranged) ---
        register(new TamerMove("odd:shuriken",   "Shuriken",    "Throw a sharp star.",        TamerMove.Category.SPECIAL,  30));

        // ENDER FALL
        register(new TamerMove("odd:ender_fall", "Ender Fall",
                "Teleport the target 30 blocks upward if the sky above is clear.",
                TamerMove.Category.SPECIAL, 0));

        // ---- defaults for any species without an explicit entry ----
        setStartersFor(defaultId(), "odd:bite", "odd:wind_gust");
        addLearn(defaultId(), 7,  "odd:taunt");
        addLearn(defaultId(), 12, "odd:heal_pulse");

        // ---- examples per species (adjust to taste) ----
        setStartersFor(new Identifier("minecraft","chicken"), "odd:peck");
        addLearn(new Identifier("minecraft","chicken"), 8, "odd:wind_gust");

        setStartersFor(new Identifier("minecraft","zombie"), "odd:bite", "odd:taunt");
        addLearn(new Identifier("minecraft","zombie"), 15, "odd:heal_pulse");

        // Villager evo uses shuriken by default; base villager learns it on evolution/level-up if desired
        setStartersFor(new Identifier("odd","villager_evo"), "odd:shuriken");
        setStartersFor(new Identifier("minecraft","villager"), "odd:shuriken");
        addLearn(new Identifier("minecraft","villager"), 6, "odd:shuriken");
        setStartersFor(new Identifier("minecraft","enderman"), "odd:ender_fall");
    }

    /* ---------- public API ---------- */

    public static void register(TamerMove move) {
        MOVES.put(move.id(), move);
        NAMES.put(move.id(), move.name());
    }

    public static void setStartersFor(Identifier type, String... ids) {
        STARTERS.put(type, Arrays.asList(ids));
    }

    public static void addLearn(Identifier type, int level, String id) {
        LEARNSETS.computeIfAbsent(type, k -> new HashMap<>()).put(level, id);
    }

    public static String[] starterMovesFor(Identifier typeId) {
        List<String> l = STARTERS.getOrDefault(typeId, STARTERS.get(defaultId()));
        return l == null ? new String[0] : l.toArray(new String[0]);
    }

    public static String learnMoveAtLevel(Identifier typeId, int level) {
        Map<Integer, String> map = LEARNSETS.getOrDefault(typeId, LEARNSETS.get(defaultId()));
        return (map == null) ? null : map.get(level);
    }

    /** True if PM already knows this move id. */
    public static boolean knows(PartyMember pm, String id) {
        if (pm == null || pm.moves == null) return false;
        for (String s : pm.moves) if (id.equals(s)) return true;
        return false;
    }

    public static boolean appendOrReplaceMove(PartyMember pm, String moveId) {
        if (pm.moves == null) { pm.moves = new String[] { moveId }; return true; }
        for (String s : pm.moves) if (s.equals(moveId)) return true; // already knows it
        if (pm.moves.length < 4) {
            String[] nx = Arrays.copyOf(pm.moves, pm.moves.length + 1);
            nx[nx.length - 1] = moveId;
            pm.moves = nx;
            return true;
        } else {
            pm.moves[0] = moveId; // replace oldest
            return false;
        }
    }

    public static String nameOf(String id) {
        return NAMES.getOrDefault(id, id);
    }

    private static Identifier defaultId() { return new Identifier("odd", "default"); }
}
