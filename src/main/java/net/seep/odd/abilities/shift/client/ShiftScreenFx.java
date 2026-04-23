package net.seep.odd.abilities.shift.client;

import ladysnake.satin.api.event.ShaderEffectRenderCallback;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public final class ShiftScreenFx {
    private ShiftScreenFx() {}

    private static final Identifier POST = new Identifier("odd", "shaders/post/shift_screen.json");

    private static boolean inited = false;
    private static ManagedShaderEffect effect;

    private static boolean imbued = false;
    private static boolean tagged = false;

    private static float imbuedStrength = 0.0F;
    private static float taggedStrength = 0.0F;
    private static float pulseStrength = 0.0F;

    public static void init() {
        if (inited) return;
        inited = true;

        effect = ShaderEffectManager.getInstance().manage(POST);
        ClientTickEvents.END_CLIENT_TICK.register(client -> clientTick());

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            if (effect == null) return;

            float i = MathHelper.clamp(imbuedStrength, 0.0F, 1.0F);
            float t = MathHelper.clamp(taggedStrength, 0.0F, 1.0F);
            float p = MathHelper.clamp(pulseStrength, 0.0F, 1.0F);

            if (i <= 0.001F && t <= 0.001F && p <= 0.001F) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return;

            int fbW = mc.getWindow().getFramebufferWidth();
            int fbH = mc.getWindow().getFramebufferHeight();

            float timeSec = 0.0F;
            if (mc.world != null) {
                timeSec = (mc.world.getTime() + tickDelta) / 20.0F;
            }

            effect.setUniformValue("OutSize", (float) fbW, (float) fbH);
            effect.setUniformValue("Time", timeSec);
            // toned down a bit so the overlay is subtler overall
            effect.setUniformValue("ImbueIntensity", i * 0.52F);
            effect.setUniformValue("TaggedIntensity", t * 0.50F);
            effect.setUniformValue("PulseIntensity", p * 0.42F);

            effect.render(tickDelta);
        });
    }

    private static void clientTick() {
        float imbuedTarget = imbued ? 1.0F : 0.0F;
        float taggedTarget = tagged ? 1.0F : 0.0F;

        imbuedStrength = MathHelper.lerp(imbued ? 0.14F : 0.22F, imbuedStrength, imbuedTarget);
        taggedStrength = MathHelper.lerp(tagged ? 0.16F : 0.24F, taggedStrength, taggedTarget);
        pulseStrength = MathHelper.lerp(0.26F, pulseStrength, 0.0F);

        if (!imbued && imbuedStrength <= 0.001F) imbuedStrength = 0.0F;
        if (!tagged && taggedStrength <= 0.001F) taggedStrength = 0.0F;
        if (pulseStrength <= 0.001F) pulseStrength = 0.0F;
    }

    public static void setImbued(boolean enabled) {
        imbued = enabled;
    }

    public static void setTagged(boolean enabled) {
        tagged = enabled;
    }

    public static void pulse(float strength) {
        pulseStrength = Math.max(pulseStrength, MathHelper.clamp(strength, 0.0F, 1.0F));
    }
}
