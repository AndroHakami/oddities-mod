package net.seep.odd.sky;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Client-only state for celestial events (textures + sky hue). */
public final class CelestialEventClient {
    private CelestialEventClient() {}

    // ---- textures ----
    private static Identifier SUN_TEX  = null;
    private static Identifier MOON_TEX = null;

    public static Identifier getSunTextureOr(Identifier fallback)  { return SUN_TEX  != null ? SUN_TEX  : fallback; }
    public static Identifier getMoonTextureOr(Identifier fallback) { return MOON_TEX != null ? MOON_TEX : fallback; }

    // ---- sun / moon size scales (1 = vanilla 30/20) ----
    private static float SUN_SCALE  = 1.0f;
    private static float MOON_SCALE = 1.0f;
    public static float sunScale()  { return SUN_SCALE; }
    public static float moonScale() { return MOON_SCALE; }

    // ---- sky hue controls ----
    private static boolean HUE_ACTIVE = false;
    private static float HUE_DEG = 0f;     // shift in degrees
    private static float SAT    = 1f;      // saturation multiplier
    private static float VAL    = 1f;      // value/brightness multiplier
    /** small lift so the night “black” picks up some color */
    private static float NIGHT_LIFT = 0f;

    public static boolean isHueActive() { return HUE_ACTIVE; }
    public static Vec3d applySkyHue(Vec3d rgb) {
        if (!HUE_ACTIVE) return rgb;
        // ensure nights aren’t pure black so hue is visible
        double x = Math.max(rgb.x, NIGHT_LIFT);
        double y = Math.max(rgb.y, NIGHT_LIFT);
        double z = Math.max(rgb.z, NIGHT_LIFT);
        return SkyColorMath.hsvShift(new Vec3d(x, y, z), HUE_DEG, SAT, VAL);
    }

    // ---- clouds ----
    private static boolean HIDE_CLOUDS = false;
    public static boolean hideClouds() { return HIDE_CLOUDS; }

    // ---- timing ----
    private static int ticksLeft = 0;

    /** Advance timer every client tick (call from your Client init tick event if you want auto-expiry). */
    public static void clientTick() {
        if (ticksLeft > 0 && --ticksLeft == 0) clear();
    }

    // ---- mutate from packets/commands ----
    public static void apply(Identifier sun, Identifier moon,
                             float hueDeg, float sat, float val, float nightLift,
                             float sunScale, float moonScale,
                             boolean hideClouds, int durationTicks) {
        SUN_TEX   = sun;
        MOON_TEX  = moon;
        HUE_ACTIVE = true;
        HUE_DEG = hueDeg; SAT = sat; VAL = val; NIGHT_LIFT = nightLift;
        SUN_SCALE = Math.max(0.1f, sunScale);
        MOON_SCALE = Math.max(0.1f, moonScale);
        HIDE_CLOUDS = hideClouds;
        ticksLeft = Math.max(0, durationTicks);
    }

    public static void clear() {
        SUN_TEX = MOON_TEX = null;
        HUE_ACTIVE = false; HUE_DEG = 0f; SAT = 1f; VAL = 1f; NIGHT_LIFT = 0f;
        SUN_SCALE = 1f; MOON_SCALE = 1f;
        HIDE_CLOUDS = false;
        ticksLeft = 0;
    }
}
