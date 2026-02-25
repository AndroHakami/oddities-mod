package net.seep.odd.event.alien.client.fx;

import ladysnake.satin.api.event.ShaderEffectRenderCallback;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.seep.odd.Oddities;
import net.seep.odd.event.alien.client.AlienInvasionClientState;

@Environment(EnvType.CLIENT)
public final class AlienOverworldSkyFx {
    private AlienOverworldSkyFx() {}

    private static boolean inited = false;
    private static ManagedShaderEffect effect;

    public static void init() {
        if (inited) return;
        inited = true;

        effect = ShaderEffectManager.getInstance().manage(
                new Identifier(Oddities.MOD_ID, "shaders/post/alien_overworld_sky.json")
        );

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null || mc.player == null) return;
            if (mc.world.getRegistryKey() != World.OVERWORLD) return;
            if (!AlienInvasionClientState.active()) return;

            float t = (float)(mc.world.getTime() + tickDelta);

            effect.setUniformValue("iTime", t);
            effect.setUniformValue("Progress", AlienInvasionClientState.skyProgress01(tickDelta));
            effect.setUniformValue("CubeIntensity", AlienInvasionClientState.cubes01(tickDelta));
            effect.render(tickDelta);
        });
    }
}