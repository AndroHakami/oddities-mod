package net.seep.odd.abilities.artificer;

import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.annotation.Nullable;
import net.minecraft.fluid.Fluid;

// net.seep.odd.abilities.artificer.EssenceType
public enum EssenceType {
    LIGHT("light", 0xFFFFFFFF),
    GAIA ("gaia",  0xFF46C046),
    HOT  ("hot",   0xFFFF8C2E),
    COLD ("cold",  0xFF7FDBFF),
    DEATH("death", 0xFF1A001F),
    LIFE ("life",  0xFFFFE066);

    public final String key;
    public final int argb;
    EssenceType(String key, int argb) { this.key = key; this.argb = argb; }

    public static EssenceType byKey(String k) {
        for (var e : values()) if (e.key.equals(k)) return e;
        return null;
    }
    public Fluid getFluid() { return OddFluids.ESSENCE_FLUIDS.get(this); }
    public static @Nullable EssenceType fromFluid(Fluid f) {
        for (var e : values()) if (OddFluids.ESSENCE_FLUIDS.get(e) == f) return e;
        return null;
    }

    // --- optional: tiny helpers for packets/NBT ---
    public static EssenceType readKey(net.minecraft.network.PacketByteBuf buf) {
        return byKey(buf.readString(32));
    }
    public void writeKey(net.minecraft.network.PacketByteBuf buf) {
        buf.writeString(this.key);
    }
}
