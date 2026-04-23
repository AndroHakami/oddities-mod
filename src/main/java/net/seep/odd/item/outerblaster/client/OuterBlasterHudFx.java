
package net.seep.odd.item.outerblaster.client;

import ladysnake.satin.api.event.ShaderEffectRenderCallback;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.Oddities;

@Environment(EnvType.CLIENT)
public final class OuterBlasterHudFx {
    private static boolean inited = false;
    private static ManagedShaderEffect effect;

    private static boolean enabled = false;
    private static boolean overheated = false;
    private static float heat01 = 0.0f;
    private static float intensity = 0.0f;

    private OuterBlasterHudFx() {}

    public static void init() {
        if (inited) return;
        inited = true;

        effect = ShaderEffectManager.getInstance()
                .manage(new Identifier(Oddities.MOD_ID, "shaders/post/outer_blaster_hud.json"));

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            if (effect == null) return;

            float target = enabled ? 1.0f : 0.0f;
            intensity = MathHelper.lerp(0.26f, intensity, target);

            if (intensity <= 0.003f) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null) return;

            float timeSec = (client.world != null) ? (client.world.getTime() + tickDelta) / 20.0f : 0.0f;

            setUniform("iTime", timeSec);
            setUniform("Intensity", intensity);
            setUniform("Heat", heat01);
            setUniform("Overheated", overheated ? 1.0f : 0.0f);
            setUniform("OutSize",
                    (float) client.getWindow().getFramebufferWidth(),
                    (float) client.getWindow().getFramebufferHeight());

            effect.render(tickDelta);
        });
    }

    public static void onHud(boolean show, float heat, float maxHeat, boolean isOverheated) {
        init();
        enabled = show;
        overheated = isOverheated;
        heat01 = maxHeat <= 0.0f ? 0.0f : MathHelper.clamp(heat / maxHeat, 0.0f, 1.0f);
    }

    private static void setUniform(String name, float v) {
        if (effect == null) return;
        try { effect.setUniformValue(name, v); } catch (Throwable ignored) {}
    }

    private static void setUniform(String name, float v0, float v1) {
        if (effect == null) return;
        try { effect.setUniformValue(name, v0, v1); } catch (Throwable ignored) {}
    }
}
