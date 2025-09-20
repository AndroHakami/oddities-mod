package net.seep.odd.sky;

import net.minecraft.util.math.Vec3d;

public final class SkyColorMath {
    private SkyColorMath() {}

    /** HSV shift on 0..1 rgb with sat/val multipliers. */
    public static Vec3d hsvShift(Vec3d rgb, float hueDeg, float satMul, float valMul) {
        float r = (float)rgb.x, g = (float)rgb.y, b = (float)rgb.z;

        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float v = max;
        float c = max - min;

        float hPrime;
        if (c < 1e-6f) {
            hPrime = 0f;
        } else if (max == r) {
            hPrime = ((g - b) / c) % 6f;
        } else if (max == g) {
            hPrime = ((b - r) / c) + 2f;
        } else {
            hPrime = ((r - g) / c) + 4f;
        }
        float h = (hPrime * 60f + hueDeg) % 360f;
        if (h < 0) h += 360f;

        float s = (v == 0f) ? 0f : (c / v);
        s = Math.max(0f, Math.min(1f, s * satMul));
        v = Math.max(0f, Math.min(1f, v * valMul));

        float C = v * s;
        float X = C * (1f - Math.abs(((h / 60f) % 2f) - 1f));
        float m = v - C;

        float rr=0, gg=0, bb=0;
        if      (h < 60)  { rr=C; gg=X; bb=0; }
        else if (h < 120) { rr=X; gg=C; bb=0; }
        else if (h < 180) { rr=0; gg=C; bb=X; }
        else if (h < 240) { rr=0; gg=X; bb=C; }
        else if (h < 300) { rr=X; gg=0; bb=C; }
        else              { rr=C; gg=0; bb=X; }

        return new Vec3d(rr + m, gg + m, bb + m);
    }
}
