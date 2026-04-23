package net.seep.odd.abilities.accelerate.client;

import ladysnake.satin.api.event.ShaderEffectRenderCallback;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public final class AccelerateFx {
    private AccelerateFx() {}

    private static final Identifier POST = new Identifier("odd", "shaders/post/accelerate.json");

    private static boolean inited = false;
    private static ManagedShaderEffect effect;

    // speed overlay
    private static float speed = 0f;
    private static float speedTarget = 0f;

    // recall flash (screen)
    private static float flash = 0f;          // 0..1
    private static float flashTime = 999f;    // seconds since trigger

    public static void init() {
        if (inited) return;
        inited = true;

        effect = ShaderEffectManager.getInstance().manage(POST);

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            if (effect == null) return;

            float s = MathHelper.clamp(speed, 0f, 1f);
            float f = MathHelper.clamp(flash, 0f, 1f);

            // if totally off, skip (saves perf)
            if (s <= 0.001f && f <= 0.001f) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return;

            int fbW = mc.getWindow().getFramebufferWidth();
            int fbH = mc.getWindow().getFramebufferHeight();

            float timeSec = 0f;
            if (mc.world != null) timeSec = (mc.world.getTime() + tickDelta) / 20f;

            effect.setUniformValue("OutSize", (float)fbW, (float)fbH);
            effect.setUniformValue("Time", timeSec);

            effect.setUniformValue("SpeedIntensity", s);
            effect.setUniformValue("FlashIntensity", f);
            effect.setUniformValue("FlashTime", flashTime);

            effect.render(tickDelta);
        });
    }

    /** Call every client tick. */
    public static void clientTick() {
        // smooth speed
        speed = MathHelper.lerp(0.18f, speed, speedTarget);

        // flash timing + decay (fast)
        if (flash > 0.001f) {
            flashTime += 1f / 20f;
            // quick decay
            flash = MathHelper.lerp(0.28f, flash, 0f);
        } else {
            flash = 0f;
            // keep time large when idle
            flashTime = 999f;
        }
    }

    /** 0..1 target intensity for the slipstream overlay. */
    public static void setSpeedTarget(float t) {
        speedTarget = MathHelper.clamp(t, 0f, 1f);
    }

    /** Trigger recall flash (player-only packet). */
    public static void triggerRecallFlash(float strength01) {
        float s = MathHelper.clamp(strength01, 0f, 1f);
        flash = Math.max(flash, s);
        flashTime = 0f;
    }
}