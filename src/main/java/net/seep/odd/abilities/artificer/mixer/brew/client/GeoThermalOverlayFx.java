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
public final class GeoThermalOverlayFx {
    private GeoThermalOverlayFx() {}

    private static boolean inited = false;
    private static ManagedShaderEffect effect;

    private static float strength = 0f;
    private static float target = 0f;
    private static float t = 0f;

    public static void init() {
        if (inited) return;
        inited = true;

        effect = ShaderEffectManager.getInstance().manage(new Identifier("odd", "shaders/post/geothermal_overlay.json"));

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            if (effect == null) return;
            if (strength <= 0.001f) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) return;

            t += 0.020f + 0.10f * strength;

            effect.setUniformValue("Time", t);
            effect.setUniformValue("Strength", strength);

            effect.render(tickDelta);
        });
    }

    public static void start(int durationTicks) { target = 1.0f; }
    public static void stop() {
        target = 0.0f;
        strength = 0.0f; // hard stop so it can't stick
    }

    public static void tickClient() {
        strength = MathHelper.lerp(0.20f, strength, target);
        if (target <= 0.001f && strength <= 0.002f) strength = 0.0f;
    }
}
