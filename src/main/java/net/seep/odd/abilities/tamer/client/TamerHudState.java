package net.seep.odd.abilities.tamer.client;

import net.minecraft.util.Identifier;

public final class TamerHudState {
    private TamerHudState() {}

    public static String name = "";
    public static Identifier icon = new Identifier("odd", "textures/gui/tamer/icons/default.png");
    public static float hp = 0, maxHp = 0;
    public static int level = 1, exp = 0, next = 1;
    public static long lastUpdate = 0L;

    public static void update(String n, Identifier ic, float h, float mh, int lv, int xp, int nx) {
        name = n;
        icon = ic;
        hp = h;
        maxHp = mh;
        level = lv;
        exp = xp;
        next = Math.max(1, nx);
        lastUpdate = System.currentTimeMillis();
    }
}