package net.seep.odd.abilities.vampire.client;

import ladysnake.satin.api.event.ShaderEffectRenderCallback;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.Oddities;

@Environment(EnvType.CLIENT)
public final class VampireFrenzyFx {
    private VampireFrenzyFx() {}

    private static boolean inited = false;
    private static ManagedShaderEffect effect;

    private static float strength = 0f; // 0..1
    private static float target = 0f;   // 0..1
    private static float time = 0f;

    public static void init() {
        if (inited) return;
        inited = true;

        effect = ShaderEffectManager.getInstance()
                .manage(new Identifier(Oddities.MOD_ID, "shaders/post/vampire_frenzy.json"));

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            // smooth fade
            strength = MathHelper.lerp(0.12f, strength, target);

            if (effect == null) return;
            if (strength <= 0.001f) return;

            time += 0.05f + tickDelta * 0.05f;

            effect.setUniformValue("Intensity", strength);
            effect.setUniformValue("Time", time);
            effect.render(tickDelta);
        });
    }

    public static void setActive(boolean v) {
        target = v ? 1.0f : 0.0f;
    }
}
