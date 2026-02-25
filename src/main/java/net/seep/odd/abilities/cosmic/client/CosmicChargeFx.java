// src/main/java/net/seep/odd/abilities/cosmic/client/CosmicChargeFx.java
package net.seep.odd.abilities.cosmic.client;

import ladysnake.satin.api.event.ShaderEffectRenderCallback;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import net.seep.odd.abilities.power.CosmicPower;

@Environment(EnvType.CLIENT)
public final class CosmicChargeFx {
    private CosmicChargeFx() {}

    private static final Identifier POST = new Identifier("odd", "shaders/post/cosmic_charge.json");

    private static boolean inited = false;
    private static ManagedShaderEffect effect;

    private static boolean charging = false;
    private static int chargeTicks = 0;

    private static float intensity = 0f;       // smoothed
    private static float intensityTarget = 0f; // 0..1

    public static void init() {
        if (inited) return;
        inited = true;

        effect = ShaderEffectManager.getInstance().manage(POST);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> clientTick());

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
            effect.setUniformValue("ChargeIntensity", a);

            effect.render(tickDelta);
        });
    }

    private static void clientTick() {
        if (!inited) return;

        if (charging) {
            chargeTicks++;
            float t = MathHelper.clamp(chargeTicks / (float) CosmicPower.STANCE_MAX_TICKS, 0f, 1f);
            // nice ease-in
            intensityTarget = t * t * (3f - 2f * t);
        } else {
            intensityTarget = 0f;
        }

        // smooth
        intensity = MathHelper.lerp(0.22f, intensity, intensityTarget);

        // when fully off, reset ticks
        if (!charging && intensity <= 0.001f) {
            intensity = 0f;
            chargeTicks = 0;
        }
    }

    public static void beginCharge() {
        charging = true;
        chargeTicks = 0;
    }

    public static void endCharge() {
        charging = false;
    }
}