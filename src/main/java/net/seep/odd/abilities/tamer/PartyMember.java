// net/seep/odd/abilities/tamer/PartyMember.java
package net.seep.odd.abilities.tamer;

import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Single captured creature in a party. */
public final class PartyMember {
    public Identifier entityTypeId;
    public String nickname;      // editable

    /** Level & XP (XP is per-level, resets on level-up). */
    public int level;            // 1..99
    public int exp;              // progress within current level

    public String[] moves;       // move ids from TamerMoves

    /** Cute icon to render in HUD/party/wheel. You provide the texture. */
    public Identifier icon;      // e.g. odd:textures/gui/tamer/icons/villager.png

    /** Persisted health so status/party shows accurate numbers and deaths persist. */
    public float hp   = -1f;     // -1 = unknown (use full on first summon)
    public float maxh = -1f;     // cached max HP for UI

    /** Species baselines captured once so level scaling doesn’t stack. */
    public float  baseMaxH = -1f;
    public double baseAtk  = -1.0;
    public double baseSpd  = -1.0;
    public double baseDef  = -1.0; // NEW: armor/defense baseline

    public static final int MAX_LEVEL = 99;

    public PartyMember(Identifier typeId, String nickname, int level, int exp, String[] moves, Identifier icon) {
        this.entityTypeId = typeId;
        this.nickname = nickname;
        this.level = Math.max(1, Math.min(MAX_LEVEL, level));
        this.exp = Math.max(0, exp);
        this.moves = moves;
        this.icon = icon != null ? icon : defaultIconFor(typeId);
    }

    public static PartyMember fromCapture(Identifier typeId, LivingEntity source) {
        String baseName = Registries.ENTITY_TYPE.get(typeId).getName().getString();
        String display = source.hasCustomName() ? source.getCustomName().getString() : capitalize(baseName);
        int startLvl = 5; // starter level; tune later
        String[] starter = TamerMoves.starterMovesFor(typeId);
        Identifier icon = defaultIconFor(typeId);
        return new PartyMember(typeId, display, startLvl, 0, starter, icon);
    }

    public String displayName() {
        return (nickname != null && !nickname.isEmpty())
                ? nickname
                : Registries.ENTITY_TYPE.get(entityTypeId).getName().getString();
    }

    /** Adds per-level XP and applies level-ups using TamerLeveling.nextFor(level). */
    public void gainExp(int amount) {
        if (level >= MAX_LEVEL) return;
        exp = Math.max(0, exp + Math.max(0, amount));
        while (level < MAX_LEVEL) {
            int cap = TamerLeveling.nextFor(level);
            if (exp < cap) break;
            exp -= cap;
            level++;
        }
    }

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putString("type", entityTypeId.toString());
        n.putString("nick", nickname == null ? "" : nickname);
        n.putInt("lv", level);
        n.putInt("xp", exp);
        n.putInt("mc", moves.length);
        for (int i = 0; i < moves.length; i++) n.putString("m" + i, moves[i]);
        if (icon != null) n.putString("icon", icon.toString());

        n.putFloat("hp",   hp);
        n.putFloat("maxh", maxh);

        n.putFloat("bmax", baseMaxH);
        n.putDouble("batk", baseAtk);
        n.putDouble("bspd", baseSpd);
        n.putDouble("bdef", baseDef); // NEW
        return n;
    }

    public static PartyMember fromNbt(NbtCompound n) {
        Identifier type = new Identifier(n.getString("type"));
        String nick = n.getString("nick");
        int lv = Math.max(1, Math.min(MAX_LEVEL, n.getInt("lv")));
        int xp = Math.max(0, n.getInt("xp"));
        int mc = Math.max(0, n.getInt("mc"));
        String[] mv = new String[mc];
        for (int i = 0; i < mc; i++) mv[i] = n.getString("m" + i);

        Identifier icon = n.contains("icon", 8)
                ? new Identifier(n.getString("icon"))
                : defaultIconFor(type);

        PartyMember pm = new PartyMember(type, nick, lv, xp, mv, icon);
        pm.hp      = n.contains("hp")   ? n.getFloat("hp")   : -1f;
        pm.maxh    = n.contains("maxh") ? n.getFloat("maxh") : -1f;
        pm.baseMaxH= n.contains("bmax") ? n.getFloat("bmax") : -1f;
        pm.baseAtk = n.contains("batk") ? n.getDouble("batk") : -1.0;
        pm.baseSpd = n.contains("bspd") ? n.getDouble("bspd") : -1.0;
        pm.baseDef = n.contains("bdef") ? n.getDouble("bdef") : -1.0; // NEW
        return pm;
    }

    private static Identifier defaultIconFor(Identifier typeId) {
        return new Identifier("odd", "textures/gui/tamer/icons/" + typeId.getPath() + ".png");
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
