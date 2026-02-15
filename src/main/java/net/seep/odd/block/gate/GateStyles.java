// FILE: src/main/java/net/seep/odd/block/gate/GateStyles.java
package net.seep.odd.block.gate;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import org.joml.Vector3f;

import java.util.*;

public final class GateStyles {
    private GateStyles(){}

    public static final Identifier ROTTEN   = new Identifier(Oddities.MOD_ID, "rotten_roots_gate");
    public static final Identifier ATHENEUM = new Identifier(Oddities.MOD_ID, "atheneum_gate");

    // LinkedHashMap = stable ordering for /odd gate list
    private static final Map<Identifier, GateStyle> REG = new LinkedHashMap<>();

    static {
        register(new GateStyle(
                ROTTEN,
                new Identifier(Oddities.MOD_ID, "geo/dimensional_gate_rotten.geo.json"),
                new Identifier(Oddities.MOD_ID, "textures/block/dimensional_gate_rotten.png"),
                new Identifier(Oddities.MOD_ID, "animations/dimensional_gate_rotten.animation.json"),
                new Identifier(Oddities.MOD_ID, "shaders/post/gate_portal.json"),
                new Vector3f(0.10f, 0.90f, 0.25f),
                new Identifier(Oddities.MOD_ID, "rotten_roots")
        ));

        register(new GateStyle(
                ATHENEUM,
                new Identifier(Oddities.MOD_ID, "geo/dimensional_gate_atheneum.geo.json"),
                new Identifier(Oddities.MOD_ID, "textures/block/dimensional_gate_atheneum.png"),
                new Identifier(Oddities.MOD_ID, "animations/dimensional_gate_atheneum.animation.json"),
                new Identifier(Oddities.MOD_ID, "shaders/post/gate_portal_atheneum.json"),
                new Vector3f(0.45f, 0.72f, 1.00f),
                new Identifier(Oddities.MOD_ID, "atheneum")
        ));
    }

    public static void register(GateStyle style) {
        REG.put(style.id(), style);
    }

    /** Never null: falls back to ROTTEN */
    public static GateStyle get(Identifier id) {
        GateStyle s = REG.get(id);
        return (s != null) ? s : REG.get(ROTTEN);
    }
    public static boolean exists(Identifier id) {
        return REG.containsKey(id);
    }


    /** Strict lookup: returns null if missing (useful for validation) */
    public static GateStyle getOrNull(Identifier id) {
        return REG.get(id);
    }


    /** Used by your command: `for (GateStyle s : GateStyles.all())` */
    public static Collection<GateStyle> all() {
        return Collections.unmodifiableCollection(REG.values());
    }

    public static Collection<Identifier> ids() {
        return Collections.unmodifiableSet(REG.keySet());
    }
}
