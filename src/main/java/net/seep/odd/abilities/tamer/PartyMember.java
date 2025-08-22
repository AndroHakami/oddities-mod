package net.seep.odd.abilities.tamer;

import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

/** Single captured creature in a party. */
public final class PartyMember {
    /** What to spawn (entity type id). */
    public Identifier entityTypeId;

    /** Human-friendly species name captured once (e.g., "Villager"). */
    public String speciesName;

    /** Editable nickname shown in UI and on the entity if set. */
    public String nickname;

    /** 1..99 */
    public int level;

    /** Total EXP accumulated (use TamerXp.totalExpForLevel to compute thresholds). */
    public int exp;

    /** Move ids from TamerMoves (2–3 typical). */
    public String[] moves;

    /** Optional evolution (target type id + required level). */
    public Identifier evolveToId;   // null if no evolution
    public int        evolveLevel;  // <=0 if none

    public static final int MAX_LEVEL = 99;

    public PartyMember(Identifier typeId,
                       String speciesName,
                       String nickname,
                       int level,
                       int exp,
                       String[] moves) {
        this.entityTypeId = typeId;
        this.speciesName  = speciesName == null ? "" : speciesName;
        this.nickname     = nickname == null ? "" : nickname;
        this.level        = Math.max(1, Math.min(MAX_LEVEL, level));
        this.exp          = Math.max(0, exp);
        this.moves        = (moves == null) ? new String[0] : moves.clone();
        this.evolveToId   = null;
        this.evolveLevel  = 0;
    }

    /** Build from a captured entity. No registry lookups required. */
    public static PartyMember fromCapture(Identifier typeId, LivingEntity source) {
        // Capture a friendly species label (server-safe)
        String species = safeCap(source.getType().getName().getString());
        String display = source.hasCustomName()
                ? source.getCustomName().getString()
                : species;

        int startLvl = 5; // tweak as desired
        String[] starter = TamerMoves.starterMovesFor(typeId);

        return new PartyMember(typeId, species, display, startLvl, 0, starter);
    }

    /** Preferred display name (nickname if set, otherwise species). */
    public String displayName() {
        return (nickname != null && !nickname.isEmpty()) ? nickname : speciesName;
    }

    /** Add EXP and perform level-ups as long as thresholds are crossed. */
    public void gainExp(int amount) {
        if (level >= MAX_LEVEL) return;
        exp = Math.max(0, exp + Math.max(0, amount));

        // Level until we fall below the next threshold
        while (level < MAX_LEVEL && exp >= TamerXp.totalExpForLevel(level + 1)) {
            level++;
        }
    }

    /** Whether this member is eligible to evolve right now. */
    public boolean canEvolveNow() {
        return evolveToId != null && evolveLevel > 0 && level >= evolveLevel;
    }

    /* --------------------- NBT --------------------- */

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putString("type", entityTypeId.toString());
        n.putString("species", speciesName == null ? "" : speciesName);
        n.putString("nick", nickname == null ? "" : nickname);
        n.putInt("lv", level);
        n.putInt("xp", exp);

        // moves
        n.putInt("mc", moves.length);
        for (int i = 0; i < moves.length; i++) n.putString("m" + i, moves[i]);

        // evolution (optional)
        if (evolveToId != null) n.putString("evoTo", evolveToId.toString());
        if (evolveLevel > 0)    n.putInt("evoLv", evolveLevel);

        return n;
    }

    public static PartyMember fromNbt(NbtCompound n) {
        Identifier type = new Identifier(n.getString("type"));
        String species  = n.contains("species") ? n.getString("species") : deriveSpeciesFromId(type);
        String nick     = n.getString("nick");
        int lv          = Math.max(1, Math.min(MAX_LEVEL, n.getInt("lv")));
        int xp          = Math.max(0, n.getInt("xp"));

        int mc = Math.max(0, n.getInt("mc"));
        String[] mv = new String[mc];
        for (int i = 0; i < mc; i++) mv[i] = n.getString("m" + i);

        PartyMember pm = new PartyMember(type, species, nick, lv, xp, mv);

        if (n.contains("evoTo")) pm.evolveToId = new Identifier(n.getString("evoTo"));
        pm.evolveLevel = n.getInt("evoLv"); // 0 or missing == none

        return pm;
    }

    /* --------------------- helpers --------------------- */

    private static String safeCap(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Fallback species label if older saves don't have it. */
    private static String deriveSpeciesFromId(Identifier id) {
        String path = id.getPath();
        if (path == null || path.isEmpty()) return "Mob";
        // rough humanization: villager_zombie -> "Villager zombie"
        String pretty = path.replace('_', ' ');
        return safeCap(pretty);
    }
}
