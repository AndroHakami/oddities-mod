package net.seep.odd.expeditions.atheneum.granny.client;

import ladysnake.satin.api.event.ShaderEffectRenderCallback;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public final class GrannyFx {
    private static final Identifier POST = new Identifier("odd", "shaders/post/granny.json");

    private static boolean inited = false;
    private static ManagedShaderEffect effect;
    private static float intensity = 0.0f;
    private static float target = 0.0f;

    private GrannyFx() {}

    public static void init() {
        if (inited) return;
        inited = true;

        effect = ShaderEffectManager.getInstance().manage(POST);

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            if (effect == null) return;
            float s = MathHelper.clamp(intensity, 0.0f, 1.0f);
            if (s <= 0.001f) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null || mc.getWindow() == null) return;

            int fbW = mc.getWindow().getFramebufferWidth();
            int fbH = mc.getWindow().getFramebufferHeight();
            float timeSec = (mc.world.getTime() + tickDelta) / 20.0f;

            effect.setUniformValue("OutSize", (float) fbW, (float) fbH);
            effect.setUniformValue("Time", timeSec);
            effect.setUniformValue("Intensity", s);
            effect.render(tickDelta);
        });
    }

    public static void clientTick() {
        intensity = MathHelper.lerp(0.18f, intensity, target);
    }

    public static void setIntensityTarget(float value) {
        target = MathHelper.clamp(value, 0.0f, 1.0f);
    }
}