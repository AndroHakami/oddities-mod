package net.seep.odd.entity.dragoness;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public final class UfoProtectorBeamRenderer {
    private static final RenderLayer BEAM_LAYER = RenderLayer.of(
            "odd_ufo_protector_beam",
            VertexFormats.POSITION_COLOR,
            VertexFormat.DrawMode.QUADS,
            256,
            false,
            true,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(new RenderPhase.ShaderProgram(GameRenderer::getPositionColorProgram))
                    .transparency(RenderLayer.ADDITIVE_TRANSPARENCY)
                    .cull(RenderLayer.DISABLE_CULLING)
                    .depthTest(RenderLayer.LEQUAL_DEPTH_TEST)
                    .writeMaskState(RenderLayer.COLOR_MASK)
                    .build(false)
    );

    private UfoProtectorBeamRenderer() {
    }

    public static void render(UfoProtectorEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers) {
        matrices.push();

        Vec3d start = new Vec3d(0.0D, -0.58D, 0.0D);
        Vec3d end = new Vec3d(0.0D, -24.5D, 0.0D);
        Vec3d dir = end.subtract(start).normalize();

        Vec3d camPos = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        Vec3d toCam = camPos.subtract(entity.getX(), entity.getY(), entity.getZ()).normalize();
        if (toCam.lengthSquared() < 1.0E-6D) {
            toCam = new Vec3d(1.0D, 0.0D, 0.0D);
        }

        Vec3d right = dir.crossProduct(toCam).normalize();
        if (right.lengthSquared() < 1.0E-6D) {
            right = new Vec3d(1.0D, 0.0D, 0.0D);
        }
        Vec3d up = right.crossProduct(dir).normalize();

        float pulse = 0.92f + 0.08f * MathHelper.sin((entity.age + tickDelta) * 0.45f);
        float rTail = 0.18f * pulse;
        float rHead = 0.10f * pulse;

        VertexConsumer vc = buffers.getBuffer(BEAM_LAYER);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        for (int i = 0; i < 3; i++) {
            double ang = i * (Math.PI / 3.0D);
            Vec3d axis = right.multiply(Math.cos(ang)).add(up.multiply(Math.sin(ang))).normalize();
            drawBeamQuad(vc, mat, start, end, axis, rTail, rHead, 70, 255, 120, 180, 110);
        }

        matrices.pop();
    }

    private static void drawBeamQuad(
            VertexConsumer vc,
            Matrix4f mat,
            Vec3d a,
            Vec3d b,
            Vec3d axisR,
            float rTail,
            float rHead,
            int cr,
            int cg,
            int cb,
            int tailA,
            int headA
    ) {
        Vec3d dir = b.subtract(a).normalize();
        Vec3d axisU = dir.crossProduct(axisR).normalize();

        Vec3d a0 = a.add(axisR.multiply(-rTail)).add(axisU.multiply(-rTail));
        Vec3d a1 = a.add(axisR.multiply(rTail)).add(axisU.multiply(-rTail));
        Vec3d b1 = b.add(axisR.multiply(rHead)).add(axisU.multiply(rHead));
        Vec3d b0 = b.add(axisR.multiply(-rHead)).add(axisU.multiply(rHead));

        vc.vertex(mat, (float) a0.x, (float) a0.y, (float) a0.z).color(cr, cg, cb, tailA).next();
        vc.vertex(mat, (float) a1.x, (float) a1.y, (float) a1.z).color(cr, cg, cb, tailA).next();
        vc.vertex(mat, (float) b1.x, (float) b1.y, (float) b1.z).color(cr, cg, cb, headA).next();
        vc.vertex(mat, (float) b0.x, (float) b0.y, (float) b0.z).color(cr, cg, cb, headA).next();
    }
}
