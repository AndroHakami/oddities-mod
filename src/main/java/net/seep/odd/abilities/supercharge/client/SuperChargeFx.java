package net.seep.odd.abilities.supercharge.client;

import com.mojang.blaze3d.systems.RenderSystem;
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
public final class SuperChargeFx {
    private SuperChargeFx() {}

    private static final Identifier POST = new Identifier("odd", "shaders/post/supercharge_empower.json");

    private static boolean inited = false;
    private static ManagedShaderEffect effect;

    private static float intensity = 0f;
    private static float intensityTarget = 0f;

    private static float progress = 0f;
    private static float progressTarget = 0f;

    public static void init() {
        if (inited) return;
        inited = true;

        effect = ShaderEffectManager.getInstance().manage(POST);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            intensity = MathHelper.lerp(0.22f, intensity, intensityTarget);
            progress  = MathHelper.lerp(0.30f, progress,  progressTarget);

            if (intensity < 0.001f && intensityTarget < 0.001f) intensity = 0f;
            if (progress  < 0.001f && progressTarget  < 0.001f) progress  = 0f;
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
            effect.setUniformValue("Progress", MathHelper.clamp(progress, 0f, 1f));

            // ✅ CRITICAL FIX: prevent depth/scissor state from killing the fullscreen quad
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.disableScissor(); // harmless if already disabled

            effect.render(tickDelta);

            // restore sane defaults
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
        });
    }

    public static void setActive(boolean active, float pct) {
        intensityTarget = active ? 1f : 0f;
        progressTarget  = active ? MathHelper.clamp(pct, 0f, 1f) : 0f;
    }
}