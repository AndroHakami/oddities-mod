package net.seep.odd.entity.seal;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

import java.util.Random;

public enum SealVariant {
    GREY(0, "seal"),
    BROWN(1, "seal_pink"),
    WHITE(2, "seal_blue");

    public final int id;
    public final String textureName;

    SealVariant(int id, String textureName) {
        this.id = id;
        this.textureName = textureName;
    }

    public Identifier textureId() {
        return new Identifier(Oddities.MOD_ID, "textures/entity/seal/" + textureName + ".png");
    }

    public static SealVariant byId(int id) {
        for (var v : values()) if (v.id == id) return v;
        return GREY;
    }

    public static SealVariant random(Random r) {
        SealVariant[] vals = values();
        return vals[r.nextInt(vals.length)];
    }
}
