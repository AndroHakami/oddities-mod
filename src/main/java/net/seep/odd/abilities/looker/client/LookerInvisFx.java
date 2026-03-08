package net.seep.odd.abilities.looker.client;

import ladysnake.satin.api.event.ShaderEffectRenderCallback;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

@Environment(EnvType.CLIENT)
public final class LookerInvisFx {
    private LookerInvisFx() {}

    private static boolean inited = false;
    private static ManagedShaderEffect effect;

    private static boolean active = false;
    private static float meter01 = 1.0f;

    private static float strength = 0f;
    private static float target = 0f;

    public static void setActive(boolean on, int meter, int max) {
        active = on;
        target = on ? 1f : 0f;
        meter01 = (max <= 0) ? 0f : Math.max(0f, Math.min(1f, meter / (float) max));
    }

    public static boolean isActive() {
        return active;
    }

    public static void init() {
        if (inited) return;
        inited = true;

        effect = ShaderEffectManager.getInstance()
                .manage(new Identifier(Oddities.MOD_ID, "shaders/post/looker_invis_swirl.json"));

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null || mc.player == null) {
                strength = 0f;
                target = 0f;
                active = false;
                return;
            }

            strength += (target - strength) * 0.14f;
            if (strength < 0.001f) return;

            effect.setUniformValue("Intensity", strength);
            effect.setUniformValue("Time", (float)(mc.world.getTime() + tickDelta) / 20f);
            effect.setUniformValue("Meter01", meter01);
            effect.setUniformValue("OutSize",
                    (float) mc.getWindow().getFramebufferWidth(),
                    (float) mc.getWindow().getFramebufferHeight());

            effect.render(tickDelta);
        });
    }
}