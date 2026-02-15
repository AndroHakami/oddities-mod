package net.seep.odd.sky.client;

import ladysnake.satin.api.event.ShaderEffectRenderCallback;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import net.seep.odd.Oddities;

@Environment(EnvType.CLIENT)
public final class AtheneumGradeFx {
    private AtheneumGradeFx() {}

    private static final RegistryKey<World> ATHENEUM =
            RegistryKey.of(RegistryKeys.WORLD, new Identifier(Oddities.MOD_ID, "atheneum"));

    private static boolean inited = false;
    private static ManagedShaderEffect effect;

    public static void init() {
        if (inited) return;
        inited = true;

        effect = ShaderEffectManager.getInstance().manage(
                new Identifier(Oddities.MOD_ID, "shaders/post/atheneum_grade.json")
        );

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return;
            if (!mc.world.getRegistryKey().equals(ATHENEUM)) return;

            float t = (float)(mc.world.getTime() + tickDelta);

            // ✅ Only time is driven by code (keeps it animatable)
            effect.setUniformValue("iTime", t / 20.0f);

            // ❌ Do NOT set Intensity/Tint here (data-driven from JSON)
            effect.render(tickDelta);
        });
    }
}
