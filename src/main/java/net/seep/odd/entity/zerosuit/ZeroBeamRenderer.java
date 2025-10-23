package net.seep.odd.entity.zerosuit;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.entity.zerosuit.ZeroBeamEntity;
import org.joml.Matrix4f;

public class ZeroBeamRenderer extends EntityRenderer<ZeroBeamEntity> {

    private static final RenderLayer LAYER = RenderLayer.of(
            "odd_zero_beam",
            VertexFormats.POSITION_COLOR,
            VertexFormat.DrawMode.QUADS,
            256, false, true,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(new RenderPhase.ShaderProgram(GameRenderer::getPositionColorProgram))
                    .transparency(RenderLayer.ADDITIVE_TRANSPARENCY)
                    .cull(RenderLayer.DISABLE_CULLING)
                    .depthTest(RenderLayer.LEQUAL_DEPTH_TEST)
                    .writeMaskState(RenderLayer.COLOR_MASK)
                    .build(false)
    );

    // timing
    private static final float TRAVEL_TICKS  = 2.5f; // reach impact quickly
    private static final float RETRACT_TICKS = 4.0f; // width-only retract window
    private static final float IMPACT_TICKS  = 5.0f; // flash duration after impact

    // spawn/retract radius shaping
    private static final float SPAWN_THIN   = 0.05f; // start at 25% radius
    private static final float RETRACT_THIN = 0.05f; // end at 5% radius

    // server radius range (to infer charge for impact scale)
    private static final float R_MIN = 0.25f;
    private static final float R_MAX = 1.40f;

    public ZeroBeamRenderer(EntityRendererFactory.Context ctx) { super(ctx); }

    @Override
    public void render(ZeroBeamEntity e, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider consumers, int light) {

        matrices.push();

        Vec3d start = Vec3d.ZERO;
        Vec3d full  = new Vec3d(e.getDX(), e.getDY(), e.getDZ());
        double fullLen = full.length();
        if (fullLen <= 1.0e-6) { matrices.pop(); return; }
        Vec3d dir = full.normalize();

        // billboard basis
        Vec3d camPos = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        Vec3d toCam  = camPos.subtract(e.getX(), e.getY(), e.getZ()).normalize();
        if (toCam.lengthSquared() < 1.0e-6) toCam = new Vec3d(0, 1, 0);
        Vec3d right = dir.crossProduct(toCam).normalize();
        if (right.lengthSquared() < 1.0e-6) right = dir.crossProduct(new Vec3d(0,1,0)).normalize();
        Vec3d up = right.crossProduct(dir).normalize();

        // time
        float age  = e.age + tickDelta;
        float life = e.getMaxLife();
        float tw   = 0.85f + 0.15f * MathHelper.sin((e.age + tickDelta) * 0.6f);

        // length travel (keep this!)
        float head01 = MathHelper.clamp(age / TRAVEL_TICKS, 0f, 1f);
        Vec3d b = dir.multiply(fullLen * head01);
        boolean reachedImpact = head01 >= 0.999f;

        // width-only retract near end of life (NO length shrink)
        float retract01 = 0f;
        if (age >= life - RETRACT_TICKS) {
            retract01 = MathHelper.clamp((age - (life - RETRACT_TICKS)) / RETRACT_TICKS, 0f, 1f);
        }

        // overall alpha fade in/out
        float alphaMul = 1.0f;
        if (age < 2.0f) alphaMul *= MathHelper.clamp(age / 2.0f, 0f, 1f);
        float tailLife = life - age;
        if (tailLife < 3.0f) alphaMul *= MathHelper.clamp(tailLife / 3.0f, 0f, 1f);

        // radius shaping:
        float baseR = e.getRadius();
        // inflate from thin → full while head travels
        float spawnInflate = mixSmooth(SPAWN_THIN, 1f, head01);        // 0..1
        // thin down during retract (width only)
        float retractThin  = MathHelper.lerp(retract01, 1f, RETRACT_THIN);
        float widthScale   = spawnInflate * retractThin;

        // head slightly fatter than tail to sell motion
        float rTailCore = baseR * 0.65f * widthScale * 0.90f;
        float rHeadCore = baseR * 0.65f * widthScale * 1.05f;
        float rTailHalo = baseR * 1.35f * widthScale * 0.90f;
        float rHeadHalo = baseR * 1.35f * widthScale * 1.05f;

        // tail alpha fade behind the head (keeps “travel” feel)
        float tailFadeGain = smooth01(Math.max(0f, head01 - 0.25f) / 0.75f);
        float tailFactor    = MathHelper.lerp(1f, 0.18f, tailFadeGain);

        int coreHeadA = (int)(255 * (0.78f * tw * alphaMul));
        int coreTailA = (int)(coreHeadA * tailFactor);
        int haloHeadA = (int)(255 * (0.35f * tw * alphaMul));
        int haloTailA = (int)(haloHeadA * tailFactor);

        VertexConsumer vc = consumers.getBuffer(LAYER);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        // three crossed planes — unchanged look, now width-retract
        for (int i = 0; i < 3; i++) {
            double ang = i * (Math.PI / 3.0);
            Vec3d r = right.multiply(Math.cos(ang)).add(up.multiply(Math.sin(ang))).normalize();

            // inner hot core
            drawBeamQuadGradientR(vc, mat, start, b, r,
                    rTailCore, rHeadCore,
                    255, 220, 120, coreTailA, coreHeadA);

            // outer orange halo
            drawBeamQuadGradientR(vc, mat, start, b, r,
                    rTailHalo, rHeadHalo,
                    255, 140, 30, haloTailA, haloHeadA);
        }

        // subtle caps so ends look clean (these also thin during retract)
        drawDisk(vc, mat, start, right, up, Math.max(rTailHalo * 0.9f, 0.2f), 14,
                255, 200, 120, (int)(255 * (0.35f * alphaMul)));
        drawDisk(vc, mat, b, right, up, Math.max(rHeadHalo * 0.9f, 0.2f), 14,
                255, 235, 180, (int)(255 * (0.55f * alphaMul)));

        // ===== IMPACT FLASH (visual “explosion”, scales with charge) =====
        if (reachedImpact) {
            float tSinceImpact = age - TRAVEL_TICKS;
            float impact01 = MathHelper.clamp(tSinceImpact / IMPACT_TICKS, 0f, 1f);
            float fade = 1.0f - impact01;
            if (fade > 0.001f) {
                // infer charge from beam radius (server encodes charge into radius)
                float charge01 = MathHelper.clamp((baseR - R_MIN) / (R_MAX - R_MIN), 0f, 1f);
                float sizeBase = MathHelper.lerp(charge01, 0.85f, 1.80f);
                float ringR = baseR * sizeBase * (1.00f + impact01 * 1.25f);
                Vec3d impactPos = dir.multiply(fullLen);

                // bright inner disk (hot white-orange)
                drawDisk(vc, mat, impactPos, right, up, ringR * 0.75f, 22,
                        255, 240, 180, (int)(255 * (0.55f * fade * alphaMul)));

                // thin expanding ring (deep orange)
                drawRing(vc, mat, impactPos, right, up, ringR, ringR * 1.16f, 28,
                        255, 160, 60, (int)(255 * (0.42f * fade * alphaMul)));
            }
        }

        matrices.pop();
    }

    /* ================= helpers ================= */

    // radius + alpha gradient along the quad (tail -> head)
    private static void drawBeamQuadGradientR(VertexConsumer vc, Matrix4f mat,
                                              Vec3d a, Vec3d b, Vec3d axisR,
                                              float rTail, float rHead,
                                              int cr, int cg, int cb, int tailA, int headA) {
        Vec3d dir = b.subtract(a).normalize();
        Vec3d axisU = dir.crossProduct(axisR).normalize();

        Vec3d a0 = a.add(axisR.multiply(-rTail)).add(axisU.multiply(-rTail));
        Vec3d a1 = a.add(axisR.multiply(+rTail)).add(axisU.multiply(-rTail));
        Vec3d b1 = b.add(axisR.multiply(+rHead)).add(axisU.multiply(+rHead));
        Vec3d b0 = b.add(axisR.multiply(-rHead)).add(axisU.multiply(+rHead));

        vc.vertex(mat, (float)a0.x, (float)a0.y, (float)a0.z).color(cr, cg, cb, tailA).next();
        vc.vertex(mat, (float)a1.x, (float)a1.y, (float)a1.z).color(cr, cg, cb, tailA).next();
        vc.vertex(mat, (float)b1.x, (float)b1.y, (float)b1.z).color(cr, cg, cb, headA).next();
        vc.vertex(mat, (float)b0.x, (float)b0.y, (float)b0.z).color(cr, cg, cb, headA).next();
    }

    private static void drawDisk(VertexConsumer vc, Matrix4f mat, Vec3d center, Vec3d right, Vec3d up,
                                 float radius, int sides, int r, int g, int b, int a) {
        if (radius <= 0f) return;
        Vec3d prev = null;
        for (int i = 0; i <= sides; i++) {
            double ang = (i / (double)sides) * Math.PI * 2.0;
            Vec3d p = center.add(right.multiply(Math.cos(ang) * radius)).add(up.multiply(Math.sin(ang) * radius));
            if (prev != null) {
                vc.vertex(mat, (float)center.x, (float)center.y, (float)center.z).color(r, g, b, a).next();
                vc.vertex(mat, (float)prev.x,   (float)prev.y,   (float)prev.z  ).color(r, g, b, a).next();
                vc.vertex(mat, (float)p.x,      (float)p.y,      (float)p.z     ).color(r, g, b, a).next();
            }
            prev = p;
        }
    }

    private static void drawRing(VertexConsumer vc, Matrix4f mat, Vec3d center, Vec3d right, Vec3d up,
                                 float rInner, float rOuter, int sides, int r, int g, int b, int a) {
        if (rOuter <= rInner) return;
        Vec3d[] inner = new Vec3d[sides];
        Vec3d[] outer = new Vec3d[sides];
        for (int i = 0; i < sides; i++) {
            double ang = (i / (double)sides) * Math.PI * 2.0;
            Vec3d ri = right.multiply(Math.cos(ang));
            Vec3d ui = up.multiply(Math.sin(ang));
            inner[i] = center.add(ri.multiply(rInner)).add(ui.multiply(rInner));
            outer[i] = center.add(ri.multiply(rOuter)).add(ui.multiply(rOuter));
        }
        for (int i = 0; i < sides; i++) {
            int j = (i + 1) % sides;
            putQuad(vc, mat, inner[i], inner[j], outer[j], outer[i], r, g, b, a);
        }
    }

    private static void putQuad(VertexConsumer vc, Matrix4f mat,
                                Vec3d a, Vec3d b, Vec3d c, Vec3d d, int r, int g, int bl, int aCol) {
        vc.vertex(mat, (float)a.x, (float)a.y, (float)a.z).color(r, g, bl, aCol).next();
        vc.vertex(mat, (float)b.x, (float)b.y, (float)b.z).color(r, g, bl, aCol).next();
        vc.vertex(mat, (float)c.x, (float)c.y, (float)c.z).color(r, g, bl, aCol).next();
        vc.vertex(mat, (float)d.x, (float)d.y, (float)d.z).color(r, g, bl, aCol).next();
    }

    private static float smooth01(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float mixSmooth(float lo, float hi, float t01) {
        return lo + (hi - lo) * smooth01(t01);
    }

    @Override
    public Identifier getTexture(ZeroBeamEntity entity) { return null; }
}
