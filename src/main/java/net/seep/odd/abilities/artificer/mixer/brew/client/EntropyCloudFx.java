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
public final class EntropyCloudFx {
    private EntropyCloudFx() {}

    private static boolean inited = false;
    private static ManagedShaderEffect effect;

    private static float strength = 0f;  // 0..1
    private static float target = 0f;    // 0..1
    private static float t = 0f;

    public static void init() {
        if (inited) return;
        inited = true;

        effect = ShaderEffectManager.getInstance().manage(new Identifier("odd", "shaders/post/entropy_cloud.json"));

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            if (effect == null) return;
            if (strength <= 0.001f) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) return;

            t += 0.02f + 0.05f * strength;

            effect.setUniformValue("Time", t);
            effect.setUniformValue("Intensity", strength);

            effect.render(tickDelta);
        });
    }

    public static void setTarget(float v) {
        target = MathHelper.clamp(v, 0f, 1f);
    }

    public static void tickClient() {
        // smooth in/out
        strength = MathHelper.lerp(0.18f, strength, target);

        // hard stop if basically off
        if (strength < 0.001f && target <= 0.001f) {
            strength = 0f;
        }
    }
}
