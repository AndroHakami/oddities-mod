package net.seep.odd.abilities.core.client;

import ladysnake.satin.api.event.ShaderEffectRenderCallback;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public final class CoreTickingScreenFx {
    private CoreTickingScreenFx() {}

    private static final Identifier POST = new Identifier("odd", "shaders/post/core_ticking_screen.json");

    private static boolean inited = false;
    private static ManagedShaderEffect effect;

    private static int activeTicks = 0;
    private static int totalTicks = 1;
    private static float intensity = 0.0F;

    public static void init() {
        if (inited) return;
        inited = true;

        effect = ShaderEffectManager.getInstance().manage(POST);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            activeTicks = 0;
            totalTicks = 1;
            intensity = 0.0F;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> clientTick());

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            if (effect == null) return;

            float a = MathHelper.clamp(intensity, 0.0F, 1.0F);
            if (a <= 0.001F) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return;

            int fbW = mc.getWindow().getFramebufferWidth();
            int fbH = mc.getWindow().getFramebufferHeight();
            float timeSec = mc.world != null ? (mc.world.getTime() + tickDelta) / 20.0F : 0.0F;

            effect.setUniformValue("OutSize", (float) fbW, (float) fbH);
            effect.setUniformValue("Time", timeSec);
            effect.setUniformValue("Intensity", a);

            effect.render(tickDelta);
        });
    }

    private static void clientTick() {
        if (activeTicks > 0) activeTicks--;

        float target = activeTicks > 0 ? 1.0F : 0.0F;
        float age01 = 1.0F - (activeTicks / (float) Math.max(1, totalTicks));
        float shaped = target * (0.88F + 0.12F * (float) Math.sin(age01 * 10.0F));

        intensity = MathHelper.lerp(activeTicks > 0 ? 0.16F : 0.24F, intensity, shaped);
        if (activeTicks <= 0 && intensity <= 0.001F) {
            intensity = 0.0F;
        }
    }

    public static void begin(int holdTicks) {
        totalTicks = Math.max(1, holdTicks);
        activeTicks = Math.max(activeTicks, holdTicks);
    }
}
