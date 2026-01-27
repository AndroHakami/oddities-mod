// src/main/java/net/seep/odd/abilities/fairy/FairySpell.java
package net.seep.odd.abilities.fairy;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum FairySpell {
    NONE,

    // === Existing ===
    AURA_LEVITATION,         // U U U
    AURA_HEAVY,              // U U D
    AREA_MINE,               // U D D   (now: HASTE aura)
    STONE_PRISON,            // D D D   (one-shot)
    BUBBLE,                  // U U R
    AURA_REGEN,              // U R R
    CROP_GROWTH,             // R R R
    BLACKHOLE,               // D L R
    RECHARGE,                // U D U

    // === NEW ===
    STORM,                   // one-shot
    MAGIC_BULLETS,           // aura
    WATER_BREATHING,         // aura
    TINY_WORLD,              // aura
    LIFT_OFF,                // one-shot
    BANISH,                  // one-shot
    EXTINGUISH,              // one-shot
    RETURN_POINT,            // one-shot
    SWITCHERANO,             // aura
    WEAKNESS;                // aura

    // 0=UP,1=DOWN,2=LEFT,3=RIGHT
    public static final byte UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3;

    // ========= Combo Registry (easy to extend) =========
    private static final Map<Integer, FairySpell> COMBOS = new HashMap<>();

    static {
        // Existing
        reg(UP,   UP,   UP,   AURA_LEVITATION);
        reg(UP,   UP,   DOWN, AURA_HEAVY);
        reg(UP,   DOWN, DOWN, AREA_MINE);
        reg(DOWN, DOWN, DOWN, STONE_PRISON);
        reg(UP,   UP,   RIGHT, BUBBLE);
        reg(UP,   RIGHT, RIGHT, AURA_REGEN);
        reg(RIGHT, RIGHT, RIGHT, CROP_GROWTH);
        reg(DOWN, LEFT, RIGHT, BLACKHOLE);
        reg(UP,   DOWN, UP,   RECHARGE);

        // New (you can change these freely)
        reg(LEFT, LEFT, LEFT, STORM);
        reg(RIGHT, RIGHT, LEFT, MAGIC_BULLETS);
        reg(RIGHT, RIGHT, UP, WATER_BREATHING);
        reg(LEFT, UP, RIGHT, TINY_WORLD);
        reg(UP, LEFT, LEFT, LIFT_OFF);
        reg(DOWN, LEFT, UP, BANISH);
        reg(DOWN, RIGHT, DOWN, EXTINGUISH);
        reg(LEFT, DOWN, RIGHT, RETURN_POINT);
        reg(LEFT, RIGHT, LEFT, SWITCHERANO);
        reg(DOWN, DOWN, LEFT, WEAKNESS);
    }

    private static void reg(int a, int b, int c, FairySpell s) {
        COMBOS.put(key(a, b, c), s);
    }

    private static int key(int a, int b, int c) {
        return ((a & 3) << 4) | ((b & 3) << 2) | (c & 3);
    }

    /** Order matters. Unknown combo => NONE (does nothing). */
    public static FairySpell fromInputs(byte a, byte b, byte c) {
        return COMBOS.getOrDefault(key(a, b, c), NONE);
    }

    public boolean isAura() {
        return switch (this) {
            case AURA_LEVITATION, AURA_HEAVY, AURA_REGEN, CROP_GROWTH, BLACKHOLE,
                 MAGIC_BULLETS, WATER_BREATHING, TINY_WORLD, SWITCHERANO, WEAKNESS,
                 BUBBLE, AREA_MINE -> true;
            default -> false;
        };
    }

    public boolean isOneShot() {
        return switch (this) {
            case STONE_PRISON, STORM, LIFT_OFF, BANISH, EXTINGUISH, RETURN_POINT -> true;
            default -> false;
        };
    }

    public String textureKey() {
        return switch (this) {
            case NONE -> "none";
            case AURA_LEVITATION -> "levitate";
            case AURA_HEAVY -> "heavy";
            case AREA_MINE -> "mine";
            case STONE_PRISON -> "stone";
            case BUBBLE -> "bubble";
            case AURA_REGEN -> "regen";
            case CROP_GROWTH -> "growth";
            case BLACKHOLE -> "blackhole";
            case RECHARGE -> "recharge";

            case STORM -> "storm";
            case MAGIC_BULLETS -> "bullets";
            case WATER_BREATHING -> "water";
            case TINY_WORLD -> "tiny";
            case LIFT_OFF -> "liftoff";
            case BANISH -> "banish";
            case EXTINGUISH -> "extinguish";
            case RETURN_POINT -> "return";
            case SWITCHERANO -> "switcherano";
            case WEAKNESS -> "weakness";
        };
    }

    public static FairySpell fromTextureKey(String key) {
        if (key == null || key.isBlank()) return NONE;
        String k = key.toLowerCase(Locale.ROOT);

        for (FairySpell s : values()) {
            if (s.textureKey().equals(k)) return s;
        }
        try {
            return FairySpell.valueOf(key.toUpperCase(Locale.ROOT));
        } catch (Throwable ignored) {
            return NONE;
        }
    }

    /** RGB 0xRRGGBB */
    public int colorRgb() {
        return switch (this) {
            case NONE -> 0xAAAAAA;

            case AURA_LEVITATION -> 0xB8A8FF;
            case AURA_HEAVY -> 0x6C6C6C;
            case AREA_MINE -> 0xFFB14A;      // mine/orange
            case STONE_PRISON -> 0x9AA3AD;
            case BUBBLE -> 0x63D6FF;
            case AURA_REGEN -> 0x63FF9C;
            case CROP_GROWTH -> 0x4BFF4B;
            case BLACKHOLE -> 0x7A3DFF;
            case RECHARGE -> 0xFFF06A;

            case STORM -> 0x1E3A8A;          // dark blue
            case MAGIC_BULLETS -> 0xFF2A2A;  // red
            case WATER_BREATHING -> 0x39D5FF;// aqua
            case TINY_WORLD -> 0xFF8A2A;     // orange
            case LIFT_OFF -> 0x8CFF7A;       // light green
            case BANISH -> 0xA855F7;         // purple
            case EXTINGUISH -> 0xFFFFFF;     // white
            case RETURN_POINT -> 0x14532D;   // dark green
            case SWITCHERANO -> 0xFFE873;    // yellow
            case WEAKNESS -> 0x7C4A1E;       // brown
        };
    }
}
