// FILE: src/main/java/net/seep/odd/abilities/misty/client/MistyHoverFx.java
package net.seep.odd.abilities.misty.client;

import ladysnake.satin.api.event.ShaderEffectRenderCallback;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

@Environment(EnvType.CLIENT)
public final class MistyHoverFx {
    private MistyHoverFx() {}

    private static boolean inited = false;
    private static ManagedShaderEffect effect;

    // Driven ONLY by net now (no status effects)
    private static boolean hoverEnabled = false; // toggle on/off
    private static boolean hoverActive  = false; // currently hovering (stronger)

    // smooth fade
    private static float strength = 0f;

    /** Called by MistyNet S2C packet. */
    public static void setState(boolean enabled, boolean active) {
        hoverEnabled = enabled;
        hoverActive  = active;
    }

    public static void clear() {
        hoverEnabled = false;
        hoverActive  = false;
    }

    public static void init() {
        if (inited) return;
        inited = true;

        effect = ShaderEffectManager.getInstance()
                .manage(new Identifier(Oddities.MOD_ID, "shaders/post/misty_hover_edge.json"));

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null || mc.player == null) {
                strength = 0f;
                return;
            }

            float target = 0f;
            if (hoverEnabled) {
                // subtle when toggle is on, stronger when actively hovering
                target = hoverActive ? 0.95f : 0.55f;
            }

            // smooth ease
            strength += (target - strength) * 0.10f;
            if (strength < 0.001f) return;

            effect.setUniformValue("Intensity", strength);
            effect.setUniformValue("Time", (float)(mc.world.getTime() + tickDelta) / 20f);
            effect.render(tickDelta);
        });
    }
}