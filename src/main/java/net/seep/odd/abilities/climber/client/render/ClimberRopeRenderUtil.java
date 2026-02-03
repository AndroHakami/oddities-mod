package net.seep.odd.abilities.climber.client.render;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public final class ClimberRopeRenderUtil {
    private ClimberRopeRenderUtil() {}

    public static void renderLeadLike(
            VertexConsumer vc,
            MatrixStack matrices,
            Vec3d startWorld,
            Vec3d endWorld,
            Vec3d camPosWorld,
            int light
    ) {
        Vec3d delta = endWorld.subtract(startWorld);
        double len = delta.length();
        if (len < 0.001) return;

        Vec3d dir = delta.multiply(1.0 / len);

        // billboard-ish “side” vectors so thickness is visible
        Vec3d view = camPosWorld.subtract(startWorld);
        if (view.lengthSquared() < 1.0e-6) view = new Vec3d(0, 1, 0);
        else view = view.normalize();

        Vec3d side1 = dir.crossProduct(view);
        if (side1.lengthSquared() < 1.0e-6) side1 = dir.crossProduct(new Vec3d(0, 1, 0));
        if (side1.lengthSquared() < 1.0e-6) side1 = new Vec3d(1, 0, 0);
        side1 = side1.normalize();

        Vec3d side2 = dir.crossProduct(side1);
        if (side2.lengthSquared() < 1.0e-6) side2 = new Vec3d(0, 1, 0);
        else side2 = side2.normalize();

        // Thickness (tweak if you want it chunkier)
        double w = 0.055;
        side1 = side1.multiply(w);
        side2 = side2.multiply(w);

        int segments = 24;

        // small sag so it feels like a lead
        double sag = Math.min(0.55, len * 0.06);

        Matrix4f m = matrices.peek().getPositionMatrix();

        for (int i = 0; i < segments; i++) {
            float t0 = i / (float) segments;
            float t1 = (i + 1) / (float) segments;

            Vec3d p0 = startWorld.add(delta.multiply(t0));
            Vec3d p1 = startWorld.add(delta.multiply(t1));

            // parabola sag down
            double s0 = (4.0 * t0 * (1.0 - t0));
            double s1 = (4.0 * t1 * (1.0 - t1));
            p0 = p0.add(0, -sag * s0, 0);
            p1 = p1.add(0, -sag * s1, 0);

            // convert to local (start-relative) because the renderer translates to startWorld
            Vec3d l0 = p0.subtract(startWorld);
            Vec3d l1 = p1.subtract(startWorld);

            // vanilla-ish alternating shading
            int base0 = (i % 2 == 0) ? 160 : 110;
            int base1 = ((i + 1) % 2 == 0) ? 160 : 110;

            // two crossed ribbons => looks thick from any angle
            renderRibbon(vc, m, l0, l1, side1, base0, base1, light);
            renderRibbon(vc, m, l0, l1, side2, base0, base1, light);
        }
    }

    private static void renderRibbon(VertexConsumer vc, Matrix4f m,
                                     Vec3d l0, Vec3d l1, Vec3d side,
                                     int c0, int c1, int light) {
        // quad: (l0-side, l0+side, l1+side, l1-side)
        v(vc, m, l0.subtract(side), c0, light);
        v(vc, m, l0.add(side),      c0, light);
        v(vc, m, l1.add(side),      c1, light);

        v(vc, m, l0.subtract(side), c0, light);
        v(vc, m, l1.add(side),      c1, light);
        v(vc, m, l1.subtract(side), c1, light);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Vec3d p, int c, int light) {
        vc.vertex(m, (float)p.x, (float)p.y, (float)p.z)
                .color(c, c, c, 255)
                .light(light)
                .next();
    }
}
