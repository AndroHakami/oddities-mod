// FILE: src/main/java/net/seep/odd/abilities/sniper/client/SniperClientState.java
package net.seep.odd.abilities.sniper.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public final class SniperClientState {
    private SniperClientState() {}

    private static boolean inited = false;

    private static boolean targetScoped = false;

    private static float scope = 0f;      // 0..1
    private static float prevScope = 0f;  // for interpolation

    // 0.1s at 20 TPS => 2 ticks. Step per tick = 0.5.
    private static final float STEP_PER_TICK = 0.5f;

    public static void init() {
        if (inited) return;
        inited = true;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // keep ticking even if world/player is null, so transitions finish cleanly on menu -> world etc.
            prevScope = scope;

            float step = STEP_PER_TICK;
            if (targetScoped) {
                scope = MathHelper.clamp(scope + step, 0f, 1f);
            } else {
                scope = MathHelper.clamp(scope - step, 0f, 1f);
            }

            // Safety: if player vanished, drop scope
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) {
                targetScoped = false;
                scope = 0f;
                prevScope = 0f;
            }
        });
    }

    /** Set desired scoped state (hold RMB => true). */
    public static void setScopedTarget(boolean scoped) {
        targetScoped = scoped;
    }

    public static boolean isTargetScoped() {
        return targetScoped;
    }

    /** Smooth per-frame (uses tickDelta interpolation). */
    public static float scopeAmount(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevScope, scope);
    }

    /** “Fully scoped” threshold (used for hiding the gun model). */
    public static boolean isFullyScoped(float tickDelta) {
        return scopeAmount(tickDelta) >= 0.95f;
    }
}
