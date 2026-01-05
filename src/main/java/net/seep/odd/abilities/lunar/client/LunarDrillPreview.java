package net.seep.odd.abilities.lunar.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class LunarDrillPreview {
    private LunarDrillPreview() {}

    private static final List<BlockPos> TARGETS = new ArrayList<>();
    private static volatile float CHARGE_PCT = 0f;

    /** If you previously called init(), it's safe to keep calling it. Mixin does the rendering. */
    public static void init() {}

    public static void setTargets(List<BlockPos> list) {
        synchronized (TARGETS) {
            TARGETS.clear();
            TARGETS.addAll(list);
        }
    }

    public static List<BlockPos> getTargets() {
        synchronized (TARGETS) {
            return List.copyOf(TARGETS);
        }
    }

    public static void setChargeProgress(float pct) {
        CHARGE_PCT = MathHelper.clamp(pct, 0f, 1f);
    }

    public static float getChargeProgress() {
        return CHARGE_PCT;
    }

    public static void clear() {
        synchronized (TARGETS) {
            TARGETS.clear();
        }
        CHARGE_PCT = 0f;
    }
}
