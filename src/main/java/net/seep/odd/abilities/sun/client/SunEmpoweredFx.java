package net.seep.odd.abilities.sun.client;

import ladysnake.satin.api.event.ShaderEffectRenderCallback;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public final class SunEmpoweredFx {
    private SunEmpoweredFx() {}

    private static final Identifier POST = new Identifier("odd", "shaders/post/sun_empowered.json");

    private static boolean inited = false;
    private static ManagedShaderEffect effect;

    private static boolean active = false;
    private static float intensity = 0f;
    private static float targetIntensity = 0f;

    public static void init() {
        if (inited) return;
        inited = true;

        effect = ShaderEffectManager.getInstance().manage(POST);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            targetIntensity = active ? 1.0f : 0.0f;
            intensity = MathHelper.lerp(active ? 0.12f : 0.18f, intensity, targetIntensity);
            if (!active && intensity < 0.001f) intensity = 0.0f;
        });

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            if (effect == null || intensity <= 0.001f) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return;
            int fbW = mc.getWindow().getFramebufferWidth();
            int fbH = mc.getWindow().getFramebufferHeight();
            float timeSec = mc.world != null ? (mc.world.getTime() + tickDelta) / 20f : 0f;
            effect.setUniformValue("OutSize", (float) fbW, (float) fbH);
            effect.setUniformValue("Time", timeSec);
            effect.setUniformValue("Intensity", intensity);
            effect.render(tickDelta);
        });
    }

    public static void begin() { active = true; }
    public static void end() { active = false; }
}
