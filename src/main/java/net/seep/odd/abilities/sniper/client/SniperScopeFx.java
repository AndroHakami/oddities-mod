// FILE: src/main/java/net/seep/odd/abilities/sniper/client/SniperScopeFx.java
package net.seep.odd.abilities.sniper.client;

import ladysnake.satin.api.event.ShaderEffectRenderCallback;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import net.seep.odd.Oddities;

@Environment(EnvType.CLIENT)
public final class SniperScopeFx {
    private SniperScopeFx() {}

    private static boolean inited = false;
    private static ManagedShaderEffect effect;

    public static void init() {
        if (inited) return;
        inited = true;

        effect = ShaderEffectManager.getInstance()
                .manage(new Identifier(Oddities.MOD_ID, "shaders/post/sniper_scope.json"));

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            if (effect == null) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null) return;

            float a = SniperClientState.scopeAmount(tickDelta); // 0..1
            if (a <= 0.001f) return;

            float time = (mc.world.getTime() + tickDelta) / 20.0f;
            int w = mc.getWindow().getFramebufferWidth();
            int h = mc.getWindow().getFramebufferHeight();

            effect.setUniformValue("Intensity", a);
            effect.setUniformValue("Time", time);
            effect.setUniformValue("OutSize", (float) w, (float) h);

            effect.render(tickDelta);
        });
    }
}
