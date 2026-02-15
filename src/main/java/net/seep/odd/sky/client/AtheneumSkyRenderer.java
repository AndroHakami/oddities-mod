// src/main/java/net/seep/odd/sky/client/AtheneumSkyRenderer.java
package net.seep.odd.sky.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.render.*;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

public final class AtheneumSkyRenderer {
    private AtheneumSkyRenderer() {}

    public static void render(WorldRenderContext ctx) {
        if (AtheneumClient.ATHENEUM_SKY == null) return;

        // Sky pass state
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();

        RenderSystem.setShader(() -> AtheneumClient.ATHENEUM_SKY);

        // Uniforms
        float t = (MinecraftClient.getInstance().world == null)
                ? 0f
                : (MinecraftClient.getInstance().world.getTime() + ctx.tickDelta());

        setFloat("iTime", t / 20.0f);

        // Reconstruct view rays in the fragment shader
        Matrix4f proj = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4f invProj = proj.invert(new Matrix4f());

        // matrixStack is the current view transform (translation ignored because w=0 for directions)
        Matrix4f view = new Matrix4f(ctx.matrixStack().peek().getPositionMatrix());
        Matrix4f invView = view.invert(new Matrix4f());

        setMat4("InvProjMat", invProj);
        setMat4("InvViewMat", invView);

        // Fullscreen quad in clip space (POSITION only)
        BufferBuilder bb = Tessellator.getInstance().getBuffer();
        bb.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        bb.vertex(-1f, -1f, 0f).next();
        bb.vertex( 1f, -1f, 0f).next();
        bb.vertex( 1f,  1f, 0f).next();
        bb.vertex(-1f,  1f, 0f).next();
        BufferRenderer.drawWithGlobalProgram(bb.end());

        // Restore
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void setFloat(String name, float v) {
        GlUniform u = AtheneumClient.ATHENEUM_SKY.getUniform(name);
        if (u != null) u.set(v);
    }

    private static void setMat4(String name, Matrix4f m) {
        GlUniform u = AtheneumClient.ATHENEUM_SKY.getUniform(name);
        if (u != null) u.set(m);
    }
}
