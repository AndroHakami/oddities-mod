package net.seep.odd.sky.day;

import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public record BiomeDayProfile(int skyColor, int fogColor, int horizonColor, int cloudColor) {
    public Vec3d skyVec() {
        return rgbToVec(skyColor);
    }

    public Vec3d fogVec() {
        return rgbToVec(fogColor);
    }

    public Vec3d horizonVec() {
        return rgbToVec(horizonColor);
    }

    public Vec3d cloudVec() {
        return rgbToVec(cloudColor);
    }

    public static int parseHex(String raw) {
        String s = raw.trim().replace("#", "").replace("0x", "").replace("0X", "");
        if (s.length() != 6) {
            throw new IllegalArgumentException("Color must be 6 hex digits.");
        }
        return Integer.parseInt(s, 16) & 0xFFFFFF;
    }

    public static String toHex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    private static Vec3d rgbToVec(int rgb) {
        return new Vec3d(
                ((rgb >> 16) & 255) / 255.0,
                ((rgb >> 8) & 255) / 255.0,
                (rgb & 255) / 255.0
        );
    }

    public Text asText() {
        return Text.literal(
                "sky=" + toHex(skyColor) +
                        ", fog=" + toHex(fogColor) +
                        ", horizon=" + toHex(horizonColor) +
                        ", cloud=" + toHex(cloudColor)
        );
    }
}