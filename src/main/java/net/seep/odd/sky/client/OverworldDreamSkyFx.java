package net.seep.odd.sky.client;

import com.mojang.blaze3d.systems.RenderSystem;
import ladysnake.satin.api.managed.ManagedCoreShader;
import ladysnake.satin.api.managed.ShaderEffectManager;
import ladysnake.satin.api.managed.uniform.Uniform1f;
import ladysnake.satin.api.managed.uniform.UniformMat4;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.seep.odd.Oddities;
import org.joml.Matrix4f;

public final class OverworldDreamSkyFx {
    private OverworldDreamSkyFx() {}

    public static final ManagedCoreShader DREAM_SKY = ShaderEffectManager.getInstance().manageCoreShader(
            new Identifier(Oddities.MOD_ID, "overworld_dream_sky"),
            VertexFormats.POSITION
    );

    private static final Uniform1f I_TIME = DREAM_SKY.findUniform1f("iTime");
    private static final Uniform1f NIGHT_STRENGTH = DREAM_SKY.findUniform1f("NightStrength");
    private static final Uniform1f RAIN_DARKEN = DREAM_SKY.findUniform1f("RainDarken");
    private static final UniformMat4 INV_PROJ_MAT = DREAM_SKY.findUniformMat4("InvProjMat");
    private static final UniformMat4 INV_VIEW_MAT = DREAM_SKY.findUniformMat4("InvViewMat");

    public static boolean shouldRender(ClientWorld world, float tickDelta) {
        return world != null
                && world.getRegistryKey().equals(World.OVERWORLD)
                && getNightStrength(world, tickDelta) > 0.001f;
    }

    public static void render(MatrixStack matrices, Matrix4f projectionMatrix, ClientWorld world, float tickDelta) {
        if (!shouldRender(world, tickDelta)) {
            return;
        }

        float time = (world.getTime() + tickDelta) / 20.0f;
        float night = getNightStrength(world, tickDelta);
        float rainDarken = 1.0f - (world.getRainGradient(tickDelta) * 0.25f + world.getThunderGradient(tickDelta) * 0.20f);
        rainDarken = MathHelper.clamp(rainDarken, 0.55f, 1.0f);

        Matrix4f invProj = new Matrix4f(projectionMatrix).invert(new Matrix4f());
        Matrix4f invView = new Matrix4f(matrices.peek().getPositionMatrix()).invert(new Matrix4f());

        I_TIME.set(time);
        NIGHT_STRENGTH.set(night);
        RAIN_DARKEN.set(rainDarken);
        INV_PROJ_MAT.set(invProj);
        INV_VIEW_MAT.set(invView);

        RenderSystem.setShader(DREAM_SKY::getProgram);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        buffer.vertex(-1.0f, -1.0f, 0.0f).next();
        buffer.vertex( 1.0f, -1.0f, 0.0f).next();
        buffer.vertex( 1.0f,  1.0f, 0.0f).next();
        buffer.vertex(-1.0f,  1.0f, 0.0f).next();
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    public static float getNightStrength(ClientWorld world, float tickDelta) {
        float timeOfDay = (world.getTimeOfDay() % 24000L) + tickDelta;
        if (timeOfDay < 0.0f) {
            timeOfDay += 24000.0f;
        }

        float fadeIn = smooth(12000.0f, 13500.0f, timeOfDay);
        float fadeOut = 1.0f - smooth(22300.0f, 23800.0f, timeOfDay);
        return MathHelper.clamp(fadeIn * fadeOut, 0.0f, 1.0f);
    }

    private static float smooth(float edge0, float edge1, float x) {
        float t = MathHelper.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}
