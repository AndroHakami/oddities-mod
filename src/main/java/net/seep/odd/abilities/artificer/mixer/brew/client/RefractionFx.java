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
public final class RefractionFx {
    private RefractionFx() {}

    private static boolean inited = false;
    private static ManagedShaderEffect effect;

    private static float strength = 0f;
    private static float target = 0f;
    private static float t = 0f;

    private static boolean playedSoundThisRun = false;

    public static void init() {
        if (inited) return;
        inited = true;

        effect = ShaderEffectManager.getInstance().manage(new Identifier("odd", "shaders/post/refraction.json"));

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            if (effect == null) return;
            if (strength <= 0.001f) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) return;

            t += 0.020f + 0.06f * strength;

            effect.setUniformValue("Time", t);
            effect.setUniformValue("Strength", strength);

            effect.render(tickDelta);
        });
    }

    public static void start(int durationTicks) {
        target = 1.0f;

        if (!playedSoundThisRun) {
            playedSoundThisRun = true;
            RefractionSoundInstance.playOnce(durationTicks); // follows player
        }
    }

    public static void stop() {
        target = 0.0f;
        playedSoundThisRun = false;

        // IMPORTANT: hard stop so it can't “stick” forever
        strength = 0.0f;
    }

    /** Call every client tick (always). */
    public static void tickClient() {
        // Smooth towards target
        strength = MathHelper.lerp(0.20f, strength, target);

        // If we're basically off, snap to zero so render callback stops
        if (target <= 0.001f && strength <= 0.002f) {
            strength = 0.0f;
        }
    }
}
