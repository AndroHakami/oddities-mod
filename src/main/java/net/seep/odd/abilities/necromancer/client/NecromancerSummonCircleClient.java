// src/main/java/net/seep/odd/abilities/necromancer/client/NecromancerSummonCircleClient.java
package net.seep.odd.abilities.necromancer.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class NecromancerSummonCircleClient {
    private NecromancerSummonCircleClient() {}

    private static boolean active = false;
    private static Vec3d pos = null;
    private static boolean skeleton = false;

    private static float anim = 0f; // 0..1

    private static final int SEGS = 96;
    private static final float RADIUS = 2.10f;

    public static void setActive(boolean on, Vec3d at, boolean isSkeleton) {
        active = on;
        pos = at;
        skeleton = isSkeleton;
    }

    public static void init() {
        WorldRenderEvents.LAST.register(ctx -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return;

            float dt = ctx.tickDelta();
            float target = (active && pos != null) ? 1f : 0f;

            if (anim < target) anim = Math.min(target, anim + 0.20f * dt + 0.06f);
            else if (anim > target) anim = Math.max(target, anim - 0.20f * dt - 0.06f);

            if (anim <= 0.001f || pos == null) return;

            Vec3d cam = ctx.camera().getPos();
            MatrixStack matrices = ctx.matrixStack();

            matrices.push();
            matrices.translate(-cam.x, -cam.y, -cam.z);

            matrices.push();
            matrices.translate(pos.x, pos.y, pos.z);

            drawRing(matrices, mc, anim);

            matrices.pop();
            matrices.pop();
        });
    }

    private static void drawRing(MatrixStack matrices, MinecraftClient mc, float alphaMul) {
        float time = (mc.world.getTime() + mc.getTickDelta());

        int r = 255;
        int g = 0;
        int b = 255;

        float pulse = 0.85f + 0.15f * MathHelper.sin(time * 0.18f);
        float rot = time * 2.2f;

        float aOuter = alphaMul * (0.55f + 0.45f * pulse);
        float aInner = alphaMul * 0.75f;

        float outerR = RADIUS * (0.92f + 0.08f * pulse);
        float innerR = outerR * 0.78f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        // outer ring
        drawGroundRing(matrices, outerR, Math.max(0.06f, outerR * 0.016f), r, g, b, (int)(180 * aOuter));
        // inner ring (fainter)
        drawGroundRing(matrices, innerR, Math.max(0.05f, innerR * 0.014f), r, g, b, (int)(120 * aInner));

        // add “runes” as short ticks around the ring
        drawTicks(matrices, outerR, r, g, b, (int)(170 * alphaMul), rot, skeleton);

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void drawGroundRing(MatrixStack matrices, float radius, float halfWidth,
                                       int r, int g, int b, int a) {
        if (radius <= 0.02f || a <= 0) return;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        Matrix4f mat = matrices.peek().getPositionMatrix();

        float inR = Math.max(0.001f, radius - halfWidth);
        float outR = radius + halfWidth;

        buf.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < SEGS; i++) {
            double t0 = (Math.PI * 2.0 * i) / SEGS;
            double t1 = (Math.PI * 2.0 * (i + 1)) / SEGS;

            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0);
            float c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);

            float in0x = c0 * inR,  in0z = s0 * inR;
            float out0x = c0 * outR, out0z = s0 * outR;

            float in1x = c1 * inR,  in1z = s1 * inR;
            float out1x = c1 * outR, out1z = s1 * outR;

            vtx(buf, mat, in0x, 0f, in0z, r, g, b, a);
            vtx(buf, mat, out0x, 0f, out0z, r, g, b, a);
            vtx(buf, mat, out1x, 0f, out1z, r, g, b, a);

            vtx(buf, mat, in0x, 0f, in0z, r, g, b, a);
            vtx(buf, mat, out1x, 0f, out1z, r, g, b, a);
            vtx(buf, mat, in1x, 0f, in1z, r, g, b, a);
        }

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    private static void drawTicks(MatrixStack matrices, float radius,
                                  int r, int g, int b, int a,
                                  float rot, boolean skeleton) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        Matrix4f mat = matrices.peek().getPositionMatrix();

        buf.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        int ticks = skeleton ? 22 : 18;
        float tickLen = radius * 0.09f;
        float tickW = Math.max(0.015f, radius * 0.006f);

        for (int i = 0; i < ticks; i++) {
            double ang = (Math.PI * 2.0 * i) / ticks + (rot * 0.02);

            float cx = (float)Math.cos(ang);
            float cz = (float)Math.sin(ang);

            float bx = cx * (radius - tickLen);
            float bz = cz * (radius - tickLen);

            float ex = cx * (radius + tickLen * 0.35f);
            float ez = cz * (radius + tickLen * 0.35f);

            // perpendicular for thickness
            float px = -cz * tickW;
            float pz =  cx * tickW;

            vtx(buf, mat, bx - px, 0f, bz - pz, r, g, b, a);
            vtx(buf, mat, bx + px, 0f, bz + pz, r, g, b, a);
            vtx(buf, mat, ex + px, 0f, ez + pz, r, g, b, a);

            vtx(buf, mat, bx - px, 0f, bz - pz, r, g, b, a);
            vtx(buf, mat, ex + px, 0f, ez + pz, r, g, b, a);
            vtx(buf, mat, ex - px, 0f, ez - pz, r, g, b, a);
        }

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    private static void vtx(BufferBuilder b, Matrix4f mat,
                            float x, float y, float z,
                            int r, int g, int bb, int a) {
        b.vertex(mat, x, y, z).color(r, g, bb, MathHelper.clamp(a, 0, 255)).next();
    }
}
