// FILE: src/main/java/net/seep/odd/abilities/wizard/client/WizardScreenShakeClient.java
package net.seep.odd.abilities.wizard.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public final class WizardScreenShakeClient {
    private WizardScreenShakeClient() {}

    private static int ticksLeft = 0;
    private static int totalTicks = 0;
    private static float strength = 0f;

    public static void trigger(int ticks, float str) {
        ticksLeft = Math.max(ticksLeft, ticks);
        totalTicks = Math.max(totalTicks, ticks);
        strength = Math.max(strength, str);
    }

    public static void init() {
        WorldRenderEvents.START.register(ctx -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return;
            if (ticksLeft <= 0 || totalTicks <= 0) return;

            float t = (mc.world.getTime() + ctx.tickDelta());
            float fade = ticksLeft / (float) totalTicks;
            float amp = strength * fade;

            double ox = (MathHelper.sin((float)(t * 3.7f)) * 0.06f) * amp;
            double oy = (MathHelper.sin((float)(t * 4.3f)) * 0.035f) * amp;
            double oz = (MathHelper.cos((float)(t * 3.1f)) * 0.06f) * amp;

            ctx.matrixStack().translate(ox, oy, oz);
            ticksLeft--;
            if (ticksLeft <= 0) { totalTicks = 0; strength = 0f; }
        });
    }
}
