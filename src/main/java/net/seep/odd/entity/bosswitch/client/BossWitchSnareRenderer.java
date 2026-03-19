package net.seep.odd.entity.bosswitch.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.entity.bosswitch.BossWitchSnareEntity;
import org.joml.Matrix4f;

public final class BossWitchSnareRenderer extends EntityRenderer<BossWitchSnareEntity> {
    private static final Identifier DUMMY = new Identifier("minecraft", "textures/misc/white.png");

    private static final int SEGS = 72;

    private static final float OUTER_RADIUS = 3.0f;
    private static final int WARNING_TICKS = 12;
    private static final int ROOT_TICKS = 60;

    public BossWitchSnareRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(BossWitchSnareEntity entity,
                       float yaw,
                       float tickDelta,
                       MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers,
                       int light) {

        float age = entity.age + tickDelta;

        float warnProg = MathHelper.clamp(age / WARNING_TICKS, 0.0f, 1.0f);
        boolean active = age >= WARNING_TICKS && age <= (WARNING_TICKS + ROOT_TICKS);
        float activeProg = active ? MathHelper.clamp((age - WARNING_TICKS) / ROOT_TICKS, 0.0f, 1.0f) : 0.0f;

        float pulse = 0.88f + 0.12f * MathHelper.sin(age * 0.35f);
        float spin = age * 2.4f;

        int r = 170;
        int g = 70;
        int b = 255;

        matrices.push();
        matrices.translate(0.0, 0.04, 0.0);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        // outer warning ring
        float outerW = 0.10f + 0.05f * warnProg;
        int outerA = (int)(120.0f * warnProg * pulse);
        drawRing(matrices, OUTER_RADIUS, outerW, r, g, b, outerA);

        // inner ring that grows in during telegraph
        float innerRadius = OUTER_RADIUS * (0.18f + 0.72f * warnProg);
        int innerA = (int)(90.0f * warnProg);
        drawRing(matrices, innerRadius, 0.07f, r, g, b, innerA);

        // rune ring
        float runeRadius = OUTER_RADIUS - 0.35f;
        int runeA = (int)(110.0f * warnProg * (0.9f + 0.1f * pulse));
        drawRuneRing(matrices, runeRadius, spin, r, g, b, runeA);

        // active snare pulse
        if (active) {
            float collapse = 1.0f - activeProg;
            float activeRadius = OUTER_RADIUS * (0.65f + 0.35f * collapse);
            int activeA = (int)(150.0f * (0.8f + 0.2f * MathHelper.sin(age * 0.7f)));
            drawRing(matrices, activeRadius, 0.14f, 210, 120, 255, activeA);

            // fill made of several thin rings so it looks like a magic pool
            for (int i = 0; i < 7; i++) {
                float t = i / 6.0f;
                float rr = activeRadius * (0.15f + t * 0.82f);
                int aa = (int)(22.0f * (1.0f - t) * (0.9f + 0.1f * pulse));
                drawRing(matrices, rr, 0.08f, 150, 60, 230, aa);
            }

            // burst ring near activation
            if (activeProg < 0.18f) {
                float burstProg = activeProg / 0.18f;
                float burstRadius = OUTER_RADIUS * (0.8f + 0.5f * burstProg);
                int burstA = (int)(180.0f * (1.0f - burstProg));
                drawRing(matrices, burstRadius, 0.18f, 220, 150, 255, burstA);
            }
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        matrices.pop();

        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    private static void drawRing(MatrixStack matrices, float radius, float halfWidth,
                                 int r, int g, int b, int a) {
        if (radius <= 0.01f || a <= 0) return;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        Matrix4f mat = matrices.peek().getPositionMatrix();

        float inner = Math.max(0.001f, radius - halfWidth);
        float outer = radius + halfWidth;

        buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < SEGS; i++) {
            double a0 = (Math.PI * 2.0 * i) / SEGS;
            double a1 = (Math.PI * 2.0 * (i + 1)) / SEGS;

            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);

            float x0i = c0 * inner;
            float z0i = s0 * inner;
            float x0o = c0 * outer;
            float z0o = s0 * outer;

            float x1i = c1 * inner;
            float z1i = s1 * inner;
            float x1o = c1 * outer;
            float z1o = s1 * outer;

            vtx(buf, mat, x0i, 0.0f, z0i, r, g, b, a);
            vtx(buf, mat, x0o, 0.0f, z0o, r, g, b, a);
            vtx(buf, mat, x1o, 0.0f, z1o, r, g, b, a);
            vtx(buf, mat, x1i, 0.0f, z1i, r, g, b, a);
        }

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    private static void drawRuneRing(MatrixStack matrices, float radius, float spinDeg,
                                     int r, int g, int b, int a) {
        if (radius <= 0.01f || a <= 0) return;

        matrices.push();
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(spinDeg));

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        Matrix4f mat = matrices.peek().getPositionMatrix();

        buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        int runeCount = 18;
        float halfW = 0.06f;
        float halfH = 0.18f;

        for (int i = 0; i < runeCount; i++) {
            double ang = (Math.PI * 2.0 * i) / runeCount;
            float c = (float) Math.cos(ang);
            float s = (float) Math.sin(ang);

            float cx = c * radius;
            float cz = s * radius;

            // tangent direction
            float tx = -s;
            float tz = c;

            // radial direction
            float rx = c;
            float rz = s;

            float x0 = cx - tx * halfW - rx * halfH;
            float z0 = cz - tz * halfW - rz * halfH;

            float x1 = cx + tx * halfW - rx * halfH;
            float z1 = cz + tz * halfW - rz * halfH;

            float x2 = cx + tx * halfW + rx * halfH;
            float z2 = cz + tz * halfW + rz * halfH;

            float x3 = cx - tx * halfW + rx * halfH;
            float z3 = cz - tz * halfW + rz * halfH;

            vtx(buf, mat, x0, 0.0f, z0, r, g, b, a);
            vtx(buf, mat, x1, 0.0f, z1, r, g, b, a);
            vtx(buf, mat, x2, 0.0f, z2, r, g, b, a);
            vtx(buf, mat, x3, 0.0f, z3, r, g, b, a);
        }

        BufferRenderer.drawWithGlobalProgram(buf.end());
        matrices.pop();
    }

    private static void vtx(BufferBuilder buf, Matrix4f mat,
                            float x, float y, float z,
                            int r, int g, int b, int a) {
        buf.vertex(mat, x, y, z).color(r, g, b, a).next();
    }

    @Override
    public Identifier getTexture(BossWitchSnareEntity entity) {
        return DUMMY;
    }
}