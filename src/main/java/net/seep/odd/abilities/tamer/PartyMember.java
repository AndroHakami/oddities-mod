// net/seep/odd/abilities/tamer/PartyMember.java
package net.seep.odd.abilities.tamer;

import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

public final class PartyMember {
    public Identifier entityTypeId;
    public String nickname;
    public int level;      // 1..99
    public int exp;        // total exp
    public String[] moves; // move ids from TamerMoves

    public static final int MAX_LEVEL = 99;

    public PartyMember(Identifier typeId, String nickname, int level, int exp, String[] moves) {
        this.entityTypeId = typeId;
        this.nickname = nickname;
        this.level = level;
        this.exp = exp;
        this.moves = moves;
    }

    public static PartyMember fromCapture(Identifier typeId, LivingEntity source) {
        // Use current custom name if present; otherwise derive a simple display from the raw id
        String baseName = typeId.getPath().replace('_', ' ');
        String display = source.hasCustomName() ? source.getCustomName().getString() : capitalize(baseName);
        int startLvl = 5; // starter level
        String[] starter = TamerMoves.starterMovesFor(typeId);
        return new PartyMember(typeId, display, startLvl, 0, starter);
    }

    public String displayName() {
        return (nickname != null && !nickname.isEmpty())
                ? nickname
                : capitalize(entityTypeId.getPath().replace('_', ' '));
    }

    public void gainExp(int amount) {
        if (level >= MAX_LEVEL) return;
        exp = Math.max(0, exp + amount);
        while (level < MAX_LEVEL && exp >= TamerXp.totalExpForLevel(level + 1)) {
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
        return new PartyMember(type, nick, lv, xp, mv);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
