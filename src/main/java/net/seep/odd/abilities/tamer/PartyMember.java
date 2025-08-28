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
    public int level;            // 1..99
    public int exp;              // total exp
    public String[] moves;       // move ids from TamerMoves
    /** Cute icon to render in HUD/party/wheel. You provide the texture. */
    public Identifier icon;      // e.g. odd:textures/gui/tamer/icons/villager.png

    public static final int MAX_LEVEL = 99;

    public PartyMember(Identifier typeId, String nickname, int level, int exp, String[] moves, Identifier icon) {
        this.entityTypeId = typeId;
        this.nickname = nickname;
        this.level = level;
        this.exp = exp;
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
        if (icon != null) n.putString("icon", icon.toString());
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

        return new PartyMember(type, nick, lv, xp, mv, icon);
    }

    private static Identifier defaultIconFor(Identifier typeId) {
        // Put your PNGs here: assets/odd/textures/gui/tamer/icons/<entity>.png
        return new Identifier("odd", "textures/gui/tamer/icons/" + typeId.getPath() + ".png");
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
