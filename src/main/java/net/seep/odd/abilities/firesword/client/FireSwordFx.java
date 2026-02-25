// src/main/java/net/seep/odd/abilities/firesword/client/FireSwordFx.java
package net.seep.odd.abilities.firesword.client;

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
public final class FireSwordFx {
    private FireSwordFx() {}

    private static final Identifier POST = new Identifier("odd", "shaders/post/firesword_conjure.json");

    private static boolean inited = false;
    private static ManagedShaderEffect effect;

    private static float intensity = 0f;
    private static float intensityTarget = 0f;

    public static void init() {
        if (inited) return;
        inited = true;

        effect = ShaderEffectManager.getInstance().manage(POST);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            intensity = MathHelper.lerp(0.22f, intensity, intensityTarget);
            if (intensity < 0.001f && intensityTarget < 0.001f) intensity = 0f;
        });

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            if (effect == null) return;

            float a = MathHelper.clamp(intensity, 0f, 1f);
            if (a <= 0.001f) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return;

            int fbW = mc.getWindow().getFramebufferWidth();
            int fbH = mc.getWindow().getFramebufferHeight();

            float timeSec = 0f;
            if (mc.world != null) timeSec = (mc.world.getTime() + tickDelta) / 20f;

            effect.setUniformValue("OutSize", (float) fbW, (float) fbH);
            effect.setUniformValue("Time", timeSec);
            effect.setUniformValue("Intensity", a);

            effect.render(tickDelta);
        });
    }

    public static void setActive(boolean active) {
        intensityTarget = active ? 1f : 0f;
    }
}