package net.seep.odd.abilities.client.hud;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;

@Environment(EnvType.CLIENT)
public final class AstralClientState {
    private static boolean active = false;
    private static GlobalPos anchor = null;
    private static long startGameTime = 0L;
    private static int maxTicks = 0;

    private AstralClientState() {}

    public static void start(GlobalPos anchorPos, int durationTicks, MinecraftClient mc) {
        active = true;
        anchor = anchorPos;
        maxTicks = durationTicks;
        if (mc != null && mc.world != null) {
            startGameTime = mc.world.getTime();
        } else {
            startGameTime = 0L;
        }
    }

    public static void stop() {
        active = false;
        anchor = null;
        startGameTime = 0L;
        maxTicks = 0;
    }

    public static boolean isActive() {
        return active;
    }

    public static long ticksLeft(MinecraftClient mc) {
        if (!active || mc == null || mc.world == null) return 0;
        long elapsed = mc.world.getTime() - startGameTime;
        long left = (long)maxTicks - elapsed;
        return Math.max(0, left);
    }

    public static double distanceToAnchor(MinecraftClient mc) {
        if (!active || mc == null || mc.player == null || mc.world == null || anchor == null) return Double.NaN;
        if (!mc.world.getRegistryKey().equals(anchor.getDimension())) return Double.NaN;
        BlockPos ap = anchor.getPos();
        return Math.sqrt(mc.player.squaredDistanceTo(ap.getX() + 0.5, ap.getY() + 0.5, ap.getZ() + 0.5));
    }
}
