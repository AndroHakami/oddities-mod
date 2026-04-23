package net.seep.odd.sky.day;

import ladysnake.satin.api.event.ShaderEffectRenderCallback;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.seep.odd.Oddities;

@Environment(EnvType.CLIENT)
public final class BiomeDayGradeFx {
    private BiomeDayGradeFx() {}

    private static boolean inited = false;
    private static ManagedShaderEffect effect;

    public static void init() {
        if (inited) return;
        inited = true;

        effect = ShaderEffectManager.getInstance().manage(
                new Identifier(Oddities.MOD_ID, "shaders/post/biome_day_grade.json")
        );

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientWorld world = mc.world;
            if (world == null || mc.player == null) return;
            if (!world.getRegistryKey().equals(World.OVERWORLD)) return;

            float dayAmount = BiomeDayProfileClientStore.getDayAmount(world, tickDelta);
            if (dayAmount <= 0.001f) return;

            Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
            BiomeDayProfileClientStore.DayBiomeBlend blend =
                    BiomeDayProfileClientStore.sample(world, cameraPos);

            float amount = blend.weight() * dayAmount;
            if (amount <= 0.001f) return;

            BlockPos eyePos = BlockPos.ofFloored(cameraPos);
            int skyLight = world.getLightLevel(LightType.SKY, eyePos);
            float skyExposure = MathHelper.clamp((skyLight - 2.0f) / 13.0f, 0.0f, 1.0f);

            float openSkyBoost = world.isSkyVisible(eyePos) ? 1.0f : 0.0f;
            float outdoorFactor = Math.max(openSkyBoost, skyExposure * 0.85f);

            // Kill the color grade in caves / enclosed spaces so custom sky colors
            // do not make underground areas feel unnaturally dark.
            amount *= outdoorFactor;
            if (amount <= 0.001f) return;

            float t = (world.getTime() + tickDelta) / 20.0f;

            Vec3d sky = blend.sky();
            Vec3d fog = blend.fog();
            Vec3d horizon = blend.horizon();
            Vec3d cloud = blend.cloud();

            effect.setUniformValue("iTime", t);
            effect.setUniformValue("Intensity", amount * 1.10f);

            effect.setUniformValue("SkyTint", (float) sky.x, (float) sky.y, (float) sky.z);
            effect.setUniformValue("FogTint", (float) fog.x, (float) fog.y, (float) fog.z);
            effect.setUniformValue("HorizonTint", (float) horizon.x, (float) horizon.y, (float) horizon.z);
            effect.setUniformValue("CloudTint", (float) cloud.x, (float) cloud.y, (float) cloud.z);

            effect.render(tickDelta);
        });
    }
}
