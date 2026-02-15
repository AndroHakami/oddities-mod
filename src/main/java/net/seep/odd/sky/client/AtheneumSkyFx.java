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
public final class AtheneumSkyFx {
    private AtheneumSkyFx() {}

    private static final RegistryKey<World> ATHENEUM =
            RegistryKey.of(RegistryKeys.WORLD, new Identifier(Oddities.MOD_ID, "atheneum"));

    private static boolean inited = false;
    private static ManagedShaderEffect effect;

    public static void init() {
        if (inited) return;
        inited = true;

        effect = ShaderEffectManager.getInstance().manage(
                new Identifier(Oddities.MOD_ID, "shaders/post/atheneum_sky.json")
        );

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null || mc.player == null) return;
            if (!mc.world.getRegistryKey().equals(ATHENEUM)) return;

            // Animated shimmer / sparkles (even though sky time is fixed)
            float t = (float)(mc.world.getTime() + tickDelta);

            effect.setUniformValue("iTime", t);
            effect.setUniformValue("Intensity", 1.0f);
            effect.render(tickDelta);
        });
    }
}
