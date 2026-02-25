package net.seep.odd.abilities.artificer.mixer.brew.client;

import ladysnake.satin.api.event.ShaderEffectRenderCallback;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public final class BrambleFx {
    private BrambleFx() {}

    private static boolean inited = false;
    private static ManagedShaderEffect effect;

    private static float strength = 0f;
    private static float target = 0f;
    private static float pulse = 0f;
    private static float t = 0f;

    public static void init() {
        if (inited) return;
        inited = true;

        effect = ShaderEffectManager.getInstance().manage(new Identifier("odd", "shaders/post/radiant_bramble.json"));

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            if (effect == null) return;
            if (strength <= 0.001f && pulse <= 0.001f) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) return;

            t += 0.02f + 0.05f * strength;

            effect.setUniformValue("Time", t);
            effect.setUniformValue("Strength", strength);
            effect.setUniformValue("Pulse", pulse);

            effect.render(tickDelta);
        });
    }

    public static void start(int durationTicks) {
        target = 1.0f;
        // give a little burst on start
        pulse = Math.max(pulse, 0.65f);
    }

    public static void stop() {
        target = 0.0f;
    }

    public static void pulse() {
        pulse = Math.max(pulse, 1.0f);
    }

    public static void tickClient() {
        strength = MathHelper.lerp(0.18f, strength, target);

        // decay pulse quickly but smoothly
        pulse = MathHelper.lerp(0.22f, pulse, 0.0f);

        // hard clamp tiny values so it doesn't “stick”
        if (strength < 0.001f) strength = 0f;
        if (pulse < 0.001f) pulse = 0f;
    }
}
