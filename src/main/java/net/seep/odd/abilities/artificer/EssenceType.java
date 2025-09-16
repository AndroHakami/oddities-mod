package net.seep.odd.abilities.artificer;

import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.annotation.Nullable;
import net.minecraft.fluid.Fluid;
import net.seep.odd.abilities.artificer.fluid.ArtificerFluids;


// net.seep.odd.abilities.artificer.EssenceType
public enum EssenceType {
    LIGHT("light", 0xFFFFFFFF),
    GAIA("gaia", 0xFF46C046),
    HOT("hot", 0xFFFF8C2E),
    COLD("cold", 0xFF7FDBFF),
    DEATH("death", 0xFF1A001F),
    LIFE("life", 0xFFFFE066);

    public final String key;
    public final int argb;

    EssenceType(String key, int argb) {
        this.key = key;
        this.argb = argb;
    }

    /**
     * Return the registered Fluid for this essence.
     */
    public Fluid getFluid() {
        // assumes you keep a map from EssenceType -> Fluid in ArtificerFluids
        return ArtificerFluids.ESSENCE_FLUIDS.get(this);
    }

    /**
     * Reverse-lookup: which EssenceType does this Fluid belong to?
     */
    public static @Nullable EssenceType fromFluid(Fluid f) {
        for (EssenceType e : values()) {
            if (ArtificerFluids.ESSENCE_FLUIDS.get(e) == f) return e;
        }
        return null;
    }

    /**
     * Parse a network/JSON key back into an EssenceType.
     */
    public static @Nullable EssenceType byKey(String key) {
        for (EssenceType e : values()) {
            if (e.key.equals(key)) return e;
        }
        return null;
    }

    /**
     * Tiny helpers for packets/NBT (keep your signatures)
     */
    public static EssenceType readKey(net.minecraft.network.PacketByteBuf buf) {
        return byKey(buf.readString(32));
    }

    public void writeKey(net.minecraft.network.PacketByteBuf buf) {
        buf.writeString(this.key);
    }
}
