package net.seep.odd.abilities.artificer.mixer;

// imports you need:
import net.minecraft.util.StringIdentifiable;

public enum AxisPos implements StringIdentifiable {
    NEG(-1, "neg"),
    ZERO(0, "zero"),
    POS(1, "pos");

    public final int o;
    private final String name;

    AxisPos(int o, String name) {
        this.o = o;
        this.name = name;
    }

    public static AxisPos of(int d) {
        return d < 0 ? NEG : (d > 0 ? POS : ZERO);
    }

    @Override
    public String asString() {  // Yarn 1.20.1 name
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
