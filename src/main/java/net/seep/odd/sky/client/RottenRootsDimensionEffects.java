// src/main/java/net/seep/odd/client/RottenRootsDimensionEffects.java
package net.seep.odd.sky.client;

import net.minecraft.client.render.DimensionEffects;
import net.minecraft.util.math.Vec3d;

public final class RottenRootsDimensionEffects extends DimensionEffects {

    // murky gray-green
    private static final float R = 0.46f;
    private static final float G = 0.52f;
    private static final float B = 0.49f;

    private static final Vec3d FOG_VEC = new Vec3d(R, G, B);
    private static final float[] FOG_RGBA = new float[]{R, G, B, 1.0f};

    public RottenRootsDimensionEffects() {
        // cloudsHeight NaN disables vanilla clouds for this effects profile
        super(Float.NaN, false, SkyType.NONE, false, false);
    }

    @Override
    public Vec3d adjustFogColor(Vec3d color, float sunHeight) {
        // constant fog color (no day/night tint)
        return FOG_VEC;
    }

    @Override
    public float[] getFogColorOverride(float skyAngle, float tickDelta) {
        // also prevents sunrise/sunset fog tint in overworld-like types
        return FOG_RGBA;
    }

    @Override
    public boolean useThickFog(int camX, int camY) {
        // make it feel swampy/murky
        return true;
    }
}
