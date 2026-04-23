package net.seep.odd.sky.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.seep.odd.event.alien.client.AlienInvasionClientState;
import net.seep.odd.event.alien.client.sky.AlienOverworldSkyCore;
import net.seep.odd.sky.CelestialEventClient;
import net.seep.odd.sky.day.BiomeDayProfileClientStore;
import org.joml.Matrix4f;

public final class OverworldDreamSkyRenderer {
    private OverworldDreamSkyRenderer() {}

    private static final Identifier SUN = new Identifier("odd", "textures/environment/sun.png");
    private static final Identifier MOON_PHASES = new Identifier("odd", "textures/environment/moon_phases.png");
    private static final Identifier ALIEN_SUN = new Identifier("odd", "textures/environment/alien_sun.png");
    private static final Identifier ALIEN_MOON = new Identifier("odd", "textures/environment/alien_moon.png");

    public static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (world == null || OverworldDreamSkyClient.OVERWORLD_DREAM_SKY == null) return;

        float tickDelta = ctx.tickDelta();

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (shouldRenderAlienSky(world)) {
            renderAlienBaseSky(ctx, world, tickDelta);
        } else {
            renderBaseSky(ctx, world, tickDelta);

            if (OverworldDreamSkyClient.OVERWORLD_BIOME_DAY_SKY != null) {
                float dayAmount = BiomeDayProfileClientStore.getDayAmount(world, tickDelta);
                if (dayAmount > 0.001f) {
                    BiomeDayProfileClientStore.DayBiomeBlend blend =
                            BiomeDayProfileClientStore.sample(world, ctx.camera().getPos());

                    float alpha = blend.weight() * dayAmount;
                    if (alpha > 0.001f) {
                        renderBiomeDayOverlay(ctx, world, tickDelta, blend, alpha);
                    }
                }
            }
        }

        renderCelestials(ctx, world, tickDelta);

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static boolean shouldRenderAlienSky(ClientWorld world) {
        return world.getRegistryKey() == net.minecraft.world.World.OVERWORLD
                && AlienInvasionClientState.active()
                && AlienOverworldSkyCore.ALIEN_SKY != null;
    }

    private static void renderBaseSky(WorldRenderContext ctx, ClientWorld world, float tickDelta) {
        ShaderProgram program = OverworldDreamSkyClient.OVERWORLD_DREAM_SKY;
        RenderSystem.setShader(() -> program);

        float nightAmount = getNightAmount(world, tickDelta);

        Matrix4f proj = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4f invProj = proj.invert(new Matrix4f());

        Matrix4f skyPassView = new Matrix4f(ctx.matrixStack().peek().getPositionMatrix());
        Matrix4f invView = skyPassView.invert(new Matrix4f());

        Matrix4f skyRot = new Matrix4f()
                .rotateY((float) Math.toRadians(-90.0))
                .rotateX(world.getSkyAngle(tickDelta) * ((float) Math.PI * 2.0f));
        Matrix4f skyInvRot = skyRot.invert(new Matrix4f());

        setFloat(program, "iTime", (world.getTime() + tickDelta) / 20.0f);
        setFloat(program, "NightAmount", nightAmount);
        setFloat(program, "Rain", world.getRainGradient(tickDelta));
        setFloat(program, "Thunder", world.getThunderGradient(tickDelta));
        setMat4(program, "InvProjMat", invProj);
        setMat4(program, "InvViewMat", invView);
        setMat4(program, "SkyInvRotMat", skyInvRot);

        drawFullscreenQuad();
    }

    private static void renderAlienBaseSky(WorldRenderContext ctx, ClientWorld world, float tickDelta) {
        ShaderProgram program = AlienOverworldSkyCore.ALIEN_SKY;
        RenderSystem.setShader(() -> program);

        float time = (world.getTime() + tickDelta) / 20.0f;
        float progress = MathHelper.clamp(AlienInvasionClientState.skyProgress01(tickDelta), 0.0f, 1.0f);
        float cubes = MathHelper.clamp(AlienInvasionClientState.cubes01(tickDelta), 0.0f, 1.0f);
        float nightAmount = getNightAmount(world, tickDelta);

        Matrix4f proj = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4f invProj = proj.invert(new Matrix4f());

        Matrix4f skyPassView = new Matrix4f(ctx.matrixStack().peek().getPositionMatrix());
        Matrix4f invView = skyPassView.invert(new Matrix4f());

        Matrix4f skyRot = new Matrix4f()
                .rotateY((float) Math.toRadians(-90.0))
                .rotateX(world.getSkyAngle(tickDelta) * ((float) Math.PI * 2.0f));
        Matrix4f skyInvRot = skyRot.invert(new Matrix4f());

        setFloat(program, "iTime", time);
        setFloat(program, "Intensity", 1.0f);
        setFloat(program, "Progress", progress);
        setFloat(program, "CubeIntensity", cubes);
        setFloat(program, "NightAmount", nightAmount);
        setFloat(program, "Rain", world.getRainGradient(tickDelta));
        setFloat(program, "Thunder", world.getThunderGradient(tickDelta));
        setMat4(program, "InvProjMat", invProj);
        setMat4(program, "InvViewMat", invView);
        setMat4(program, "SkyInvRotMat", skyInvRot);

        drawFullscreenQuad();
    }

    private static void renderBiomeDayOverlay(WorldRenderContext ctx,
                                              ClientWorld world,
                                              float tickDelta,
                                              BiomeDayProfileClientStore.DayBiomeBlend blend,
                                              float alpha) {
        ShaderProgram program = OverworldDreamSkyClient.OVERWORLD_BIOME_DAY_SKY;
        RenderSystem.setShader(() -> program);

        Matrix4f proj = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4f invProj = proj.invert(new Matrix4f());

        Matrix4f skyPassView = new Matrix4f(ctx.matrixStack().peek().getPositionMatrix());
        Matrix4f invView = skyPassView.invert(new Matrix4f());

        setFloat(program, "iTime", (world.getTime() + tickDelta) / 20.0f);
        setFloat(program, "BlendAlpha", alpha);
        setFloat(program, "DayAmount", BiomeDayProfileClientStore.getDayAmount(world, tickDelta));
        setFloat(program, "Rain", world.getRainGradient(tickDelta));
        setFloat(program, "Thunder", world.getThunderGradient(tickDelta));

        setFloat(program, "SkyR", (float) blend.sky().x);
        setFloat(program, "SkyG", (float) blend.sky().y);
        setFloat(program, "SkyB", (float) blend.sky().z);

        setFloat(program, "FogR", (float) blend.fog().x);
        setFloat(program, "FogG", (float) blend.fog().y);
        setFloat(program, "FogB", (float) blend.fog().z);

        setFloat(program, "HorizonR", (float) blend.horizon().x);
        setFloat(program, "HorizonG", (float) blend.horizon().y);
        setFloat(program, "HorizonB", (float) blend.horizon().z);

        setMat4(program, "InvProjMat", invProj);
        setMat4(program, "InvViewMat", invView);

        drawFullscreenQuad();
    }

    private static void drawFullscreenQuad() {
        BufferBuilder bb = Tessellator.getInstance().getBuffer();
        bb.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        bb.vertex(-1.0f, -1.0f, 0.0f).next();
        bb.vertex( 1.0f, -1.0f, 0.0f).next();
        bb.vertex( 1.0f,  1.0f, 0.0f).next();
        bb.vertex(-1.0f,  1.0f, 0.0f).next();
        BufferRenderer.drawWithGlobalProgram(bb.end());
    }

    private static void renderCelestials(WorldRenderContext ctx, ClientWorld world, float tickDelta) {
        float visibility = 1.0f - world.getRainGradient(tickDelta);
        if (visibility <= 0.0f) return;

        boolean alienActive = shouldRenderAlienSky(world);

        ctx.matrixStack().push();
        ctx.matrixStack().multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0f));
        ctx.matrixStack().multiply(RotationAxis.POSITIVE_X.rotationDegrees(world.getSkyAngle(tickDelta) * 360.0f));

        Matrix4f mat = ctx.matrixStack().peek().getPositionMatrix();
        BufferBuilder bb = Tessellator.getInstance().getBuffer();

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA,
                GlStateManager.DstFactor.ONE,
                GlStateManager.SrcFactor.ONE,
                GlStateManager.DstFactor.ZERO
        );
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, visibility);

        Identifier sunTex = alienActive ? ALIEN_SUN : CelestialEventClient.getSunTextureOr(SUN);
        Identifier moonTex = alienActive ? ALIEN_MOON : CelestialEventClient.getMoonTextureOr(MOON_PHASES);

        RenderSystem.setShaderTexture(0, sunTex);
        float sunSize = 30.0f * CelestialEventClient.sunScale();
        bb.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        bb.vertex(mat, -sunSize, 100.0f, -sunSize).texture(0.0f, 0.0f).next();
        bb.vertex(mat,  sunSize, 100.0f, -sunSize).texture(1.0f, 0.0f).next();
        bb.vertex(mat,  sunSize, 100.0f,  sunSize).texture(1.0f, 1.0f).next();
        bb.vertex(mat, -sunSize, 100.0f,  sunSize).texture(0.0f, 1.0f).next();
        BufferRenderer.drawWithGlobalProgram(bb.end());

        RenderSystem.setShaderTexture(0, moonTex);
        int phase = world.getMoonPhase();
        int col = phase % 4;
        int row = (phase / 4) % 2;

        float u0 = col / 4.0f;
        float v0 = row / 2.0f;
        float u1 = (col + 1) / 4.0f;
        float v1 = (row + 1) / 2.0f;

        float moonSize = 44.0f * CelestialEventClient.moonScale();
        bb.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        bb.vertex(mat, -moonSize, -100.0f,  moonSize).texture(u1, v1).next();
        bb.vertex(mat,  moonSize, -100.0f,  moonSize).texture(u0, v1).next();
        bb.vertex(mat,  moonSize, -100.0f, -moonSize).texture(u0, v0).next();
        bb.vertex(mat, -moonSize, -100.0f, -moonSize).texture(u1, v0).next();
        BufferRenderer.drawWithGlobalProgram(bb.end());

        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        ctx.matrixStack().pop();
    }

    public static float getNightAmount(ClientWorld world, float tickDelta) {
        float sunHeight = MathHelper.cos(world.getSkyAngle(tickDelta) * ((float) Math.PI * 2.0f)) * 2.0f + 0.5f;
        sunHeight = MathHelper.clamp(sunHeight, 0.0f, 1.0f);
        float night = 1.0f - sunHeight;
        return MathHelper.clamp((night - 0.04f) / 0.96f, 0.0f, 1.0f);
    }

    private static void setFloat(ShaderProgram program, String name, float v) {
        GlUniform u = program.getUniform(name);
        if (u != null) u.set(v);
    }

    private static void setMat4(ShaderProgram program, String name, Matrix4f m) {
        GlUniform u = program.getUniform(name);
        if (u != null) u.set(m);
    }
}
