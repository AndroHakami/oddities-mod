// FILE: src/main/java/net/seep/odd/abilities/wizard/client/WizardSummonCircleClient.java
package net.seep.odd.abilities.wizard.client;

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
public final class WizardSummonCircleClient {
    private WizardSummonCircleClient() {}

    public enum Style {
        BIG_CYAN,
        COMBO_BLUE_GOLD
    }

    private static boolean active = false;
    private static Vec3d pos = null;

    private static float baseRadius = 2.2f;
    private static float grow01 = 1f;
    private static Style style = Style.BIG_CYAN;

    private static float anim = 0f; // 0..1
    private static final int SEGS = 96;

    public static void setActive(boolean on, Vec3d at, float radius, float grow, Style st) {
        active = on;
        pos = at;
        baseRadius = radius;
        grow01 = MathHelper.clamp(grow, 0f, 1f);
        style = st;
    }

    public static void init() {
        WorldRenderEvents.LAST.register(ctx -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return;

            float dt = ctx.tickDelta();
            float target = (active && pos != null) ? 1f : 0f;

            if (anim < target) anim = Math.min(target, anim + 0.24f * dt + 0.06f);
            else if (anim > target) anim = Math.max(target, anim - 0.24f * dt - 0.06f);

            if (anim <= 0.001f || pos == null) return;

            Vec3d cam = ctx.camera().getPos();
            MatrixStack matrices = ctx.matrixStack();

            matrices.push();
            matrices.translate(-cam.x, -cam.y, -cam.z);

            matrices.push();
            matrices.translate(pos.x, pos.y, pos.z);

            draw(matrices, mc, anim);

            matrices.pop();
            matrices.pop();
        });
    }

    private static void draw(MatrixStack matrices, MinecraftClient mc, float alphaMul) {
        float time = (mc.world.getTime() + mc.getTickDelta());
        float pulse = 0.85f + 0.15f * MathHelper.sin(time * 0.18f);

        float r = baseRadius * (0.75f + 0.25f * pulse) * (0.35f + 0.65f * grow01);
        float w = Math.max(0.06f, r * 0.016f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        if (style == Style.BIG_CYAN) {
            int cr = 30, cg = 255, cb = 240;
            drawRing(matrices, r, w, cr, cg, cb, (int)(180 * alphaMul));
            drawRing(matrices, r * 0.78f, w * 0.9f, cr, cg, cb, (int)(110 * alphaMul));
            drawTicks(matrices, r, cr, cg, cb, (int)(170 * alphaMul), time * 2.0f);
        } else {
            // blue ring + gold highlights
            int br = 70, bg = 150, bb = 255;
            int gr = 255, gg = 210, gb = 90;

            drawRing(matrices, r, w, br, bg, bb, (int)(175 * alphaMul));
            drawRing(matrices, r * 0.80f, w * 0.85f, br, bg, bb, (int)(95 * alphaMul));

            drawTicks(matrices, r, gr, gg, gb, (int)(190 * alphaMul), time * 2.4f);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void drawRing(MatrixStack matrices, float radius, float halfWidth,
                                 int r, int g, int b, int a) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        Matrix4f mat = matrices.peek().getPositionMatrix();

        float inR = Math.max(0.001f, radius - halfWidth);
        float outR = radius + halfWidth;

        buf.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < SEGS; i++) {
            double t0 = (Math.PI * 2.0 * i) / SEGS;
            double t1 = (Math.PI * 2.0 * (i + 1)) / SEGS;

            float c0 = (float)Math.cos(t0), s0 = (float)Math.sin(t0);
            float c1 = (float)Math.cos(t1), s1 = (float)Math.sin(t1);

            float in0x = c0 * inR,  in0z = s0 * inR;
            float out0x = c0 * outR, out0z = s0 * outR;

            float in1x = c1 * inR,  in1z = s1 * inR;
            float out1x = c1 * outR, out1z = s1 * outR;

            vtx(buf, mat, in0x, 0f, in0z, r, g, b, a);
            vtx(buf, mat, out0x,0f, out0z,r, g, b, a);
            vtx(buf, mat, out1x,0f, out1z,r, g, b, a);

            vtx(buf, mat, in0x, 0f, in0z, r, g, b, a);
            vtx(buf, mat, out1x,0f, out1z,r, g, b, a);
            vtx(buf, mat, in1x, 0f, in1z, r, g, b, a);
        }

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    private static void drawTicks(MatrixStack matrices, float radius,
                                  int r, int g, int b, int a,
                                  float rot) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        Matrix4f mat = matrices.peek().getPositionMatrix();

        buf.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        int ticks = 20;
        float tickLen = radius * 0.11f;
        float tickW = Math.max(0.016f, radius * 0.0065f);

        for (int i = 0; i < ticks; i++) {
            double ang = (Math.PI * 2.0 * i) / ticks + (rot * 0.02);

            float cx = (float)Math.cos(ang);
            float cz = (float)Math.sin(ang);

            float bx = cx * (radius - tickLen);
            float bz = cz * (radius - tickLen);

            float ex = cx * (radius + tickLen * 0.35f);
            float ez = cz * (radius + tickLen * 0.35f);

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
