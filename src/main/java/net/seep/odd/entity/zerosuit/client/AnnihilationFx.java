package net.seep.odd.entity.zerosuit.client;

import ladysnake.satin.api.event.ShaderEffectRenderCallback;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import net.seep.odd.Oddities;

import java.lang.reflect.Method;

@Environment(EnvType.CLIENT)
public final class AnnihilationFx {
    private AnnihilationFx() {}

    private static boolean inited = false;


    private static final Identifier EFFECT_ID =
            new Identifier(Oddities.MOD_ID, "shaders/post/annihilation.json");

    private static ManagedShaderEffect effect;

    private static boolean active = false;
    private static int startAge = 0;
    private static int durationTicks = 0;

    public static void init() {
        if (inited) return;
        inited = true;

        effect = ShaderEffectManager.getInstance().manage(EFFECT_ID);

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            if (!active || effect == null) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null || mc.world == null) {
                stop();
                return;
            }

            int age = mc.player.age;
            float t = (age + tickDelta - startAge) / (float) Math.max(1, durationTicks);
            if (t >= 1.0f) {
                stop();
                return;
            }

            // Smooth in/out envelope
            float in = smoothstep(0.00f, 0.15f, t);
            float out = 1.0f - smoothstep(0.70f, 1.00f, t);
            float intensity = MathHelper.clamp(in * out, 0f, 1f);

            // Radius grows then stabilizes
            float radius = smoothstep(0.02f, 0.55f, t) * 1.15f;

            int w = mc.getWindow().getFramebufferWidth();
            int h = mc.getWindow().getFramebufferHeight();
            float aspect = (h > 0) ? (w / (float) h) : 1.0f;

            float timeSec = (age + tickDelta) / 20.0f;

            // Push uniforms into the *program* used by the post pass.
            // (Reflection keeps this resilient to tiny Satin API diffs.)
            trySetUniform(effect, "Time", timeSec);
            trySetUniform(effect, "Intensity", intensity);
            trySetUniform(effect, "Radius", radius);
            trySetUniform(effect, "Aspect", aspect);
            trySetUniform(effect, "Center", 0.5f, 0.5f);

            effect.render(tickDelta);
        });
    }

    public static void trigger(int ticks) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        active = true;
        startAge = mc.player.age;
        durationTicks = Math.max(1, ticks);
    }

    public static void stop() {
        active = false;
        startAge = 0;
        durationTicks = 0;
    }

    public static boolean isActive() {
        return active;
    }

    private static float smoothstep(float a, float b, float x) {
        float t = MathHelper.clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static void trySetUniform(ManagedShaderEffect fx, String name, float v0) {
        tryInvokeSetUniformValue(fx, new Class<?>[]{String.class, float.class}, new Object[]{name, v0});
    }

    private static void trySetUniform(ManagedShaderEffect fx, String name, float v0, float v1) {
        tryInvokeSetUniformValue(fx, new Class<?>[]{String.class, float.class, float.class}, new Object[]{name, v0, v1});
    }

    private static void tryInvokeSetUniformValue(ManagedShaderEffect fx, Class<?>[] sig, Object[] args) {
        try {
            Method m = fx.getClass().getMethod("setUniformValue", sig);
            m.invoke(fx, args);
        } catch (Throwable ignored) {
            // If this method isn't available in your Satin build,
            // the effect will still render with JSON defaults.
        }
    }
}
