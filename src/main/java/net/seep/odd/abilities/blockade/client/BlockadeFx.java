// src/main/java/net/seep/odd/abilities/blockade/client/BlockadeFx.java
package net.seep.odd.abilities.blockade.client;

import ladysnake.satin.api.event.ShaderEffectRenderCallback;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

@Environment(EnvType.CLIENT)
public final class BlockadeFx {
    private BlockadeFx() {}

    private static boolean inited = false;
    private static ManagedShaderEffect effect;

    // client-side state
    private static boolean active = false;

    // smooth fade
    private static float strength = 0f;
    private static float target = 0f;

    public static void setActive(boolean v) {
        active = v;
        target = v ? 1f : 0f;
    }

    public static boolean isActive() {
        return active;
    }

    public static void init() {
        if (inited) return;
        inited = true;

        effect = ShaderEffectManager.getInstance()
                .manage(new Identifier(Oddities.MOD_ID, "shaders/post/blockade_edge.json"));

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null || mc.player == null) {
                strength = 0f;
                target = 0f;
                return;
            }

            // ease in/out
            float speed = 0.12f; // tweak: higher = snappier
            strength += (target - strength) * speed;

            if (strength < 0.001f) return;

            // uniforms
            effect.setUniformValue("Intensity", strength);
            effect.setUniformValue("Time", (float)(mc.world.getTime() + tickDelta) / 20f);

            // render the post chain
            effect.render(tickDelta);
        });
    }
}
