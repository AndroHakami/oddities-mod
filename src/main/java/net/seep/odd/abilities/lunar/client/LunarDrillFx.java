package net.seep.odd.abilities.lunar.client;

import ladysnake.satin.api.event.ShaderEffectRenderCallback;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.lunar.item.LunarDrillItem;

@Environment(EnvType.CLIENT)
public final class LunarDrillFx {
    private LunarDrillFx() {}

    private static boolean inited = false;
    private static ManagedShaderEffect effect;

    private static float strength = 0f;
    private static float target = 0f;

    public static void init() {
        if (inited) return;
        inited = true;

        effect = ShaderEffectManager.getInstance()
                .manage(new Identifier(Oddities.MOD_ID, "shaders/post/lunar_drill_charge.json"));

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null || mc.player == null) {
                strength = 0f;
                target = 0f;
                return;
            }

            boolean using = mc.player.isUsingItem()
                    && (mc.player.getActiveItem().getItem() instanceof LunarDrillItem);

            float charge = LunarDrillPreview.getChargeProgress(); // 0..1 (client-only)
            target = (using && charge > 0f) ? 1f : 0f;

            strength += (target - strength) * 0.18f;
            if (strength < 0.001f) return;

            effect.setUniformValue("Intensity", strength);
            effect.setUniformValue("Charge", charge);
            effect.setUniformValue("Time", (float)(mc.world.getTime() + tickDelta) / 20f);
            effect.setUniformValue("OutSize",
                    (float) mc.getWindow().getFramebufferWidth(),
                    (float) mc.getWindow().getFramebufferHeight());

            effect.render(tickDelta);
        });
    }
}