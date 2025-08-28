package net.seep.odd.abilities.tamer.client;

import net.minecraft.util.Identifier;

public final class TamerHudState {
    private TamerHudState() {}

    public static String name = "";
    public static Identifier icon = null;   // 16x16 PNG (nullable)
    public static float hp = 0, maxHp = 0;
    public static int level = 0, exp = 0, next = 0; // total exp + remaining to next
    public static long lastUpdate = 0L;

    public static void update(String n, Identifier i, float h, float mh, int lv, int xp, int nx) {
        name = n;
        icon = i;
        hp = h;
        maxHp = mh;
        level = lv;
        exp = xp;
        next = nx;
        lastUpdate = System.currentTimeMillis();
    }
}