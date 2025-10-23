package net.seep.odd.abilities.rat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleType;
import virtuoel.pehkui.api.ScaleTypes;

public final class PehkuiUtil {
    private PehkuiUtil() {}

    /** Preferred: explicit eye height control. */
    public static void applyScaleSafely(PlayerEntity p,
                                        float base, float motion, float jumpHeight, float stepHeight,
                                        float eyeHeight) {
        try {
            set(ScaleTypes.BASE,        p, base);
            set(ScaleTypes.MOTION,      p, motion);
            set(ScaleTypes.JUMP_HEIGHT, p, jumpHeight);
            set(ScaleTypes.STEP_HEIGHT, p, stepHeight);
            set(ScaleTypes.EYE_HEIGHT,  p, eyeHeight);

            // (Optional but helps apply instantly on some setups)
            p.calculateDimensions();
        } catch (Throwable ignored) {}
    }

    /** Legacy: eye height == base scale. */
    @Deprecated
    public static void applyScaleSafely(PlayerEntity p, float base, float motion, float jumpHeight, float stepHeight) {
        applyScaleSafely(p, base, motion, jumpHeight, stepHeight, base);
    }

    public static void resetScalesSafely(PlayerEntity p) {
        try {
            reset(ScaleTypes.BASE, p);
            reset(ScaleTypes.MOTION, p);
            reset(ScaleTypes.JUMP_HEIGHT, p);
            reset(ScaleTypes.STEP_HEIGHT, p);
            reset(ScaleTypes.EYE_HEIGHT, p);
            p.calculateDimensions();
        } catch (Throwable ignored) {}
    }

    private static void set(ScaleType type, Entity e, float value) {
        ScaleData d = type.getScaleData(e);
        d.setTargetScale(value);
        d.setScale(value);
        d.setScaleTickDelay(0);
    }

    private static void reset(ScaleType type, Entity e) {
        ScaleData d = type.getScaleData(e);
        d.setTargetScale(1.0f);
        d.setScale(1.0f);
        d.setScaleTickDelay(0);
    }
}
